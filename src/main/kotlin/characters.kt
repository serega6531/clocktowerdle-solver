fun loadCharacters(): List<Character> {
    // characters.js extracted from the website
    val charactersScript = readResource("/characters.js") ?: error("Failed to load characters.js")

    val characterGroupRegex = Regex("\\{(.+?)}", RegexOption.DOT_MATCHES_ALL)
    return characterGroupRegex.findAll(charactersScript)
        .map { parseCharacter(it.value) }
        .toList()
}

private fun readResource(resource: String): String? {
    val resourceStream = object {}.javaClass.getResourceAsStream(resource) ?: return null
    return resourceStream.bufferedReader().use { it.readText() }
}

private fun parseCharacter(text: String): Character {
    val attributes = text
        .removePrefix("{")
        .removeSuffix("}")
        .lines()
        .map { it.trim().removeSuffix(",") }
        .filter { it.isNotBlank() }
        .map { it.split(": ") }
        .associate { it[0] to it[1] }

    return Character(
        name = attributes.getValue("name").removeSurrounding("\""),
        originalScript = attributes.getValue("originalScript").let { OriginalScript.entries[it.toInt()] },
        characterType = attributes.getValue("characterType").let { CharacterType.entries[it.toInt()] },
        wakesInNight = attributes.getValue("wakesInNight").let { WakesInNight.entries[it.toInt()] },
        selectsPlayer = attributes.getValue("selectsPlayer").let { SelectsPlayer.entries[it.toInt()] },
        learnsInfo = attributes.getValue("learnsInfo").let { LearnsInfo.entries[it.toInt()] },
        ability = attributes.getValue("ability")
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().toInt() }
            .map { Ability.entries[it] }
            .toSet()
    )
}

data class Character(
    val name: String,
    val originalScript: OriginalScript,
    val characterType: CharacterType,
    val wakesInNight: WakesInNight,
    val selectsPlayer: SelectsPlayer,
    val learnsInfo: LearnsInfo,
    val ability: Set<Ability>
)

enum class OriginalScript {
    TROUBLE_BREWING,
    SECTS_AND_VIOLETS,
    BAD_MOON_RISING,
    EXPERIMENTAL
}

enum class CharacterType {
    TOWNSFOLK,
    OUTSIDER,
    MINION,
    DEMON;

    fun otherInTeam(): CharacterType {
        return when (this) {
            TOWNSFOLK -> OUTSIDER
            OUTSIDER -> TOWNSFOLK
            MINION -> DEMON
            DEMON -> MINION
        }
    }

    fun otherTeam(): List<CharacterType> {
        return when (this) {
            TOWNSFOLK, OUTSIDER -> listOf(MINION, DEMON)
            MINION, DEMON -> listOf(TOWNSFOLK, OUTSIDER)
        }
    }
}

enum class WakesInNight {
    NO,
    SOMETIMES,
    ONCE,
    ALWAYS,
    ALWAYS_EXCEPT_FIRST,
    QUESTION_MARK
}

enum class SelectsPlayer {
    NO,
    OPTIONALLY,
    REQUIRED,
    QUESTION_MARK
}

enum class LearnsInfo {
    NO,
    YES,
    QUESTION_MARK
}

 enum class Ability {
    PREVENTS_DEATH,
    ON_DEATH,
    EXECUTION,
    CAUSES_DEATH,
    DROISONING,
    LEARNS_CHARACTER,
    SELECTS_CHARACTER,
    SPECIFIC_CHARACTER,
    YES_NO,
    LEARNS_NUMBER,
    PUBLIC,
    NOMINATION_VOTING,
    ONCE_FIRST_TIME,
    SETUP,
    MADNESS,
    ALIGNMENT,
    OUTSIDERS,
    MINIONS,
    TOWNSFOLK,
    DEMON,
    WIN_LOSS,
    CHANGES_CHARACTER,
    CAN_REVIVE,
    POSITIONING,
    STORYTELLER,
    QUESTION_MARK
}