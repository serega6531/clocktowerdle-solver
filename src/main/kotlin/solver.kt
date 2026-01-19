fun getBestStarting(): List<Pair<Character, Double>> {
    val minPaths: MutableMap<DistanceKey, List<Path>> = mutableMapOf()

    Character.entries.forEach { assumedTarget ->
        calculateShortestPaths(assumedTarget, Character.entries.toSet(), emptyList(), minPaths)
    }

    val avgDistancesByStarting = Character.entries.associateWith { starting ->
        minPaths.filter { it.key.starting == starting }.values.map { it.first().size }.average()
    }
    return avgDistancesByStarting.entries.sortedBy { it.value }.map { (k, v) -> k to v }
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
    result: MutableMap<DistanceKey, List<Path>>
) {
    val charactersToTry = when (possibleCharacters.size) {
        1 -> {
            check(possibleCharacters.single() == target) { "Expected target to be the only possible character" }
            updateShortestPath(target, existingGuesses, result)
            return
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
        (possibleCharacters - guessCharacter).filterTo(mutableSetOf()) { candidate ->
            matches(candidate, guess)
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

internal fun makeGuess(target: Character, guessCharacter: Character): Guess {
    val correct = guessCharacter == target
    val originalScriptAccuracy = isAccurateNoPartial(target, guessCharacter) { it.originalScript }
    val characterTypeAccuracy = getCharacterTypeAccuracy(target, guessCharacter)
    val wakesInNightAccuracy = isAccurateNoPartial(target, guessCharacter) { it.wakesInNight }
    val selectsPlayerAccuracy = isAccurateNoPartial(target, guessCharacter) { it.selectsPlayer }
    val learnsInfoAccuracy = isAccurateNoPartial(target, guessCharacter) { it.learnsInfo }
    val abilityMatches = getAbilityMatches(target, guessCharacter)

    return Guess(
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
    return character.ability.intersect(guessCharacter.ability).size
}

internal fun matches(character: Character, guess: Guess): Boolean {
    val scriptMatches = attributeMatchesNoPartial(character, guess, guess.originalScriptAccuracy) { it.originalScript }
    val characterTypeMatches = characterTypeMatches(character, guess)
    val wakesInNightMatches =
        attributeMatchesNoPartial(character, guess, guess.wakesInNightAccuracy) { it.wakesInNight }
    val selectsPlayerMatches =
        attributeMatchesNoPartial(character, guess, guess.selectsPlayerAccuracy) { it.selectsPlayer }
    val learnsInfoMatches = attributeMatchesNoPartial(character, guess, guess.learnsInfoAccuracy) { it.learnsInfo }
    val abilityMatches = abilityMatches(character, guess)

    return scriptMatches && characterTypeMatches && wakesInNightMatches && selectsPlayerMatches && learnsInfoMatches && abilityMatches
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