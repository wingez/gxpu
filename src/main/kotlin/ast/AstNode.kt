package se.wingez.ast

enum class NodeTypes {
    Body,
    Identifier,

    Addition,
    Subtraction,
    NotEquals,
    MemberDeclaration,
    Assign,
    Print,
    Constant,
    Call,
    Function,
    If,
    While,
    Return,
    Struct,
    MemberDeref,
    MemberAccess,
    ArrayAccess,
}


open class AstNode(
    val type: NodeTypes,
    val data: Any?,
    val childNodes: List<AstNode> = emptyList(),
) : Iterable<AstNode> {

    fun hasChildren(): Boolean {
        return childNodes.isNotEmpty()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AstNode)
            return false

        return type == other.type && data == other.data && childNodes == other.childNodes
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + childNodes.hashCode()
        return result
    }

    override fun iterator(): Iterator<AstNode> {
        return childNodes.iterator()
    }

    class OperationNode(val node: AstNode) {
        val left get() = node.childNodes[0]
        val right get() = node.childNodes[1]
    }

    fun asOperation(): OperationNode {
        return OperationNode(this)
    }

    class IdentifierNode(val node: AstNode) {
        val name: String
            get() {
                return node.data as String
            }
    }

    fun asIdentifier(): IdentifierNode {
        return IdentifierNode(this)
    }

    class ConstantNode(val node: AstNode) {
        val value
            get() = node.data as Int
    }

    fun asConstant(): ConstantNode {
        return ConstantNode(this)
    }

    companion object {


        fun fromOperation(type: NodeTypes, left: AstNode, right: AstNode): AstNode {
            return AstNode(type, null, listOf(left, right))
        }

        fun fromBody(body: List<AstNode>): AstNode {
            return AstNode(NodeTypes.Body, null, body)
        }

        fun fromIdentifier(name: String): AstNode {
            return AstNode(NodeTypes.Identifier, name)
        }

        fun fromConstant(value: Int): AstNode {
            return AstNode(NodeTypes.Constant, value, emptyList())
        }

    }
}


class PrimitiveMemberDeclaration(name: String, type: String, explicitNew: Boolean = false, isArray: Boolean = false) :
    AstNode(
        NodeTypes.MemberDeclaration, MemberData(name, type, explicitNew, isArray)
    ) {

    data class MemberData(
        val name: String,
        val type: String,
        val explicitNew: Boolean = false,
        val isArray: Boolean = false,
    )

    val memberData get() = data as MemberData
}


class AssignNode(
    target: AstNode,
    value: AstNode,
    type: String = "",
    explicitNew: Boolean = false,

    ) : AstNode(NodeTypes.Assign, AssignData(type, explicitNew), listOf(target, value)) {
    data class AssignData(
        val type: String,
        val explicitNew: Boolean,
    )

    val target = childNodes[0]
    val value = childNodes[1]

    val assignData = data as AssignData
}

class PrintNode(target: AstNode) : AstNode(NodeTypes.Print, null, listOf(target)) {
    val target get() = childNodes[0]
}


class CallNode(
    val targetName: String,
    parameters: List<AstNode>
) : AstNode(NodeTypes.Call, targetName, parameters) {
    val parameters get() = childNodes
}


class FunctionNode(
    name: String,
    arguments: List<PrimitiveMemberDeclaration>,
    body: List<AstNode>,
    returnType: String,
) : AstNode(NodeTypes.Function, FunctionData(name, arguments, returnType), body) {

    data class FunctionData(
        val name: String,
        val arguments: List<PrimitiveMemberDeclaration>,
        val returnType: String,
    )

    val functionData = data as FunctionData
}

class IfNode(
    condition: AstNode,
    body: List<AstNode>,
    elseBody: List<AstNode>,
) : AstNode(NodeTypes.If, null, listOf(condition, AstNode.fromBody(body), AstNode.fromBody(elseBody))) {


    val condition
        get() = childNodes[0]

    val ifBody
        get() = childNodes[1].childNodes

    val elseBody
        get() = childNodes[2].childNodes

    val hasElse
        get() = childNodes[2].hasChildren()

}

class WhileNode(
    condition: AstNode,
    body: List<AstNode>,
) : AstNode(
    NodeTypes.While, null, listOf(condition, AstNode.fromBody(body))
) {
    val condition
        get() = childNodes[0]
    val body
        get() = childNodes[1].childNodes
}

class ReturnNode(
    value: AstNode? = null,
) : AstNode(NodeTypes.Return, null, if (value != null) listOf(value) else emptyList()) {

    fun hasValue(): Boolean {
        return hasChildren()
    }

    val value: AstNode
        get() {
            if (!hasValue())
            // TODO: type
                throw Exception("Return node has no value")
            return childNodes[0]
        }

}

class StructNode(
    val name: String,
    members: List<PrimitiveMemberDeclaration>,
) : AstNode(NodeTypes.Struct, name, members)

class MemberAccess(
    parent: AstNode,
    val member: String
) : AstNode(NodeTypes.MemberAccess, member, listOf(parent)) {
    val parent get() = childNodes[0]
}

class MemberDeref(
    parent: AstNode,
    val member: String,
) : AstNode(NodeTypes.MemberDeref, member, listOf(parent)) {
    val parent get() = childNodes[0]
}

class ArrayAccess(
    parent: AstNode,
    index: AstNode,
) : AstNode(NodeTypes.ArrayAccess, null, listOf(parent, index)) {
    val parent get() = childNodes[0]
    val index get() = childNodes[1]
}