package ru.serega6531.clocktowerdle

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SolverTest {

    private val defaultSolver: Solver
        get() = Solver(SolverConfig())

    @Nested
    inner class MakeGuessTests {
        @Test
        fun `makeGuess returns correct guess when characters match`() {
            val target = Character.CHEF
            val guess = Character.CHEF

            val result = defaultSolver.makeGuess(target, guess)

            assertTrue(result.correct)
            assertEquals(Accuracy.CORRECT, result.originalScriptAccuracy)
            assertEquals(Accuracy.CORRECT, result.characterTypeAccuracy)
            assertEquals(Accuracy.CORRECT, result.wakesInNightAccuracy)
            assertEquals(Accuracy.CORRECT, result.selectsPlayerAccuracy)
            assertEquals(Accuracy.CORRECT, result.learnsInfoAccuracy)
            assertEquals(target.ability.size, result.abilityMatches)
        }

        @Test
        fun `makeGuess returns incorrect attributes when characters differ`() {
            val target = Character.CHEF // TOWNSFOLK, TROUBLE_BREWING
            val guess = Character.IMP // DEMON, TROUBLE_BREWING

            val result = defaultSolver.makeGuess(target, guess)

            assertFalse(result.correct)
            assertEquals(Accuracy.CORRECT, result.originalScriptAccuracy)
            assertEquals(Accuracy.INCORRECT, result.characterTypeAccuracy)
        }

        @Test
        fun `makeGuess returns partially correct for same team character types`() {
            val target = Character.CHEF // TOWNSFOLK
            val guess = Character.BUTLER // OUTSIDER

            val result = defaultSolver.makeGuess(target, guess)

            assertFalse(result.correct)
            assertEquals(Accuracy.PARTIALLY_CORRECT, result.characterTypeAccuracy)
        }

        @Test
        fun `makeGuess calculates ability matches correctly`() {
            val target = Character.CHEF // abilities: LEARNS_NUMBER, POSITIONING, ALIGNMENT
            val guess = Character.EMPATH // abilities: ALIGNMENT, LEARNS_NUMBER, POSITIONING

            val result = defaultSolver.makeGuess(target, guess)

            assertEquals(3, result.abilityMatches)
        }

        @Test
        fun `makeGuess calculates zero ability matches when no overlap`() {
            val target = Character.SOLDIER // abilities: DEMON, PREVENTS_DEATH
            val guess = Character.BUTLER // abilities: NOMINATION_VOTING

            val result = defaultSolver.makeGuess(target, guess)

            assertEquals(0, result.abilityMatches)
        }
    }

    @Nested
    inner class MatchesTests {
        @Test
        fun `matches returns true when character matches all guess criteria`() {
            val target = Character.CHEF
            val guess = Guess.correct(target)

            assertTrue(defaultSolver.matches(target, guess))
        }

        @Test
        fun `matches filters correctly based on character type accuracy`() {
            val actualTarget = Character.BUTLER // OUTSIDER
            val guessChar = Character.CHEF // TOWNSFOLK
            val guess = defaultSolver.makeGuess(actualTarget, guessChar)

            // BUTLER (OUTSIDER) vs CHEF (TOWNSFOLK) - same team, different type
            // So guess.characterTypeAccuracy = PARTIALLY_CORRECT
            assertEquals(Accuracy.PARTIALLY_CORRECT, guess.characterTypeAccuracy)

            // The guess tells us: we guessed TOWNSFOLK and got PARTIALLY_CORRECT
            // This means the target is the otherInTeam of TOWNSFOLK, which is OUTSIDER
            // So any OUTSIDER candidate should match
            val outsiderCandidate = Character.DRUNK // Another OUTSIDER
            assertTrue(defaultSolver.matches(outsiderCandidate, guess))

            // But TOWNSFOLK candidates should NOT match
            assertFalse(defaultSolver.matches(guessChar, guess))
        }

        @Test
        fun `matches validates ability matches count correctly`() {
            val actualTarget = Character.CHEF // Has some abilities
            val guessChar = Character.EMPATH // Has some overlapping abilities
            val guess = defaultSolver.makeGuess(actualTarget, guessChar)

            // The guess contains abilityMatches count based on CHEF's abilities
            // A candidate matches if it has the same number of abilities in common with EMPATH
            // CHEF should match its own guess
            assertTrue(defaultSolver.matches(actualTarget, guess))

            // But a character with a different overlap count should NOT match
            val differentCandidate = Character.SOLDIER
            // First check if SOLDIER actually has a different overlap with EMPATH
            val soldierOverlap = differentCandidate.ability.count { it in guessChar.ability }
            assertNotEquals(guess.abilityMatches, soldierOverlap)
            assertFalse(defaultSolver.matches(differentCandidate, guess))
        }

        @ParameterizedTest
        @MethodSource("ru.serega6531.clocktowerdle.SolverTest#accuracyTestCases")
        fun `matches correctly validates other accuracy attributes`(
            target: Character,
            guess: Character,
            expectedAccuracy: Accuracy,
            extractor: (Character) -> Any,
            extractAccuracy: (Guess) -> Accuracy
        ) {
            val guessResult = defaultSolver.makeGuess(target, guess)
            val actualAccuracy = extractAccuracy(guessResult)

            // Verify the accuracy is what we expect
            assertEquals(expectedAccuracy, actualAccuracy)

            // The target should always match its own guess result
            assertTrue(defaultSolver.matches(target, guessResult))

            val guessValue = extractor(guess)
            val targetValue = extractor(target)

            // When accuracy is CORRECT, guess character has the same value as target
            if (expectedAccuracy == Accuracy.CORRECT) {
                assertEquals(guessValue, targetValue, "When accuracy is CORRECT, target and guess should have same value")
            }

            // When accuracy is INCORRECT, guess character has a different value than target
            if (expectedAccuracy == Accuracy.INCORRECT) {
                assertNotEquals(guessValue, targetValue, "When accuracy is INCORRECT, target and guess should have different values")
            }
        }
    }

    @Nested
    inner class CharacterTypeAccuracyTests {
        @Test
        fun `getCharacterTypeAccuracy returns CORRECT for same type`() {
            val result = defaultSolver.getCharacterTypeAccuracy(Character.CHEF, Character.LIBRARIAN)
            assertEquals(Accuracy.CORRECT, result)
        }

        @Test
        fun `getCharacterTypeAccuracy returns PARTIALLY_CORRECT for same team`() {
            val result = defaultSolver.getCharacterTypeAccuracy(Character.CHEF, Character.BUTLER)
            assertEquals(Accuracy.PARTIALLY_CORRECT, result)
        }

        @Test
        fun `getCharacterTypeAccuracy returns INCORRECT for different team`() {
            val result = defaultSolver.getCharacterTypeAccuracy(Character.CHEF, Character.IMP)
            assertEquals(Accuracy.INCORRECT, result)
        }
    }

    @Nested
    inner class AbilityMatchesTests {
        @Test
        fun `abilityMatches returns true when counts match`() {
            val character = Character.CHEF
            val guessChar = Character.EMPATH
            val guess = defaultSolver.makeGuess(character, guessChar)

            assertTrue(defaultSolver.abilityMatches(character, guess))
        }

        @Test
        fun `abilityMatches returns false when counts don't match`() {
            val character = Character.CHEF
            val guessChar = Character.EMPATH
            val guess = defaultSolver.makeGuess(character, guessChar).copy(abilityMatches = 0)

            assertFalse(defaultSolver.abilityMatches(character, guess))
        }
    }

    @Nested
    inner class GetNextStepTests {
        @Test
        fun `getNextStep returns a valid character from remaining possibilities`() = runTest {
            val existingGuesses = listOf(
                defaultSolver.makeGuess(Character.CHEF, Character.LIBRARIAN)
            )

            val result = defaultSolver.getNextStep(existingGuesses)

            assertTrue(result.bestChoices.isNotEmpty())
            assertFalse(result.bestChoices.any { it.character == Character.LIBRARIAN })
        }
    }

    @Nested
    inner class GetBestStartingTests {

        @Test
        fun `getBestStarting returns all characters sorted by average distance`() = runTest(timeout = 1.hours) {
            val result = defaultSolver.getBestStarting()

            assertEquals(Character.entries.size, result.size)
            val distances = result.map { it.second }
            assertEquals(distances.sorted(), distances)

            result.forEach { println("${it.first.characterName} -> ${it.second}") }
        }
    }

    @Nested
    inner class IntegrationTests {
        @Test
        fun `full game simulation - guessing CHEF`() = runTest {
            val target = Character.CHEF
            val guesses = mutableListOf<Guess>()

            // Make first guess ourselves (like CLI does)
            val firstGuessCharacter = Character.NO_DASHII
            val firstGuess = defaultSolver.makeGuess(target, firstGuessCharacter)
            guesses.add(firstGuess)

            // Make subsequent guesses using solver
            var found = false
            var attempts = 1
            val maxAttempts = 5

            while (!found && attempts < maxAttempts) {
                val report = defaultSolver.getNextStep(guesses)
                val nextGuess = assertNotNull(report.bestChoices.randomOrNull()?.character)
                val guess = defaultSolver.makeGuess(target, nextGuess)
                guesses.add(guess)
                found = guess.correct
                attempts++
            }

            assertTrue(found, "Should find the target character")
            assertTrue(attempts <= Character.entries.size, "Should find in reasonable number of attempts")
        }
    }

    companion object {
        @JvmStatic
        fun accuracyTestCases(): Stream<Arguments> = Stream.of(
            // originalScript tests - CORRECT case
            Arguments.of(
                Character.CHEF, // originalScript = TROUBLE_BREWING
                Character.LIBRARIAN, // originalScript = TROUBLE_BREWING
                Accuracy.CORRECT,
                { c: Character -> c.originalScript },
                { g: Guess -> g.originalScriptAccuracy }
            ),
            // originalScript tests - INCORRECT case
            Arguments.of(
                Character.CHEF, // originalScript = TROUBLE_BREWING
                Character.CLOCKMAKER, // originalScript = SECTS_AND_VIOLETS
                Accuracy.INCORRECT,
                { c: Character -> c.originalScript },
                { g: Guess -> g.originalScriptAccuracy }
            ),
            // characterType tests - CORRECT case
            Arguments.of(
                Character.CHEF, // TOWNSFOLK
                Character.LIBRARIAN, // TOWNSFOLK (same type)
                Accuracy.CORRECT,
                { c: Character -> c.characterType },
                { g: Guess -> g.characterTypeAccuracy }
            ),
            // characterType tests - INCORRECT case
            Arguments.of(
                Character.IMP, // DEMON
                Character.CHEF, // TOWNSFOLK (different team)
                Accuracy.INCORRECT,
                { c: Character -> c.characterType },
                { g: Guess -> g.characterTypeAccuracy }
            ),
            // wakesInNight tests - CORRECT case
            Arguments.of(
                Character.CHEF, // wakesInNight = ONCE
                Character.LIBRARIAN, // wakesInNight = ONCE
                Accuracy.CORRECT,
                { c: Character -> c.wakesInNight },
                { g: Guess -> g.wakesInNightAccuracy }
            ),
            // wakesInNight tests - INCORRECT case
            Arguments.of(
                Character.EMPATH, // wakesInNight = ALWAYS
                Character.CHEF, // wakesInNight = ONCE
                Accuracy.INCORRECT,
                { c: Character -> c.wakesInNight },
                { g: Guess -> g.wakesInNightAccuracy }
            ),
            // selectsPlayer tests - CORRECT case
            Arguments.of(
                Character.CHEF, // selectsPlayer = NO
                Character.LIBRARIAN, // selectsPlayer = NO
                Accuracy.CORRECT,
                { c: Character -> c.selectsPlayer },
                { g: Guess -> g.selectsPlayerAccuracy }
            ),
            // selectsPlayer tests - INCORRECT case
            Arguments.of(
                Character.POISONER, // selectsPlayer = REQUIRED
                Character.CHEF, // selectsPlayer = NO
                Accuracy.INCORRECT,
                { c: Character -> c.selectsPlayer },
                { g: Guess -> g.selectsPlayerAccuracy }
            ),
            // learnsInfo tests - CORRECT case
            Arguments.of(
                Character.CHEF, // learnsInfo = YES
                Character.LIBRARIAN, // learnsInfo = YES
                Accuracy.CORRECT,
                { c: Character -> c.learnsInfo },
                { g: Guess -> g.learnsInfoAccuracy }
            ),
            // learnsInfo tests - INCORRECT case
            Arguments.of(
                Character.CHEF, // learnsInfo = YES
                Character.SOLDIER, // learnsInfo = NO
                Accuracy.INCORRECT,
                { c: Character -> c.learnsInfo },
                { g: Guess -> g.learnsInfoAccuracy }
            )
        )
    }
}
