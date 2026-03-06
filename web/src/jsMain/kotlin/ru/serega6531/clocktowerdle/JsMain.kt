package ru.serega6531.clocktowerdle

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.math.roundToInt
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLUListElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

private data class Ui(
    val guessSelect: HTMLSelectElement,
    val abilityMatchesInput: HTMLInputElement,
    val maxGuessesInput: HTMLInputElement,
    val topChoicesInput: HTMLInputElement,
    val includeInefficientInput: HTMLInputElement,
    val addGuessButton: HTMLButtonElement,
    val markCorrectButton: HTMLButtonElement,
    val resetButton: HTMLButtonElement,
    val status: HTMLElement,
    val guessesList: HTMLUListElement,
    val possibleTargets: HTMLElement,
    val bestChoices: HTMLElement,
    val sessionOutput: HTMLElement
)

private val uiScope = MainScope()

private const val GROUP_ORIGINAL_SCRIPT = "original-script"
private const val GROUP_CHARACTER_TYPE = "character-type"
private const val GROUP_WAKES_IN_NIGHT = "wakes-in-night"
private const val GROUP_SELECTS_PLAYER = "selects-player"
private const val GROUP_LEARNS_INFO = "learns-info"

fun main() {
    val ui = Ui(
        guessSelect = requireElement("guess-select"),
        abilityMatchesInput = requireElement("ability-matches"),
        maxGuessesInput = requireElement("max-guesses"),
        topChoicesInput = requireElement("top-choices"),
        includeInefficientInput = requireElement("include-inefficient"),
        addGuessButton = requireElement("add-guess"),
        markCorrectButton = requireElement("mark-correct"),
        resetButton = requireElement("reset"),
        status = requireElement("status"),
        guessesList = requireElement("guesses"),
        possibleTargets = requireElement("possible-targets"),
        bestChoices = requireElement("best-choices"),
        sessionOutput = requireElement("session-output")
    )

    val guesses = mutableListOf<Guess>()

    populateGuessSelect(ui)
    updateAbilityRange(ui)
    renderGuesses(ui, guesses)

    ui.guessSelect.onChange {
        updateAbilityRange(ui)
    }

    ui.addGuessButton.onClick {
        ui.status.textContent = ""
        val guess = buildGuess(ui) ?: return@onClick
        guesses.add(guess)
        scrollToSessionOutput(ui)
        computeAndRender(ui, guesses)
    }

    ui.markCorrectButton.onClick {
        ui.status.textContent = ""
        val character = selectedCharacter(ui) ?: return@onClick
        addCorrectGuess(ui, guesses, character)
    }

    ui.resetButton.onClick {
        guesses.clear()
        ui.status.textContent = ""
        ui.possibleTargets.textContent = ""
        ui.bestChoices.textContent = ""
        resetSelections(ui)
        renderGuesses(ui, guesses)
    }

}

private fun populateGuessSelect(ui: Ui) {
    val sorted = Character.entries.sortedBy { it.characterName }
    for (character in sorted) {
        val option = document.createElement("option") as HTMLOptionElement
        option.value = character.name
        option.text = character.characterName
        ui.guessSelect.add(option)
    }
}

private fun updateAbilityRange(ui: Ui) {
    val character = selectedCharacter(ui) ?: return
    ui.abilityMatchesInput.max = character.ability.size.toString()
    val current = ui.abilityMatchesInput.value.toIntOrNull() ?: 0
    val clamped = current.coerceIn(0, character.ability.size)
    ui.abilityMatchesInput.value = clamped.toString()
}

