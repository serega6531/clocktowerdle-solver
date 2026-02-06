package ru.serega6531.clocktowerdle

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class SolverConfig(
    val maxGuesses: Int = 4,
    val maxInFlight: Int = 16,
    val topChoiceLimit: Int = 5
)

class Solver(private val config: SolverConfig) {

    suspend fun getBestStarting(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Pair<Character, Double>> {
        val inputs = buildSolverInputs(emptyList())
        val expectedByGuess = getExpectedCostsParallel(inputs, onProgress)
        return expectedByGuess.toList().sortedBy { it.second }
    }

    suspend fun getNextStep(existingGuesses: Path): SolverReport {
        val inputs = buildSolverInputs(existingGuesses)
        val expectedByGuess = getExpectedCostsParallel(inputs)
        val bestChoices = bestChoicesFrom(expectedByGuess, config.topChoiceLimit)
        return SolverReport(inputs.possibleTargets, bestChoices)
    }

    private fun getPossibleTargets(existingGuesses: Path): Set<Character> {
        val guessed = existingGuesses.map { it.character }.toSet()
        return (Character.entries - guessed).filterTo(mutableSetOf()) { candidate ->
            existingGuesses.all { matches(candidate, it) }
        }
    }

    private fun buildSolverInputs(existingGuesses: Path): SolverInputs {
        val guessed = existingGuesses.map { it.character }.toSet()
        val possibleTargets = getPossibleTargets(existingGuesses)
        val guessPool = Character.entries.toSet() - guessed
        return SolverInputs(possibleTargets, guessed, guessPool)
    }

    private fun bestChoicesFrom(expectedByGuess: Map<Character, Double>, limit: Int): List<SolverChoice> {
        if (expectedByGuess.isEmpty()) {
            return emptyList()
        }

        return expectedByGuess
            .filterValues { it.isFinite() }
            .entries
            .sortedWith(compareBy<Map.Entry<Character, Double>> { it.value }.thenBy { it.key.characterName })
            .take(limit)
            .map { SolverChoice(it.key, it.value) }
    }

    private suspend fun getExpectedCostsParallel(
        inputs: SolverInputs,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<Character, Double> = coroutineScope {
        if (inputs.possibleTargets.isEmpty()) {
            return@coroutineScope emptyMap()
        }

        if (inputs.guessed.size >= config.maxGuesses) {
            return@coroutineScope emptyMap()
        }

        val availableGuesses = inputs.availableGuesses
        if (availableGuesses.isEmpty()) {
            return@coroutineScope emptyMap()
        }

        val total = availableGuesses.size
        val workerCount = config.maxInFlight.coerceAtMost(total)

        val work = Channel<Character>(capacity = workerCount)
        val results = Channel<Pair<Character, Double>>(capacity = workerCount)
        val progress = Channel<Unit>(capacity = workerCount)

        repeat(workerCount) {
            launch(Dispatchers.Default) {
                for (guess in work) {
                    val expectedCost = expectedCostForGuess(
                        guess,
                        inputs.possibleTargets,
                        inputs.guessed,
                        inputs.guessPool,
                        mutableMapOf()
                    )
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
            for (guess in availableGuesses) {
                work.send(guess)
            }
            work.close()
        }

        val expectedByGuess = List(total) { results.receive() }.toMap()
        progressJob.join()
        results.close()
        progress.close()

        expectedByGuess
    }

    internal fun expectedCostForGuess(
        guess: Character,
        possibleTargets: Set<Character>,
        guessed: Set<Character>,
        guessPool: Set<Character>,
        memo: MutableMap<ExpectedKey, Double> = mutableMapOf()
    ): Double {
        if (guessed.size >= config.maxGuesses) {
            return Double.POSITIVE_INFINITY
        }

        val newGuessed = guessed + guess
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
                val expectedCost = expectedCost(remaining, newGuessed, guessPool, memo)

                if (expectedCost.isInfinite()) {
                    return Double.POSITIVE_INFINITY //short-circuit without checking the remaining targets
                }

                weight * (1.0 + expectedCost)
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
        if (guessed.size >= config.maxGuesses) {
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
        return guessCache.computeIfAbsent(target to guessCharacter) {
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
        return matchesCache.computeIfAbsent(character to guess) {
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
}

private typealias Path = List<Guess>

private data class SolverInputs(
    val possibleTargets: Set<Character>,
    val guessed: Set<Character>,
    val guessPool: Set<Character>
) {
    val availableGuesses: Set<Character>
        get() = guessPool - guessed
}

data class SolverChoice(
    val character: Character,
    val expectedCost: Double
)

data class SolverReport(
    val possibleTargets: Set<Character>,
    val bestChoices: List<SolverChoice>
)

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

internal data class ExpectedKey(
    val possibleTargets: Set<Character>,
    val guessed: Set<Character>
)
