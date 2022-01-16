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

    fun asMemberDeclaration(): MemberDeclarationData {
        return data as MemberDeclarationData
    }

    class AssignNode(val node: AstNode) {
        val target = node.childNodes[0]
        val value = node.childNodes[1]
        val assignData = node.data as AssignData
    }

    fun asAssign(): AssignNode {
        return AssignNode(this)
    }

    class CallNode(
        val node: AstNode
    ) {
        val targetName = node.data as String
        val parameters get() = node.childNodes
    }

    fun asCall(): CallNode {
        return CallNode(this)
    }

    fun asFunction(): FunctionData {
        return this.data as FunctionData
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

        fun fromMemberDeclaration(memberData: MemberDeclarationData): AstNode {
            return AstNode(
                NodeTypes.MemberDeclaration, memberData
            )
        }

        fun fromAssign(
            target: AstNode,
            value: AstNode,
            type: String = "",
            explicitNew: Boolean = false,
        ): AstNode {
            return AstNode(NodeTypes.Assign, AssignData(type, explicitNew), listOf(target, value))
        }

        fun fromPrint(target: AstNode): AstNode {
            return AstNode(NodeTypes.Print, null, listOf(target))
        }

        fun fromCall(targetName: String, parameters: List<AstNode>): AstNode {
            return AstNode(NodeTypes.Call, targetName, parameters)
        }

        fun fromFunction(
            name: String,
            arguments: List<AstNode>,
            body: List<AstNode>,
            returnType: String,
        ): AstNode {
            return AstNode(NodeTypes.Function, FunctionData(name, arguments, returnType), body)
        }
    }
}

data class MemberDeclarationData(
    val name: String,
    val type: String,
    val explicitNew: Boolean = false,
    val isArray: Boolean = false,
)

data class AssignData(
    val type: String,
    val explicitNew: Boolean,
)

data class FunctionData(
    val name: String,
    val arguments: List<AstNode>,
    val returnType: String,
)

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
    members: List<AstNode>,
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