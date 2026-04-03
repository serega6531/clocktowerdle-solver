package ru.serega6531.clocktowerdle

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.math.round

private val uiScope = MainScope()

fun main() {
    if (document.getElementById("solver-extension-message") != null) return

    val target = document.querySelector(".guesses-remaining") as? HTMLElement ?: return
    val guesses = parseExistingGuesses()
    val message = createMessageElement()
    target.insertAdjacentElement("afterend", message)

    runSolverIfPossible(message, guesses)
    wireGuessButton(message)
}

private fun createMessageElement(): HTMLDivElement {
    val message = document.createElement("div") as HTMLDivElement
    message.id = "solver-extension-message"

    val style = message.style
    style.marginTop = "20px"
    style.textAlign = "center"
    style.font = "600 max(1.2vw, 1.05em)/1.3 \"Segoe UI\", Arial, sans-serif"
    style.color = "#ffffff"

    return message
}

private fun wireGuessButton(message: HTMLElement) {
    val button = document.getElementById("makeGuessButton") as? HTMLElement ?: return
    button.addEventListener("click", {
        window.setTimeout({ // to allow DOM to update
            val guesses = parseExistingGuesses()
            runSolverIfPossible(message, guesses)
        }, 0)
    })
}

private fun runSolverIfPossible(message: HTMLElement, guesses: List<Guess>) {
    if (guesses.isEmpty()) {
        message.textContent = "Solver ready."
        return
    }
    message.textContent = "Calculating..."
    val solver = Solver(SolverConfig())
    uiScope.launch {
        val report = solver.getNextStep(guesses)
        message.textContent = "Best next guesses:"
        message.appendChild(buildChoicesBlock(report))
    }
}

private fun buildChoicesBlock(report: SolverReport): HTMLElement {
    val container = document.createElement("div") as HTMLElement
    container.style.marginTop = "6px"
    if (report.bestChoices.isEmpty()) {
        container.textContent = "No valid choices."
        return container
    }

    report.bestChoices.forEachIndexed { index, choice ->
        val row = document.createElement("div") as HTMLElement
        row.textContent = "${index + 1}. ${choice.character.characterName} (${formatScore(choice.expectedCost)})"
        container.appendChild(row)
    }
    return container
}

private fun formatScore(value: Double): String {
    if (!value.isFinite()) {
        return "INF"
    }
    val rounded = round(value * 1000.0) / 1000.0
    return rounded.toString()
}

private fun parseExistingGuesses(): List<Guess> {
    val container = document.querySelector(".guesses-list") as? HTMLElement ?: return emptyList()
    val desktopRows = container.querySelectorAllElements(".guess-grid .guess-row")
    if (desktopRows.isNotEmpty()) {
        return parseDesktopRows(desktopRows)
    }
    return emptyList()
}

private fun parseDesktopRows(rows: List<Element>): List<Guess> {
    val guesses = mutableListOf<Guess>()
    for (row in rows) {
        val cards = row.querySelectorAllElements("app-card")
        if (cards.size < 7) continue
        val guess = parseGuessRow(cards, characterOverride = null, startIndex = 1) ?: continue
        guesses.add(guess)
    }
    return guesses
}

private fun parseGuessRow(cards: List<Element>, characterOverride: Character?, startIndex: Int): Guess? {
    val character = characterOverride ?: run {
        val name = extractCharacterName(cards[0])
        if (name.isEmpty()) return null
        Character.entries.firstOrNull { it.characterName.normalizeCellText() == name } ?: return null
    }

    val originalScript = extractAccuracy(cards.getOrNull(startIndex + 0)) ?: return null
    val characterType = extractAccuracy(cards.getOrNull(startIndex + 1)) ?: return null
    val wakesInNight = extractAccuracy(cards.getOrNull(startIndex + 2)) ?: return null
    val selectsPlayer = extractAccuracy(cards.getOrNull(startIndex + 3)) ?: return null
    val learnsInfo = extractAccuracy(cards.getOrNull(startIndex + 4)) ?: return null
    val abilityText = extractCellText(cards.getOrNull(startIndex + 5)) ?: return null
    val abilityMatches = parseAbilityMatches(abilityText)

    val correct = originalScript == Accuracy.CORRECT &&
        characterType == Accuracy.CORRECT &&
        wakesInNight == Accuracy.CORRECT &&
        selectsPlayer == Accuracy.CORRECT &&
        learnsInfo == Accuracy.CORRECT &&
        abilityMatches == character.ability.size

    return Guess(
        character = character,
        correct = correct,
        originalScriptAccuracy = originalScript,
        characterTypeAccuracy = characterType,
        wakesInNightAccuracy = wakesInNight,
        selectsPlayerAccuracy = selectsPlayer,
        learnsInfoAccuracy = learnsInfo,
        abilityMatches = abilityMatches
    )
}

private fun extractCharacterName(card: Element): String {
    val cell = card.querySelector(".flippableCellBackCharacter")
    return cell?.textContent?.normalizeCellText() ?: ""
}

private fun extractAccuracy(card: Element?): Accuracy? {
    val cell = card?.querySelector(".flippableCellBack") ?: return null
    return when {
        cell.classList.contains("correct") -> Accuracy.CORRECT
        cell.classList.contains("partially-correct") -> Accuracy.PARTIALLY_CORRECT
        cell.classList.contains("incorrect") -> Accuracy.INCORRECT
        else -> null
    }
}

private fun extractCellText(card: Element?): String? {
    val cell = card?.querySelector(".flippableCellBack") ?: return null
    return cell.textContent?.normalizeCellText()
}

private fun parseAbilityMatches(text: String): Int {
    val match = Regex("\\d+").findAll(text).lastOrNull()
    return match?.value?.toIntOrNull() ?: 0
}

private fun String.normalizeCellText(): String {
    return trim().replace(Regex("\\s+"), " ")
}

private fun Element.querySelectorAllElements(selector: String): List<Element> {
    val nodes = querySelectorAll(selector)
    val result = ArrayList<Element>(nodes.length)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) {
            result.add(node)
        }
    }
    return result
}
