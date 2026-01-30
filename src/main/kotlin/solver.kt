import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val MAX_GUESSES = 4
private const val MAX_IN_FLIGHT = 16

suspend fun getBestStarting(
    maxInFlight: Int = MAX_IN_FLIGHT,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
): List<Pair<Character, Double>> = coroutineScope {
    require(maxInFlight > 0) { "maxInFlight must be positive" }
    val possibleTargets = Character.entries.toSet()
    val guessPool = possibleTargets
    val total = Character.entries.size
    val workerCount = maxInFlight.coerceAtMost(total)

    val work = Channel<Character>(capacity = workerCount)
    val results = Channel<Pair<Character, Double>>(capacity = workerCount)
    val progress = Channel<Unit>(capacity = workerCount)

    repeat(workerCount) {
        launch(Dispatchers.Default) {
            for (guess in work) {
                val expectedCost = expectedCostForGuess(guess, possibleTargets, emptySet(), guessPool)
                results.send(guess to expectedCost)
                progress.send(Unit)
            }
        }
    }

    val progressJob = launch {
        var done = 0
        repeat(total) {
            progress.receive()
            done += 1
            onProgress(done, total)
        }
    }

    launch {
        for (guess in Character.entries) {
            work.send(guess)
        }
        work.close()
    }

    val expectedByGuess = List(total) { results.receive() }
    progressJob.join()
    results.close()
    progress.close()

    expectedByGuess.sortedBy { it.second }
}

fun getBestChoice(existingGuesses: Path): Character? {
    val bestChoices = getBestChoices(existingGuesses)
    return bestChoices.randomOrNull()
}

fun getBestChoices(existingGuesses: Path): List<Character> {
    val guessed = existingGuesses.map { it.character }.toSet()
    val possibleTargets =
        (Character.entries - guessed).filterTo(mutableSetOf()) { candidate ->
            existingGuesses.all { matches(candidate, it) }
        }

    val guessPool = Character.entries.toSet() - guessed
    return getBestChoicesForTargets(possibleTargets, guessed, guessPool)
}

internal fun getBestChoicesForTargets(
    possibleTargets: Set<Character>,
    guessed: Set<Character>,
    guessPool: Set<Character> = Character.entries.toSet()
): List<Character> {
    if (possibleTargets.isEmpty()) {
        return emptyList()
    }

    if (guessed.size >= MAX_GUESSES) {
        return emptyList()
    }

    val availableGuesses = guessPool - guessed
    if (availableGuesses.isEmpty()) {
        return emptyList()
    }

    val memo = mutableMapOf<ExpectedKey, Double>()
    val expectedByGuess = availableGuesses.associateWith { guess ->
        expectedCostForGuess(guess, possibleTargets, guessed, guessPool, memo)
    }

    val minCost = expectedByGuess.values.minOrNull() ?: return emptyList()
    val epsilon = 1e-9
    val bestChoices = expectedByGuess.filterValues { it <= minCost + epsilon }.keys
    return bestChoices.toList()
}

internal fun expectedCostForGuess(
    guess: Character,
    possibleTargets: Set<Character>,
    guessed: Set<Character>,
    guessPool: Set<Character>,
    memo: MutableMap<ExpectedKey, Double> = mutableMapOf()
): Double {
    if (guessed.size >= MAX_GUESSES) {
        return Double.POSITIVE_INFINITY
    }

    val grouped = possibleTargets.groupBy { target -> makeGuess(target, guess) }

    if (guess !in possibleTargets) {
        val maxRemaining = grouped.maxOf { it.value.size }
        if (maxRemaining >= possibleTargets.size - 1) {
            return Double.POSITIVE_INFINITY
        }
    }

    val total = grouped.entries.sumOf { (guessResult, targetsForFeedback) ->
        val weight = targetsForFeedback.size.toDouble()
        if (guessResult.correct) {
            weight
        } else {
            val remaining = targetsForFeedback.toSet()
            weight * (1.0 + expectedCost(remaining, guessed + guess, guessPool, memo))
        }
    }

    return total / possibleTargets.size
}

internal fun expectedCost(
    possibleTargets: Set<Character>,
    guessed: Set<Character>,
    guessPool: Set<Character>,
    memo: MutableMap<ExpectedKey, Double>
): Double {
    if (guessed.size >= MAX_GUESSES) {
        return Double.POSITIVE_INFINITY
    }

    if (possibleTargets.size <= 1) {
        return 1.0
    }

    val key = ExpectedKey(possibleTargets, guessed)
    memo[key]?.let { return it }

    val availableGuesses = guessPool - guessed
    check(availableGuesses.isNotEmpty()) {
        "No available guesses while ${possibleTargets.size} targets remain"
    }

    val best = availableGuesses.minOf { guess ->
        expectedCostForGuess(guess, possibleTargets, guessed, guessPool, memo)
    }

    memo[key] = best
    return best
}

