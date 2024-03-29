package ast.expression

import SourceInfo
import ast.AstNode
import ast.NodeTypes
import tokens.Token
import tokens.TokenType

enum class ValueType {
    Token,
    Node,
    ParenthesisBlock,
    BracketsBlock,
}

class Value private constructor(
    val type: ValueType,
    private val _token: Token? = null,
    private val _node: AstNode? = null,
    private val _nodeList: List<AstNode>? = null,
    private val _blockSourceInfo: SourceInfo? = null
) {
    val token get() = _token!!
    val node get() = _node!!

    val nodeList get() = _nodeList!!

    override fun toString(): String {
        return when (type) {
            ValueType.Node -> node.toString()
            ValueType.Token -> token.toString()
            ValueType.ParenthesisBlock -> "($nodeList)"
            ValueType.BracketsBlock -> "[$nodeList]"
        }
    }

    val sourceInfo
        get() = when (type) {
            ValueType.Token -> token.sourceInfo
            ValueType.Node -> node.sourceInfo
            ValueType.BracketsBlock,ValueType.ParenthesisBlock -> _blockSourceInfo!!
        }

    companion object {
        fun node(node: AstNode): Value = Value(ValueType.Node, _node = node)
        fun token(token: Token): Value = Value(ValueType.Token, _token = token)
        fun parenthesisBlock(content: List<AstNode>, sourceInfo: SourceInfo): Value =
            Value(ValueType.ParenthesisBlock, _nodeList = content, _blockSourceInfo = sourceInfo)

        fun bracketBlock(content: List<AstNode>, sourceInfo: SourceInfo): Value =
            Value(ValueType.BracketsBlock, _nodeList = content, _blockSourceInfo = sourceInfo)

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
        return value.type == ValueType.Node && value.node.type == nodeTypes
    }
}

class TokenMatcher(
    private val tokenType: TokenType
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        return value.type == ValueType.Token && value.token.type == tokenType
    }
}

class MultiTokenMatcher(
    private val tokens: Set<TokenType>
) : ValueMatcher {
    override fun match(value: Value): Boolean {
        if (value.type != ValueType.Token) {
            return false
        }
        return value.token.type in tokens
    }
}