private fun buildGuess(ui: Ui): Guess? {
    val character = selectedCharacter(ui) ?: return null
    val abilityMatches = ui.abilityMatchesInput.value.toIntOrNull() ?: 0

    return Guess(
        character = character,
        correct = false,
        originalScriptAccuracy = accuracyFromGroup(GROUP_ORIGINAL_SCRIPT),
        characterTypeAccuracy = accuracyFromGroup(GROUP_CHARACTER_TYPE),
        wakesInNightAccuracy = accuracyFromGroup(GROUP_WAKES_IN_NIGHT),
        selectsPlayerAccuracy = accuracyFromGroup(GROUP_SELECTS_PLAYER),
        learnsInfoAccuracy = accuracyFromGroup(GROUP_LEARNS_INFO),
        abilityMatches = abilityMatches
    )
}

private fun resetSelections(ui: Ui) {
    ui.guessSelect.selectedIndex = 0
    ui.abilityMatchesInput.value = "0"
    setAccuracyGroupToIncorrect(GROUP_ORIGINAL_SCRIPT)
    setAccuracyGroupToIncorrect(GROUP_CHARACTER_TYPE)
    setAccuracyGroupToIncorrect(GROUP_WAKES_IN_NIGHT)
    setAccuracyGroupToIncorrect(GROUP_SELECTS_PLAYER)
    setAccuracyGroupToIncorrect(GROUP_LEARNS_INFO)
    updateAbilityRange(ui)
}

private fun setAccuracyGroupToIncorrect(groupName: String) {
    val input = document.querySelector("input[name='$groupName'][value='INCORRECT']") as? HTMLInputElement
        ?: return
    input.checked = true
}

private fun computeAndRender(ui: Ui, guesses: MutableList<Guess>) {
    ui.status.textContent = "Calculating next step..."
    val solver = buildSolver(ui)
    uiScope.launch {
        val report = solver.getNextStep(guesses) { done, total ->
            println("Progress: $done/$total")
            val percent = if (total == 0) 0 else ((done.toDouble() / total) * 100.0).roundToInt()
            ui.status.textContent = "Calculating next step... $done/$total ($percent%)"
        }
        renderReport(ui, report, guesses)
        ui.status.textContent = ""
    }
}

private fun renderSolved(ui: Ui, character: Character, guesses: List<Guess>) {
    renderGuesses(ui, guesses)
    ui.possibleTargets.textContent = "Solved: ${character.characterName}"
    ui.bestChoices.textContent = ""
}

private fun addCorrectGuess(ui: Ui, guesses: MutableList<Guess>, character: Character) {
    guesses.add(Guess.correct(character))
    renderSolved(ui, character, guesses)
}

private fun renderReport(ui: Ui, report: SolverReport, guesses: MutableList<Guess>) {
    renderGuesses(ui, guesses)
    renderPossibleTargets(ui, report.possibleTargets)
    renderBestChoices(ui, report, guesses)
}

private fun renderPossibleTargets(ui: Ui, possibleTargets: Set<Character>) {
    val targets = possibleTargets
        .sortedBy { it.characterName }
        .joinToString(", ") { it.characterName }

    ui.possibleTargets.textContent = if (possibleTargets.isEmpty()) {
        "No possible targets remain."
    } else {
        targets
    }
}

private fun renderBestChoices(ui: Ui, report: SolverReport, guesses: MutableList<Guess>) {
    ui.bestChoices.innerHTML = ""
    if (report.bestChoices.isEmpty()) {
        ui.bestChoices.textContent = "No valid guesses remain."
        return
    }

    report.bestChoices.forEachIndexed { index, choice ->
        val row = buildChoiceRow(ui, report, guesses, index, choice)
        ui.bestChoices.appendChild(row)
    }
}

private fun buildChoiceRow(
    ui: Ui,
    report: SolverReport,
    guesses: MutableList<Guess>,
    index: Int,
    choice: SolverChoice
): HTMLElement {
    val row = document.createElement("div") as HTMLElement
    row.className = "choice-row"

    row.appendChild(buildChoiceLabel(index, choice))
    row.appendChild(buildChoiceAction(ui, report, guesses, choice))

    return row
}

private fun buildChoiceLabel(index: Int, choice: SolverChoice): HTMLElement {
    val label = document.createElement("span") as HTMLElement
    label.className = "choice-label"
    label.textContent = "${index + 1}) ${choice.character.characterName} (${formatScore(choice.expectedCost)})"
    return label
}

