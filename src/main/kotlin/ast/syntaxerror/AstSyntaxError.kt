package ast.syntaxerror

import SourceInfo

class ParserSyntaxError internal constructor(
    message: String,
    val sourceInfo: SourceInfo
) : Exception(message)

fun throwSyntaxError(message: String, sourceInfo: SourceInfo): Nothing {
    throw ParserSyntaxError(message, sourceInfo)
}
