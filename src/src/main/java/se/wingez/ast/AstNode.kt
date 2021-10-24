package se.wingez.ast

import se.wingez.TokenNumericConstant

abstract class AstNode
abstract class StatementNode : AstNode()
abstract class ValueProviderNode : StatementNode()


data class MemberAccessModifier(val member: String)
data class MemberAccess(val name: String, val actions: List<MemberAccessModifier> = emptyList()) : ValueProviderNode()

data class AssignTarget(
        val member: MemberAccess,
        val type: String = "",
        val explicitNew: Boolean = false
) : StatementNode()

data class AssignNode(
        val target: AssignTarget,
        val value: ValueProviderNode?
) : StatementNode()

data class PrintNode(val target: ValueProviderNode) : StatementNode()
data class ConstantNode(val value: Int) : ValueProviderNode() {
    companion object {
        fun fromToken(token: TokenNumericConstant): ConstantNode {
            return ConstantNode(value = token.value)
        }
    }
}

data class CallNode(
        val targetName: String,
        val parameters: List<ValueProviderNode>
) : ValueProviderNode()


data class FunctionNode(
        val name: String,
        val arguments: List<AssignTarget>,
        val body: List<StatementNode>,
        val returnType: String,
) : AstNode(), NodeContainer {
    override fun getNodes(): Iterable<StatementNode> {
        return body
    }

}

enum class Operation {
    Plus,
    Minus
}

data class SingleOperationNode(
        val operation: Operation,
        val left: ValueProviderNode,
        val right: ValueProviderNode,
) : ValueProviderNode()

data class IfNode(
        val condition: ValueProviderNode,
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
        val condition: ValueProviderNode,
        val body: List<StatementNode>,
) : StatementNode(), NodeContainer {
    override fun getNodes(): Iterable<StatementNode> {
        return body
    }
}

data class ReturnNode(
        val value: ValueProviderNode? = null,
) : StatementNode()

data class StructNode(
        val name: String,
        val members: List<AssignTarget>,
) : AstNode()