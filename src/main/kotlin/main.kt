fun main() {
    val characters = loadCharacters()
    val bestStarting = getBestStarting(characters)

    bestStarting.forEach { println("${it.first.name} -> ${it.second}") }
}


