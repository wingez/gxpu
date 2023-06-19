package se.wingez.ast

import ast.expression.parseExpressionUntil
import se.wingez.TokenEndBlock
import se.wingez.tokens.Token
import se.wingez.tokens.TokenType
import se.wingez.tokens.parseFile
import se.wingez.tokens.parseLine
import java.io.StringReader

fun parse(tokens: List<Token>): List<AstNode> {
    return AstParser(tokens).parse()
}

fun parserFromLine(line: String): AstParser {
    return AstParser(parseLine(line))
}

fun parserFromFile(file: String): AstParser {
    return AstParser(parseFile(StringReader(file)))
}

fun parseExpressions(tokens: List<Token>): List<AstNode> {
    return AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()
}

fun parseExpression(expression: String): AstNode {
    val tokensIterator = TokenIterator(parseLine(expression))
    return parseExpressionUntil(tokensIterator, TokenType.EOL)
}

fun assign(to: String, value: Int): AstNode {
    return AstNode.fromAssign(identifier(to), constant(value))
}

fun identifier(name: String): AstNode {
    return AstNode.fromIdentifier(name)
}

fun constant(value: Int): AstNode {
    return AstNode.fromConstant(value)
}

fun variable(name: String, type: String = "byte", isPointer: Boolean = false, isArray: Boolean = false): AstNode {
    return AstNode.fromNewVariable(name, TypeDefinition(type, isPointer = isPointer, isArray = isArray), null)
}

fun call(target: String, parameters: List<AstNode>): AstNode {
    return AstNode.fromCall(target, FunctionType.Normal, parameters)
}

fun function(name: String, arguments: List<AstNode>, body: List<AstNode>, returnTypeName: String?): AstNode {
    val returnTypeDef = if (returnTypeName != null) TypeDefinition(returnTypeName) else null
    return AstNode.fromFunction(name, FunctionType.Normal, arguments, body, returnTypeDef)
}
