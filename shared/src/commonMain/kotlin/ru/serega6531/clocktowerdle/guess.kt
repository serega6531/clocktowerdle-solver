package ru.serega6531.clocktowerdle

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