private val guessCache = ConcurrentHashMap<Pair<Character, Character>, Guess>()

internal fun makeGuess(target: Character, guessCharacter: Character): Guess {
    return guessCache.getOrPut(target to guessCharacter) {
        val correct = guessCharacter == target
        val originalScriptAccuracy = isAccurateNoPartial(target, guessCharacter) { it.originalScript }
        val characterTypeAccuracy = getCharacterTypeAccuracy(target, guessCharacter)
        val wakesInNightAccuracy = isAccurateNoPartial(target, guessCharacter) { it.wakesInNight }
        val selectsPlayerAccuracy = isAccurateNoPartial(target, guessCharacter) { it.selectsPlayer }
        val learnsInfoAccuracy = isAccurateNoPartial(target, guessCharacter) { it.learnsInfo }
        val abilityMatches = getAbilityMatches(target, guessCharacter)

        Guess(
            character = guessCharacter,
            correct = correct,
            originalScriptAccuracy = originalScriptAccuracy,
            characterTypeAccuracy = characterTypeAccuracy,
            wakesInNightAccuracy = wakesInNightAccuracy,
            selectsPlayerAccuracy = selectsPlayerAccuracy,
            learnsInfoAccuracy = learnsInfoAccuracy,
            abilityMatches = abilityMatches
        )
    }
}

internal fun getCharacterTypeAccuracy(character: Character, guessCharacter: Character): Accuracy {
    val left = character.characterType
    val right = guessCharacter.characterType

    return when {
        left == right -> Accuracy.CORRECT
        left.otherInTeam() == right -> Accuracy.PARTIALLY_CORRECT
        else -> Accuracy.INCORRECT
    }
}

private inline fun isAccurateNoPartial(
    character: Character,
    guessCharacter: Character,
    extractor: (Character) -> Any
): Accuracy {
    val left = extractor(character)
    val right = extractor(guessCharacter)

    return if (left == right) Accuracy.CORRECT else Accuracy.INCORRECT
}

private fun getAbilityMatches(character: Character, guessCharacter: Character): Int {
    return character.ability.count { it in guessCharacter.ability }
}

private val matchesCache = ConcurrentHashMap<Pair<Character, Guess>, Boolean>()

internal fun matches(character: Character, guess: Guess): Boolean {
    return matchesCache.getOrPut(character to guess) {
        attributeMatchesNoPartial(character, guess, guess.originalScriptAccuracy) { it.originalScript } &&
                characterTypeMatches(character, guess) &&
                attributeMatchesNoPartial(character, guess, guess.wakesInNightAccuracy) { it.wakesInNight } &&
                attributeMatchesNoPartial(character, guess, guess.selectsPlayerAccuracy) { it.selectsPlayer } &&
                attributeMatchesNoPartial(character, guess, guess.learnsInfoAccuracy) { it.learnsInfo } &&
                abilityMatches(character, guess)
    }
}

private inline fun attributeMatchesNoPartial(
    character: Character,
    guess: Guess,
    accuracy: Accuracy,
    extractor: (Character) -> Any
): Boolean {
    val characterAttri = extractor(character)
    val guessAttri = extractor(guess.character)

    val same = characterAttri == guessAttri
    val correct = accuracy == Accuracy.CORRECT

    return (same && correct) || (!same && !correct)
}

private fun characterTypeMatches(character: Character, guess: Guess): Boolean {
    val guessedType = guess.character.characterType

    val possibleTypes = when (guess.characterTypeAccuracy) {
        Accuracy.CORRECT -> listOf(guessedType)
        Accuracy.PARTIALLY_CORRECT -> listOf(guessedType.otherInTeam())
        Accuracy.INCORRECT -> guessedType.otherTeam()
    }

    return character.characterType in possibleTypes
}

internal fun abilityMatches(character: Character, guess: Guess): Boolean {
    val expectedMatches = character.ability.count { it in guess.character.ability }
    return expectedMatches == guess.abilityMatches
}

data class Guess(
    val character: Character,
    val correct: Boolean,
    val originalScriptAccuracy: Accuracy,
    val characterTypeAccuracy: Accuracy,
    val wakesInNightAccuracy: Accuracy,
    val selectsPlayerAccuracy: Accuracy,
    val learnsInfoAccuracy: Accuracy,
    val abilityMatches: Int,
) {
    companion object {
        fun correct(character: Character) =
            Guess(
                character,
                true,
                Accuracy.CORRECT,
                Accuracy.CORRECT,
                Accuracy.CORRECT,
                Accuracy.CORRECT,
                Accuracy.CORRECT,
                character.ability.size
            )
    }
}

enum class Accuracy {
    CORRECT,
    PARTIALLY_CORRECT,
    INCORRECT
}

private typealias Path = List<Guess>

internal data class ExpectedKey(
    val possibleTargets: Set<Character>,
    val guessed: Set<Character>
)
