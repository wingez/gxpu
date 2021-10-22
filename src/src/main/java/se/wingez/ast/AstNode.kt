package se.wingez.ast

import se.wingez.TokenNumericConstant
import javax.swing.plaf.nimbus.State

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
) : AstNode()

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
) : StatementNode()

data class WhileNode(
        val condition: ValueProviderNode,
        val body: List<StatementNode>,
) : StatementNode()

data class ReturnNode(
        val value: ValueProviderNode? = null,
) : StatementNode()