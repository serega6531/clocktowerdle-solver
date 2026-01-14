import java.io.File

object CharacterEnumGenerator {
    private val ORIGINAL_SCRIPTS = listOf("TROUBLE_BREWING", "SECTS_AND_VIOLETS", "BAD_MOON_RISING", "EXPERIMENTAL")
    private val CHARACTER_TYPES = listOf("TOWNSFOLK", "OUTSIDER", "MINION", "DEMON")
    private val WAKES_IN_NIGHTS = listOf("NO", "SOMETIMES", "ONCE", "ALWAYS", "ALWAYS_EXCEPT_FIRST", "QUESTION_MARK")
    private val SELECTS_PLAYERS = listOf("NO", "OPTIONALLY", "REQUIRED", "QUESTION_MARK")
    private val LEARNS_INFOS = listOf("NO", "YES", "QUESTION_MARK")
    private val ABILITIES = listOf("PREVENTS_DEATH", "ON_DEATH", "EXECUTION", "CAUSES_DEATH", "DROISONING", "LEARNS_CHARACTER", "SELECTS_CHARACTER", "SPECIFIC_CHARACTER", "YES_NO", "LEARNS_NUMBER", "PUBLIC", "NOMINATION_VOTING", "ONCE_FIRST_TIME", "SETUP", "MADNESS", "ALIGNMENT", "OUTSIDERS", "MINIONS", "TOWNSFOLK", "DEMON", "WIN_LOSS", "CHANGES_CHARACTER", "CAN_REVIVE", "POSITIONING", "STORYTELLER", "QUESTION_MARK")

    fun generate(inputFile: File, outputFile: File) {
        val charactersScript = inputFile.readText()
        val characters = parseCharacters(charactersScript)

        val generatedCode = generateEnum(characters)

        outputFile.parentFile.mkdirs()
        outputFile.writeText(generatedCode)

        println("Generated ${characters.size} character enum entries to ${outputFile.path}")
    }

    private fun parseCharacters(text: String): List<CharacterData> {
        val characterGroupRegex = Regex("\\{(.+?)}", RegexOption.DOT_MATCHES_ALL)
        return characterGroupRegex.findAll(text)
            .map { parseCharacter(it.value) }
            .toList()
    }

    private fun parseCharacter(text: String): CharacterData {
        val attributes = text
            .removePrefix("{")
            .removeSuffix("}")
            .lines()
            .map { it.trim().removeSuffix(",") }
            .filter { it.isNotBlank() }
            .map { it.split(": ") }
            .associate { it[0] to it[1] }

        return CharacterData(
            name = attributes.getValue("name").removeSurrounding("\""),
            originalScript = attributes.getValue("originalScript").toInt(),
            characterType = attributes.getValue("characterType").toInt(),
            wakesInNight = attributes.getValue("wakesInNight").toInt(),
            selectsPlayer = attributes.getValue("selectsPlayer").toInt(),
            learnsInfo = attributes.getValue("learnsInfo").toInt(),
            ability = attributes.getValue("ability")
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .map { it.trim().toInt() }
        )
    }

    private fun generateEnum(characters: List<CharacterData>): String {
        val enumConstants = characters.joinToString(",\n") { char ->
            val enumName = char.name.toEnumConstant()
            val abilityList = char.ability.joinToString(", ") { "Ability.${ABILITIES[it]}" }
            """    $enumName(
        characterName = "${char.name}",
        originalScript = OriginalScript.${ORIGINAL_SCRIPTS[char.originalScript]},
        characterType = CharacterType.${CHARACTER_TYPES[char.characterType]},
        wakesInNight = WakesInNight.${WAKES_IN_NIGHTS[char.wakesInNight]},
        selectsPlayer = SelectsPlayer.${SELECTS_PLAYERS[char.selectsPlayer]},
        learnsInfo = LearnsInfo.${LEARNS_INFOS[char.learnsInfo]},
        ability = setOf($abilityList)
    )"""
        }

        return """// This file is auto-generated. Do not edit manually.
// Generated from characters.js

enum class Character(
    val characterName: String,
    val originalScript: OriginalScript,
    val characterType: CharacterType,
    val wakesInNight: WakesInNight,
    val selectsPlayer: SelectsPlayer,
    val learnsInfo: LearnsInfo,
    val ability: Set<Ability>
) {
$enumConstants
}

enum class OriginalScript {
    ${ORIGINAL_SCRIPTS.joinToString(",\n    ")}
}

enum class CharacterType {
    ${CHARACTER_TYPES.joinToString(",\n    ")};

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
    ${WAKES_IN_NIGHTS.joinToString(",\n    ")}
}

enum class SelectsPlayer {
    ${SELECTS_PLAYERS.joinToString(",\n    ")}
}

enum class LearnsInfo {
    ${LEARNS_INFOS.joinToString(",\n    ")}
}

enum class Ability {
    ${ABILITIES.joinToString(",\n    ")}
}
"""
    }

    private fun String.toEnumConstant(): String {
        return this.uppercase()
            .replace(" ", "_")
            .replace("-", "_")
    }

    private data class CharacterData(
        val name: String,
        val originalScript: Int,
        val characterType: Int,
        val wakesInNight: Int,
        val selectsPlayer: Int,
        val learnsInfo: Int,
        val ability: List<Int>
    )
}
