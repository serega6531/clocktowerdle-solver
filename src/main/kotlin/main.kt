import java.util.Locale
import me.tongfei.progressbar.ProgressBar
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking

abstract class SolverCommand : CliktCommand() {
    protected val maxGuesses by option("--max-guesses", "-g", help = "Maximum number of guesses allowed")
        .int()
        .default(4)
        .check("max-guesses must be positive") { it > 0 }

    protected val maxInFlight by option("--max-in-flight", "-j", help = "Maximum number of parallel jobs")
        .int()
        .default(16)
        .check("max-in-flight must be positive") { it > 0 }

    protected val topChoiceLimit by option("--top-choice-limit", "-t", help = "Number of top choices to display")
        .int()
        .default(5)
        .check("top-choice-limit must be positive") { it > 0 }

    protected val config: SolverConfig
        get() = SolverConfig(maxGuesses, maxInFlight, topChoiceLimit)

    protected val solver
        get() = Solver(config)
}

class ClockwerdleSolver : SolverCommand() {
    override fun run() = Unit
}

class BestStarting : SolverCommand() {
    override fun run() = runBlocking {
        printBestStartingCharacters(solver)
    }
}

class Interactive : SolverCommand() {
    override fun run() = runBlocking {
        runInteractiveSolver(solver)
    }
}

fun main(args: Array<String>) = ClockwerdleSolver()
    .subcommands(BestStarting(), Interactive())
    .main(args)

private suspend fun printBestStartingCharacters(solver: Solver) {
    println("Calculating best starting characters...")
    val total = Character.entries.size.toLong()
    val bestStarting = ProgressBar("Best starting", total).use { progressBar ->
        solver.getBestStarting { done, _ ->
            progressBar.stepTo(done.toLong())
        }
    }
    println("Best starting characters:")
    if (bestStarting.isEmpty()) {
        println("None")
        return
    }

    val showIndex = bestStarting.size > 1
    bestStarting.forEachIndexed { index, (character, score) ->
        val formattedScore = String.format(Locale.US, "%.3f", score)
        val label = "${character.characterName} ($formattedScore)"
        val entry = if (showIndex) "${index + 1}) $label" else label
        println(entry)
    }
}

private suspend fun runInteractiveSolver(solver: Solver) {
    val guesses = mutableListOf<Guess>()
    var character = getCharacterGuessInput()

    while (true) {
        val feedback = getFeedbackInput(character)

        if (feedback.correct) {
            println("Well done!")
            return
        }

        guesses.add(feedback)
        val (possibleTargets, bestChoices) = solver.getNextStep(guesses)

        if (possibleTargets.isEmpty()) {
            println("No possible targets remain.")
            return
        }

        if (possibleTargets.size == 1) {
            val solved = possibleTargets.single()
            println("Found target: ${solved.characterName}")
            return
        }

        printCharacterReport(possibleTargets)

        if (bestChoices.isEmpty()) {
            println("No valid guesses remain.")
            return
        }

        printChoiceReport(bestChoices)
        character = selectBestChoice(bestChoices.map { it.character }) ?: run {
            println("Incorrect state")
            return
        }
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
        } catch (_: IllegalArgumentException) {
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

private fun printCharacterReport(characters: Collection<Character>) {
    val sorted = characters.sortedBy { it.characterName }
    println("Possible targets (${sorted.size}):")
    if (sorted.isEmpty()) {
        println("None")
        return
    }

    println(sorted.joinToString(", ") { it.characterName })
}

private fun printChoiceReport(choices: List<SolverChoice>) {
    println("Best next guesses (${choices.size}):")
    if (choices.isEmpty()) {
        println("None")
        return
    }

    val showIndex = choices.size > 1
    val items = choices.mapIndexed { index, choice ->
        val cost = String.format(Locale.US, "%.3f", choice.expectedCost)
        val label = "${choice.character.characterName} ($cost)"
        if (showIndex) "${index + 1}) $label" else label
    }

    println(items.joinToString(", "))
}

private fun selectBestChoice(choices: List<Character>): Character? {
    if (choices.isEmpty()) {
        return null
    }

    if (choices.size == 1) {
        return choices.first()
    }

    while (true) {
        print("Choose next guess by number or name: ")
        val input = readln().trim()

        val index = input.toIntOrNull()
        if (index != null) {
            val choiceIndex = index - 1
            if (choiceIndex in choices.indices) {
                return choices[choiceIndex]
            }
        }

        val match = choices.firstOrNull { choice ->
            choice.name.equals(input, ignoreCase = true) ||
                    choice.characterName.equals(input, ignoreCase = true)
        }
        if (match != null) {
            return match
        }

        println("Invalid choice, try again")
    }
}
