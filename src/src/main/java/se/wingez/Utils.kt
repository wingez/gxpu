package se.wingez

class Utils {
    companion object {
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
    }
}