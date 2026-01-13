import kotlin.collections.emptyList

fun getBestStarting(allCharacters: List<Character>): List<Pair<Character, Double>> {
    val minPaths: MutableMap<DistanceKey, List<Path>> = mutableMapOf()

    allCharacters.forEach { assumedTarget ->
        calculateShortestPaths(assumedTarget, allCharacters, allCharacters.toSet(), emptyList(), minPaths)
    }

    val avgDistancesByStarting = allCharacters.associateWith { starting ->
        minPaths.filter { it.key.starting == starting }.values.map { it.first().size }.average()
    }
    return avgDistancesByStarting.entries.sortedBy { it.value }.map { (k, v) -> k to v }
}

fun getBestChoice(allCharacters: List<Character>, existingGuesses: Path): Character {
    val possibleCharacters =
        (allCharacters - existingGuesses.map { it.character }.toSet()).filterTo(mutableSetOf()) { candidate ->
            existingGuesses.all { matches(candidate, it) }
        }

    val minPaths: MutableMap<DistanceKey, List<Path>> = mutableMapOf()
    possibleCharacters.forEach { target ->
        calculateShortestPaths(
            target,
            allCharacters,
            possibleCharacters,
            existingGuesses,
            minPaths
        )
    }

    val nextChoices = minPaths.values.flatten().map { it[existingGuesses.size].character }
    val frequencies = nextChoices.groupingBy { it }.eachCount()
    return frequencies.entries.maxBy { it.value }.key
}

private fun calculateShortestPaths(
    target: Character,
    allCharacters: List<Character>,
    possibleCharacters: Set<Character>,
    existingGuesses: Path,
    result: MutableMap<DistanceKey, List<Path>>
) {
    val charactersToTry = if (possibleCharacters.size > 2) {
        // there might be a character that is already excluded but cuts the possibilities better
        (allCharacters - existingGuesses.map { it.character }.toSet())
    } else {
        possibleCharacters
    }

    val withoutDirect = if (charactersToTry.size > 1) {
        charactersToTry - target
    } else {
        charactersToTry
    }

    withoutDirect.forEach { guessCharacter ->
        tryMatchCharacter(
            target,
            guessCharacter,
            existingGuesses,
            possibleCharacters,
            allCharacters,
            result
        )
    }
}

private fun tryMatchCharacter(
    assumedTarget: Character,
    guessCharacter: Character,
    existingGuesses: Path,
    possibleCharacters: Set<Character>,
    allCharacters: List<Character>,
    minDistances: MutableMap<DistanceKey, List<Path>>
) {
    val guess = makeGuess(assumedTarget, guessCharacter)
    val newGuesses = existingGuesses + guess

    val distanceKey = DistanceKey(newGuesses.first().character, assumedTarget)
    val previousPaths = minDistances[distanceKey]

    if (guess.correct) {
        if (previousPaths == null || newGuesses.size < previousPaths.first().size) {
            minDistances[distanceKey] = listOf(newGuesses)
        } else if (newGuesses.size == previousPaths.first().size) {
            minDistances[distanceKey] = previousPaths + listOf(newGuesses)
        }

        return
    }

    if (previousPaths?.let { it.first().size == newGuesses.size } == true) {
        return // the path can only be longer than the one found so far
    }

    val remaining = (possibleCharacters - guessCharacter).filterTo(mutableSetOf()) { candidate ->
        newGuesses.all {
            matches(candidate, it)
        }
    }

    if (remaining.size + 1 >= possibleCharacters.size) { // guess gave no additional information
        return
    }

    calculateShortestPaths(assumedTarget, allCharacters, remaining, newGuesses, minDistances)
}

private fun makeGuess(target: Character, guessCharacter: Character): Guess {
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

private fun getCharacterTypeAccuracy(character: Character, guessCharacter: Character): Accuracy {
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

private fun matches(character: Character, guess: Guess): Boolean {
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

private fun abilityMatches(character: Character, guess: Guess): Boolean {
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

private data class DistanceKey(val starting: Character, val target: Character)

private typealias Path = List<Guess>