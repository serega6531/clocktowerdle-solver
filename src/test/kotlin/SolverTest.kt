import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.stream.Stream

class SolverTest {

    @Nested
    inner class MakeGuessTests {
        @Test
        fun `makeGuess returns correct guess when characters match`() {
            val target = Character.CHEF
            val guess = Character.CHEF

            val result = makeGuess(target, guess)

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

            val result = makeGuess(target, guess)

            assertFalse(result.correct)
            assertEquals(Accuracy.CORRECT, result.originalScriptAccuracy)
            assertEquals(Accuracy.INCORRECT, result.characterTypeAccuracy)
        }

        @Test
        fun `makeGuess returns partially correct for same team character types`() {
            val target = Character.CHEF // TOWNSFOLK
            val guess = Character.BUTLER // OUTSIDER

            val result = makeGuess(target, guess)

            assertFalse(result.correct)
            assertEquals(Accuracy.PARTIALLY_CORRECT, result.characterTypeAccuracy)
        }

        @Test
        fun `makeGuess calculates ability matches correctly`() {
            val target = Character.CHEF // abilities: LEARNS_NUMBER, POSITIONING, ALIGNMENT
            val guess = Character.EMPATH // abilities: ALIGNMENT, LEARNS_NUMBER, POSITIONING

            val result = makeGuess(target, guess)

            assertEquals(3, result.abilityMatches)
        }

        @Test
        fun `makeGuess calculates zero ability matches when no overlap`() {
            val target = Character.SOLDIER // abilities: DEMON, PREVENTS_DEATH
            val guess = Character.BUTLER // abilities: NOMINATION_VOTING

            val result = makeGuess(target, guess)

            assertEquals(0, result.abilityMatches)
        }
    }

    @Nested
    inner class MatchesTests {
        @Test
        fun `matches returns true when character matches all guess criteria`() {
            val target = Character.CHEF
            val guess = Guess.correct(target)

            assertTrue(matches(target, guess))
        }

        @Test
        fun `matches filters correctly based on character type accuracy`() {
            val actualTarget = Character.BUTLER // OUTSIDER
            val guessChar = Character.CHEF // TOWNSFOLK
            val guess = makeGuess(actualTarget, guessChar)

            // BUTLER (OUTSIDER) vs CHEF (TOWNSFOLK) - same team, different type
            // So guess.characterTypeAccuracy = PARTIALLY_CORRECT
            assertEquals(Accuracy.PARTIALLY_CORRECT, guess.characterTypeAccuracy)

            // The guess tells us: we guessed TOWNSFOLK and got PARTIALLY_CORRECT
            // This means the target is the otherInTeam of TOWNSFOLK, which is OUTSIDER
            // So any OUTSIDER candidate should match
            val outsiderCandidate = Character.DRUNK // Another OUTSIDER
            assertTrue(matches(outsiderCandidate, guess))

            // But TOWNSFOLK candidates should NOT match
            assertFalse(matches(guessChar, guess))
        }

        @Test
        fun `matches validates ability matches count correctly`() {
            val actualTarget = Character.CHEF // Has some abilities
            val guessChar = Character.EMPATH // Has some overlapping abilities
            val guess = makeGuess(actualTarget, guessChar)

            // The guess contains abilityMatches count based on CHEF's abilities
            // A candidate matches if it has the same number of abilities in common with EMPATH
            // CHEF should match its own guess
            assertTrue(matches(actualTarget, guess))

            // But a character with a different overlap count should NOT match
            val differentCandidate = Character.SOLDIER
            // First check if SOLDIER actually has a different overlap with EMPATH
            val soldierOverlap = differentCandidate.ability.intersect(guessChar.ability).size
            assertNotEquals(guess.abilityMatches, soldierOverlap)
            assertFalse(matches(differentCandidate, guess))
        }

        @ParameterizedTest
        @MethodSource("SolverTest#accuracyTestCases")
        fun `matches correctly validates other accuracy attributes`(
            target: Character,
            guess: Character,
            expectedAccuracy: Accuracy,
            extractor: (Character) -> Any,
            extractAccuracy: (Guess) -> Accuracy
        ) {
            val guessResult = makeGuess(target, guess)
            val actualAccuracy = extractAccuracy(guessResult)

            // Verify the accuracy is what we expect
            assertEquals(expectedAccuracy, actualAccuracy)

            // The target should always match its own guess result
            assertTrue(matches(target, guessResult))

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
            val result = getCharacterTypeAccuracy(Character.CHEF, Character.LIBRARIAN)
            assertEquals(Accuracy.CORRECT, result)
        }

        @Test
        fun `getCharacterTypeAccuracy returns PARTIALLY_CORRECT for same team`() {
            val result = getCharacterTypeAccuracy(Character.CHEF, Character.BUTLER)
            assertEquals(Accuracy.PARTIALLY_CORRECT, result)
        }

        @Test
        fun `getCharacterTypeAccuracy returns INCORRECT for different team`() {
            val result = getCharacterTypeAccuracy(Character.CHEF, Character.IMP)
            assertEquals(Accuracy.INCORRECT, result)
        }
    }

