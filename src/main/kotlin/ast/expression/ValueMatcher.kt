package se.wingez.ast.expression

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.tokens.Token
import se.wingez.tokens.TokenType

enum class ValueType {
    Token,
    Node,
    ParenthesisBlock,
    BracketsBlock,
}

class Value(
    val type: ValueType,
    private val token: Token? = null,
    private val node: AstNode? = null,
    private val nodeList: List<AstNode>? = null
) {
    val valueToken get() = token!!
    val valueNode get() = node!!

    val valueNodeList get() = nodeList!!

    override fun toString(): String {
        return when (type) {
            ValueType.Node -> valueNode.toString()
            ValueType.Token -> valueToken.toString()
            ValueType.ParenthesisBlock -> "($valueNodeList)"
            ValueType.BracketsBlock -> "[$valueNodeList]"
        }
    }
}

interface ValueMatcher {
    fun match(value: Value): Boolean
}

object anythingMatcher : ValueMatcher {
    override fun match(value: Value) = true
}

object nothingMatcher : ValueMatcher {
    override fun match(value: Value) = false
}

object anyNodeMatcher : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Node
    }
}

class TypeMatcher(val type: ValueType) : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == type
    }
}

class NodeMatcher(
    private val nodeTypes: NodeTypes
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Node && value.valueNode.type == nodeTypes
    }
}

class TokenMatcher(
    private val tokenType: TokenType
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Token && value.valueToken.type == tokenType
    }
}

class MultiTokenMatcher(
    private val tokens: Set<TokenType>
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        if (value.type != ValueType.Token) {
            return false
        }
        return value.valueToken.type in tokens
    }
}
