fun splitMany(toSplit: String, delimiters: Iterable<String>): List<String> {
    val delimitersCopy = delimiters.toMutableList()

    var current = mutableListOf(toSplit)

    while (delimitersCopy.isNotEmpty()) {
        val delimiter = delimitersCopy.removeLast()
        val newCurrent = mutableListOf<String>()

        for (c in current) {
            newCurrent.addAll(c.split(delimiter))
        }
        current = newCurrent
    }
    return current
}

open class PeekIterator<T>(
    private val values: List<T>
) {
    private var index = 0

    fun getCurrentIndex(): Int {
        return index
    }

    fun hasMore(): Boolean {
        return index < values.size
    }

    fun peek(): T {
        if (index >= values.size)
            throw Error("End of token-list reached")
        return values[index]
    }

    fun consume(): T {
        return values[index++]
    }
}

fun requireNotReached():Nothing{
    throw AssertionError("This should never be reached")
}