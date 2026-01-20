import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

suspend fun getBestStarting(): List<Pair<Character, Double>> = coroutineScope {
    val minPathsMap = Character.entries.map { assumedTarget ->
        async(Dispatchers.Default) {
            val (minPaths, duration) = measureTimedValue {
                calculateShortestPaths(assumedTarget, Character.entries.toSet(), emptyList())
            }
            println("Calculated shortest paths to ${assumedTarget.name} in $duration: ${minPaths.values.sumOf { it.size }} paths")
            minPaths
        }
    }.awaitAll()

    val minPaths = minPathsMap.flatMap { it.entries }.associate { it.key to it.value }

    val avgDistancesByStarting = Character.entries.associateWith { starting ->
        minPaths.filter { it.key.starting == starting }.values.map { it.first().size }.average()
    }
    avgDistancesByStarting.entries.sortedBy { it.value }.map { (k, v) -> k to v }
}

fun getBestChoice(existingGuesses: Path): Character {
    val possibleCharacters =
        (Character.entries - existingGuesses.map { it.character }.toSet()).filterTo(mutableSetOf()) { candidate ->
            existingGuesses.all { matches(candidate, it) }
        }

    val minPaths: MutableMap<DistanceKey, List<Path>> = mutableMapOf()
    possibleCharacters.forEach { target ->
        calculateShortestPaths(
            target,
            possibleCharacters,
            existingGuesses,
            minPaths
        )
    }

    val nextChoices = minPaths.values.flatten().map { it[existingGuesses.size].character }
    val frequencies = nextChoices.groupingBy { it }.eachCount()
    return frequencies.entries.maxBy { it.value }.key
}

internal fun calculateShortestPaths(
    target: Character,
    possibleCharacters: Set<Character>,
    existingGuesses: Path,
    result: MutableMap<DistanceKey, List<Path>> = mutableMapOf()
): Map<DistanceKey, List<Path>> {
    val charactersToTry = when (possibleCharacters.size) {
        1 -> {
            check(possibleCharacters.single() == target) { "Expected target to be the only possible character" }
            updateShortestPath(target, existingGuesses, result)
            return result
        }

        2 -> {
            // if only two are left, it's not useful to try characters that are not in the list
            possibleCharacters
        }

        else -> {
            // there might be a character that is already excluded but cuts the possibilities better
            (Character.entries - existingGuesses.map { it.character }.toSet())
        }
    }

    val withoutDirect = charactersToTry - target

    val withRemaining = withoutDirect.associateWith { guessCharacter ->
        val guess = makeGuess(target, guessCharacter)
        possibleCharacters.filterTo(mutableSetOf()) { candidate ->
            candidate != guessCharacter && matches(candidate, guess)
        }
    }

    withRemaining
        .asSequence()
        .filter { (_, remaining) -> remaining.isNotEmpty() }
        .sortedBy { (_, remaining) -> remaining.size }
        .filter { (guessCharacter, _) ->
            val first = existingGuesses.firstOrNull()?.character ?: guessCharacter
            val distanceKey = DistanceKey(first, target)
            val previousPaths = result[distanceKey]

            // If we've already found a path of length (existingGuesses.size + 1), there's no point
            // exploring this branch further as it won't produce a shorter path.
            previousPaths == null || previousPaths.first().size != existingGuesses.size + 1
        }
        .forEach { (guessCharacter, remaining) ->
            val guess = makeGuess(target, guessCharacter)
            val newGuesses = existingGuesses + guess

            calculateShortestPaths(target, remaining, newGuesses, result)
        }

    return result
}

private fun updateShortestPath(
    target: Character,
    existingGuesses: Path,
    result: MutableMap<DistanceKey, List<Path>>
) {
    val distanceKey = DistanceKey(existingGuesses.first().character, target)
    val previousPaths = result[distanceKey]

    val guess = Guess.correct(target)
    val newGuesses = existingGuesses + guess

    if (previousPaths == null || newGuesses.size < previousPaths.first().size) {
        result[distanceKey] = listOf(newGuesses)
    } else if (newGuesses.size == previousPaths.first().size) {
        result[distanceKey] = previousPaths + listOf(newGuesses)
    }
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

internal data class DistanceKey(val starting: Character, val target: Character) {
    override fun toString(): String {
        return "${starting.characterName} -> ${target.characterName}"
    }
}

private typealias Path = List<Guess>