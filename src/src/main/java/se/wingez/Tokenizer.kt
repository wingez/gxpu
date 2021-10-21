package se.wingez

class Tokenizer {

    fun getIndentation(line: String): Pair<Int, String> {
        var indentation = 0
        var hasTabs = false
        var hasSpaces = false
        var current = line

        while (true) {
            var indentLetters = 0

            if (current.startsWith("\t")) {
                if (hasSpaces)
                    throw ClassCircularityError("help me")
                hasTabs = true
                indentLetters = 1
            }
            if (current.startsWith("  ")) {
                if (hasTabs)
                    throw  ClassCircularityError(" help me 2")
                hasSpaces = true
                indentLetters = 2
            }

            if (current[0] == ' ' && current[1] != ' ')
                throw ClassCircularityError("sdfsdf")

            if (indentLetters > 0) {
                indentation++
                current = current.substring(indentLetters)
            } else {
                return Pair(indentation, current)
            }
        }
    }
}