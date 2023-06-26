package ast.syntaxerror

import CompilerError
import SourceInfo

class ParserSyntaxError internal constructor(
    message: String,
    sourceInfo: SourceInfo
) : CompilerError(message, sourceInfo) {
}

fun throwSyntaxError(message: String, sourceInfo: SourceInfo): Nothing {
    throw ParserSyntaxError(message, sourceInfo)
}
