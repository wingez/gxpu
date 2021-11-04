package se.wingez.ast

import se.wingez.tokens.TokenNumericConstant

abstract class AstNode
abstract class StatementNode : AstNode()
abstract class ValueNode : StatementNode()


data class Identifier(val name: String) : ValueNode()


data class PrimitiveMemberDeclaration(
    val name: String,
    val type: String,
    val explicitNew: Boolean = false,
    val isArray: Boolean = false,
) : StatementNode()


data class AssignNode(
    val target: ValueNode,
    val value: ValueNode?,
    val type: String = "",
    val explicitNew: Boolean = false,

    ) : StatementNode()

data class PrintNode(val target: ValueNode) : StatementNode()
data class ConstantNode(val value: Int) : ValueNode() {
    companion object {
        fun fromToken(token: TokenNumericConstant): ConstantNode {
            return ConstantNode(value = token.value)
        }
    }
}

data class SizeofNode(val type: String) : ValueNode()


data class CallNode(
    val targetName: String,
    val parameters: List<ValueNode>
) : ValueNode()


data class FunctionNode(
    val name: String,
    val arguments: List<PrimitiveMemberDeclaration>,
    val body: List<StatementNode>,
    val returnType: String,
) : AstNode(), NodeContainer {
    override fun getNodes(): Iterable<StatementNode> {
        return body
    }

}

enum class Operation {
    Addition,
    Subtraction,
    NotEquals,
}

data class SingleOperationNode(
    val operation: Operation,
    val left: ValueNode,
    val right: ValueNode,
) : ValueNode()

data class IfNode(
    val condition: ValueNode,
    val body: List<StatementNode>,
    val elseBody: List<StatementNode>,
) : StatementNode(), NodeContainer {
    override fun getNodes(): Iterable<StatementNode> {
        return body + elseBody
    }

    val hasElse
        get() = elseBody.isNotEmpty()

}

data class WhileNode(
    val condition: ValueNode,
    val body: List<StatementNode>,
) : StatementNode(), NodeContainer {
    override fun getNodes(): Iterable<StatementNode> {
        return body
    }
}

data class ReturnNode(
    val value: ValueNode? = null,
) : StatementNode()

data class StructNode(
    val name: String,
    val members: List<PrimitiveMemberDeclaration>,
) : AstNode()

data class MemberAccess(
    val left: ValueNode,
    val member: String
) : ValueNode()

data class MemberDeref(
    val left: ValueNode,
    val member: String,
) : ValueNode()