private fun buildChoiceAction(
    ui: Ui,
    report: SolverReport,
    guesses: MutableList<Guess>,
    choice: SolverChoice
): HTMLButtonElement {
    val action = document.createElement("button") as HTMLButtonElement
    action.className = "ghost choice-action"
    action.type = "button"
    action.textContent = "Use"
    action.setAttribute("aria-label", "Use ${choice.character.characterName} as guess")
    action.onClick {
        resetSelections(ui)
        ui.guessSelect.value = choice.character.name
        updateAbilityRange(ui)

        if (report.possibleTargets.size == 1) {
            addCorrectGuess(ui, guesses, choice.character)
            return@onClick
        }

        window.scrollTo(0.0, 0.0)
    }
    return action
}

private fun renderGuesses(ui: Ui, guesses: List<Guess>) {
    ui.guessesList.innerHTML = ""
    if (guesses.isEmpty()) {
        val item = document.createElement("li")
        item.textContent = "No guesses yet."
        ui.guessesList.appendChild(item)
        return
    }

    guesses.forEachIndexed { index, guess ->
        val item = document.createElement("li")
        item.textContent = "${index + 1}. ${guess.character.characterName} | " +
            "${guess.originalScriptAccuracy.shortLabel()} " +
            "${guess.characterTypeAccuracy.shortLabel()} " +
            "${guess.wakesInNightAccuracy.shortLabel()} " +
            "${guess.selectsPlayerAccuracy.shortLabel()} " +
            "${guess.learnsInfoAccuracy.shortLabel()} " +
            "${guess.abilityMatches}"
        ui.guessesList.appendChild(item)
    }
}

private fun accuracyFromGroup(groupName: String): Accuracy {
    val input = document.querySelector("input[name='$groupName']:checked") as? HTMLInputElement
        ?: error("Missing selected accuracy option for '$groupName'")
    return when (input.value) {
        "CORRECT" -> Accuracy.CORRECT
        "PARTIALLY_CORRECT" -> Accuracy.PARTIALLY_CORRECT
        else -> Accuracy.INCORRECT
    }
}

private fun Accuracy.shortLabel(): String {
    return when (this) {
        Accuracy.CORRECT -> "\uD83D\uDFE9" // green square
        Accuracy.PARTIALLY_CORRECT -> "\uD83D\uDFE8" // yellow square
        Accuracy.INCORRECT -> "⬛" // black square
    }
}

private fun selectedCharacter(ui: Ui): Character? {
    val name = ui.guessSelect.value
    return Character.entries.firstOrNull { it.name == name }
}

private fun formatScore(value: Double): String {
    if (!value.isFinite()) {
        return "INF"
    }
    val rounded = (value * 1000.0).roundToInt() / 1000.0
    return rounded.toString()
}

private fun buildSolver(ui: Ui): Solver {
    val maxGuesses = ui.maxGuessesInput.value.toIntOrNull()?.coerceAtLeast(1) ?: 4
    val topChoices = ui.topChoicesInput.value.toIntOrNull()?.coerceAtLeast(1) ?: 5
    val includeInefficient = ui.includeInefficientInput.checked

    return Solver(
        SolverConfig(
            maxGuesses = maxGuesses,
            topChoiceLimit = topChoices,
            includeInefficientBranches = includeInefficient
        )
    )
}

private fun scrollToSessionOutput(ui: Ui) {
    ui.sessionOutput.scrollIntoView()
}

private fun EventTarget.onClick(callback: (Event) -> Unit) {
    this.addEventListener("click", callback)
}

private fun EventTarget.onChange(callback: (Event) -> Unit) {
    this.addEventListener("change", callback)
}

private inline fun <reified T> requireElement(id: String): T {
    val element = document.getElementById(id)
        ?: error("Missing element with id '$id'")
    return element as T
}
