package ast

import SourceInfo
import ast.expression.parseExpressionUntil
import TokenEndBlock
import compiler.backends.emulator.emulator.Emulator
import tokenizeLine
import tokenizeLines
import tokens.Token
import tokens.TokenType
import tokens.parseFile
import tokens.parseLine
import java.io.StringReader
import kotlin.test.assertEquals


val na = SourceInfo.notApplicable
fun parse(tokens: List<Token>): List<AstNode> {
    return AstParser(tokens).parse()
}

fun parserFromLine(line: String): AstParser {
    return AstParser(tokenizeLine(line))
}

fun parserFromFile(file: String): AstParser {
    return AstParser(tokenizeLines(file))
}

fun parseExpressions(tokens: List<Token>): List<AstNode> {
    return AstParser(tokens + listOf(TokenEndBlock)).parseStatementsUntilEndblock()
}

fun parseExpression(expression: String): AstNode {
    val tokensIterator = TokenIterator(tokenizeLine(expression))
    return parseExpressionUntil(tokensIterator, TokenType.EOL)
}

fun assign(to: String, value: Int): AstNode {
    return AstNode.fromAssign(identifier(to), constant(value), na)
}

fun identifier(name: String): AstNode {
    return AstNode.fromIdentifier(name, na)
}

fun constant(value: Int): AstNode {
    return AstNode.fromConstant(value, na)
}

fun variable(name: String, type: String = "byte", isPointer: Boolean = false, isArray: Boolean = false): AstNode {
    return AstNode.fromNewVariable(name, TypeDefinition(type, isPointer = isPointer, isArray = isArray), null, na)
}

fun call(target: String, parameters: List<AstNode>): AstNode {
    return AstNode.fromCall(target, FunctionType.Normal, parameters, na)
}

fun function(name: String, arguments: List<AstNode>, body: List<AstNode>, returnTypeName: String?): AstNode {
    val returnTypeDef = if (returnTypeName != null) TypeDefinition(returnTypeName) else null
    return AstNode.fromFunction(name, FunctionType.Normal, arguments, body, returnTypeDef, na)
}

private fun transformRecursive(node: AstNode, transform: (AstNode) -> AstNode): AstNode {
    val newChilds = node.childNodes.map { transformRecursive(it, transform) }

    val childCopy = node.copy(childNodes = newChilds)
    return transform.invoke(childCopy)
}

fun assertEqualsIgnoreSource(expected: AstNode, actual: AstNode) {
    val ex = expected.ignoreSource()
    val act = actual.ignoreSource()
    return assertEquals(ex, act)
}

fun assertEqualsIgnoreSource(expected: List<AstNode>, actual: List<AstNode>) {
    return assertEquals(expected.ignoreSource(), actual.ignoreSource())
}

fun AstNode.ignoreSource(): AstNode {
    val result = transformRecursive(this) { node -> node.copy(sourceInfo = na) }
    return result
}

fun List<AstNode>.ignoreSource(): List<AstNode> {
    return this.map { it.ignoreSource() }
}