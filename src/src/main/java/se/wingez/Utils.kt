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
    if (value !in 0..255)
        throw AssertionError()
    return value.toUByte()
}