    @Nested
    inner class AbilityMatchesTests {
        @Test
        fun `abilityMatches returns true when counts match`() {
            val character = Character.CHEF
            val guessChar = Character.EMPATH
            val guess = makeGuess(character, guessChar)

            assertTrue(abilityMatches(character, guess))
        }

        @Test
        fun `abilityMatches returns false when counts don't match`() {
            val character = Character.CHEF
            val guessChar = Character.EMPATH
            val guess = makeGuess(character, guessChar).copy(abilityMatches = 0)

            assertFalse(abilityMatches(character, guess))
        }
    }

    @Nested
    inner class GetBestChoiceTests {
        @Test
        fun `getBestChoice returns a valid character from remaining possibilities`() {
            val existingGuesses = listOf(
                makeGuess(Character.CHEF, Character.LIBRARIAN)
            )

            val result = getBestChoice(existingGuesses)

            assertNotNull(result)
            assertNotEquals(Character.LIBRARIAN, result)
        }
    }

    @Nested
    inner class GetBestStartingTests {

        @Test
        fun `getBestStarting returns all characters sorted by average distance`() {
            val result = getBestStarting()

            assertEquals(Character.entries.size, result.size)
            val distances = result.map { it.second }
            assertEquals(distances.sorted(), distances)
        }
    }

    @Nested
    inner class IntegrationTests {
        @Test
        fun `full game simulation - guessing CHEF`() {
            val target = Character.CHEF
            val guesses = mutableListOf<Guess>()

            // Make first guess ourselves (like CLI does)
            val firstGuessCharacter = Character.NO_DASHII
            val firstGuess = makeGuess(target, firstGuessCharacter)
            guesses.add(firstGuess)

            // Make subsequent guesses using solver
            var found = false
            var attempts = 1
            val maxAttempts = 5

            while (!found && attempts < maxAttempts) {
                val nextGuess = getBestChoice(guesses)
                val guess = makeGuess(target, nextGuess)
                guesses.add(guess)
                found = guess.correct
                attempts++
            }

            assertTrue(found, "Should find the target character")
            assertTrue(attempts <= Character.entries.size, "Should find in reasonable number of attempts")
        }
    }

    @Nested
    inner class ShortestPathTests {
        @Test
        fun `findShortestPaths works correctly`() {
            val target = Character.INVESTIGATOR
            val possibleCharacters = Character.entries.toSet()
            val existingGuesses = emptyList<Guess>()
            val minPaths = mutableMapOf<DistanceKey, List<List<Guess>>>()

            calculateShortestPaths(target, possibleCharacters, existingGuesses, minPaths)

            // From each character to INVESTIGATOR, except from themselves
            val expectedPathCount = Character.entries.size - 1

            assertEquals(expectedPathCount, minPaths.size, "Should find all possible paths")

            minPaths.forEach { (key, paths) ->
                assertTrue(paths.isNotEmpty(), "Should have at least one path for $key")

                paths.forEach { path ->
                    assertTrue(path.size >= 2)
                    assertEquals(paths.first().size, path.size)
                    assertEquals(key.starting, path.first().character)
                    assertEquals(key.target, path.last().character)
                }
            }
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
