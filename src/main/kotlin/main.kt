suspend fun main() {
    while (true) {
        println("Choose an option:")
        println("1 - Print best starting characters")
        println("2 - Interactive solver")
        println("0 - Exit")
        print("Enter your choice: ")

        val userInput = readln()

        when (userInput) {
            "1" -> {
                println("Calculating best starting characters...")
                val bestStarting = getBestStarting()
                bestStarting.forEach { println("${it.first.characterName} -> ${it.second}") }
            }

            "2" -> {
                runInteractiveSolver()
            }

            "0" -> return

            else -> {
                println("Invalid option")
            }
        }
    }
}

private fun runInteractiveSolver() {
    val guesses = mutableListOf<Guess>()
    var character = getCharacterGuessInput()

    while (true) {
        val feedback = getFeedbackInput(character)

        if (feedback.correct) {
            println("Well done!")
            return
        }

        guesses.add(feedback)
        character = getBestChoice(guesses) ?: run { println("Incorrect state"); return }
        println("Next guess: ${character.characterName}")
    }
}

private fun getCharacterGuessInput(): Character {
    while (true) {
        print("Enter your guess: ")
        val input = readln()
        val character = Character.entries.find { it.name == input || it.characterName.equals(input, ignoreCase = true) }

        if (character == null) {
            println("Character not found, try again")
            continue
        }

        return character
    }
}

private fun getFeedbackInput(character: Character): Guess {
    while (true) {
        println("Enter the feedback (e.g. CORRECT, PARTIALLY_CORRECT, INCORRECT, CORRECT, INCORRECT, 1) or CORRECT:")
        val feedback = readln().split(" ")

        if (feedback.first() == "CORRECT") {
            return Guess.correct(character)
        }

        if (feedback.size != 6) {
            println("Invalid feedback, try again")
            continue
        }

        try {
            val originalScriptAccuracy = feedback[0].toAccuracy()
            val characterTypeAccuracy = feedback[1].toAccuracy()
            val wakesInNightAccuracy = feedback[2].toAccuracy()
            val selectsPlayerAccuracy = feedback[3].toAccuracy()
            val learnsInfoAccuracy = feedback[4].toAccuracy()
            val abilityMatches = feedback[5].toInt()

            return Guess(
                character = character,
                correct = false,
                originalScriptAccuracy = originalScriptAccuracy,
                characterTypeAccuracy = characterTypeAccuracy,
                wakesInNightAccuracy = wakesInNightAccuracy,
                selectsPlayerAccuracy = selectsPlayerAccuracy,
                learnsInfoAccuracy = learnsInfoAccuracy,
                abilityMatches = abilityMatches
            )
        } catch (_: NumberFormatException) {
            println("Invalid feedback, try again")
        }
    }

}

private fun String.toAccuracy(): Accuracy = when (this.uppercase()) {
    "CORRECT", "+" -> Accuracy.CORRECT
    "PARTIALLY_CORRECT", "~" -> Accuracy.PARTIALLY_CORRECT
    "INCORRECT", "-" -> Accuracy.INCORRECT
    else -> throw IllegalArgumentException("Invalid accuracy: $this")
}
