package se.wingez

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

fun bytes(vararg bytes: Int): List<UByte> {
    val result = mutableListOf<UByte>()
    for (byte in bytes) {

        result.add(byte(byte))
    }
    return result
}

fun byte(value: Int): UByte {
    val wrappedVal = value % 0x100
//
//    if (wrappedVal !in 0..255)
//        throw AssertionError()
    return wrappedVal.toUByte()
}

fun byte(value: UInt): UByte {
    return byte(value.toInt())
}

interface SupportTypePeekIterator<T : Enum<T>> {
    val type: T
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


open class TypePeekIterator<T : SupportTypePeekIterator<S>, S : Enum<S>>(values: List<T>) :
    PeekIterator<T>(values) {

    private var index = 0

    fun peekIs(type: S, consumeMatch: Boolean = false): Boolean {
        val result = peek().type == type
        if (result && consumeMatch) {
            consume()
        }
        return result
    }

    fun consumeType(type: S): T {
        return consumeType(type, "Expected token to be of type $type")
    }

    fun consumeType(type: S, errorMessage: String): T {
        if (!peekIs(type))
            throw Error(errorMessage)
        return consume()
    }
}

