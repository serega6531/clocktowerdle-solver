# Clockwerdle Solver

A Kotlin-based solver for [Clockwerdle](https://clocktowerdle.com/), a Wordle-style guessing game based on Blood on the Clocktower characters. The solver helps you find optimal guesses using advanced game theory algorithms.

## What is Clockwerdle?

Clockwerdle is a guessing game where players attempt to identify a Blood on the Clocktower character through a series of guesses. Each guess provides feedback across multiple attributes:

- **Original Script**: Which script the character is from
- **Character Type**: Townsfolk, Outsider, Minion, or Demon (with partial matches for same team)
- **Wakes in Night**: When/if the character acts at night
- **Selects Player**: Whether the character targets other players
- **Learns Info**: Whether the character gains information
- **Ability Matches**: Number of ability tags in common with the target

## Features

- **Best Starting Character Analysis**: Calculates the optimal first guess to maximize information gain
- **Interactive Solver**: Guides you through the game with optimal suggestions at each step
- **Configurable Parameters**: Customize max guesses, parallelism, and number of suggestions

## Algorithm

### Core Strategy

The solver uses an **expected cost minimization algorithm** (expectimax) to find optimal guesses. For each possible guess, it calculates the expected number of guesses needed to solve the puzzle by:

1. **Simulating All Outcomes**: For each candidate guess, simulate the feedback you'd receive for every possible target character
2. **Grouping by Feedback**: Group remaining possibilities by the feedback pattern they'd produce
3. **Recursive Cost Calculation**: For each feedback group, recursively calculate the expected cost to solve
4. **Weighted Average**: Compute the weighted average cost across all possible outcomes

### Expected Cost Formula

For a guess `g` with possible targets `T`:

```
ExpectedCost(g, T) = Σ P(f) × Cost(T_f)
                     f ∈ feedback(g, T)

where:
    feedback(g, T) = set of distinct feedback patterns when guessing g against targets in T
    T_f = subset of targets that produce feedback pattern f when the guess is g
    P(f) = |T_f| / |T|  (probability of getting feedback f)

    Cost(T_f) = {
        1                               if f indicates correct guess
        1 + min ExpectedCost(g', T_f)   otherwise (recursively evaluate all available guesses)
            g' ∈ available_guesses
    }
```

### Optimizations

1. **Memoization**: Caches expected costs for (targets, guessed) states to avoid redundant computation
2. **Pruning**: Eliminates guesses that fail to reduce the search space effectively
3. **Concurrent Computation**: Evaluates multiple candidate guesses in parallel using coroutines
4. **Early Termination**: Short-circuits when a branch exceeds the maximum guess limit

### Heuristics

- **Non-viable Guess Detection**: Rejects guesses that don't eliminate enough possibilities (prevents reducing N targets to N-1)
- **Guess Limit Enforcement**: Returns infinite cost when max guesses would be exceeded

## Usage

### Prerequisites

- Java 21 or higher
- Gradle (wrapper included)

### Building

```bash
./gradlew installDist
```

### Running

#### Find Best Starting Character

```bash
.\build\install\clockwerdle-solver\bin\clockwerdle-solver best-starting
```

#### Interactive Solver

```bash
.\build\install\clockwerdle-solver\bin\clockwerdle-solver interactive
```

The interactive mode guides you step-by-step:

1. Enter your guess (character name)
2. Provide the feedback from the game:
   - Format: `ORIGINAL CHARACTER_TYPE WAKES SELECTS LEARNS ABILITY_MATCHES`
   - Use `CORRECT`, `PARTIALLY_CORRECT`, or `INCORRECT` (or `+`, `~`, `-`)
   - Or just `CORRECT` if you guessed correctly
3. Receive optimal next guess suggestions
4. Repeat until solved

Example feedback input:
```
CORRECT PARTIALLY_CORRECT INCORRECT CORRECT INCORRECT 2
```
or shorthand:
```
+ ~ - + - 2
```

### Command-Line Options

All commands support:
- `--max-guesses N` / `-g N`: Set maximum of guesses allowed (default: 4)
- `--max-in-flight N` / `-j N`: Set parallelism level (default: 16)
- `--top-choice-limit N` / `-t N`: Number of top choices to display (default: 5)
- `--include-inefficient-branches` / `-i`: Include branches where the guess cannot be optimal

Example:
```bash
.\build\install\clockwerdle-solver\bin\clockwerdle-solver best-starting -g 5 -j 2
```

### Example
```
.\build\install\clockwerdle-solver\bin\clockwerdle-solver interactive
Enter your guess: DRUNK
Enter the feedback (e.g. +, ~, -, +, -, 1) or CORRECT:
> - - - - - 0
Possible targets (32):
Al-Hadikhia, Assassin, Boffin, Boomdandy, Cerenovus, Devils Advocate, Evil Twin, Fang Gu, Fearmonger, Goblin, Godfather, Harpy, Kazali, Legion, Leviathan, Lil Monsta, Lord Of Typhon, Mastermind, Mezepheles, Ojo, Pit Hag, Po, Psychopath, Riot, Shabaloth, Summoner, Vizier, Witch, Wizard, Wraith, Yaggababble, Zombuul
Best next guesses (5):
1) Kazali (2.188), 2) Lil Monsta (2.188), 3) Lord Of Typhon (2.188), 4) Mastermind (2.250), 5) Summoner (2.250)
Choose next guess by number or name: Kazali
Next guess: Kazali
Enter the feedback (e.g. +, ~, -, +, -, 1) or CORRECT:
> + ~ - - - 0
Possible targets (2):
Boffin, Mezepheles
Best next guesses (2):
1) Boffin (1.500), 2) Mezepheles (1.500)
Choose next guess by number or name: 1
Next guess: Boffin
Enter the feedback (e.g. +, ~, -, +, -, 1) or CORRECT:
> CORRECT
Well done!
```