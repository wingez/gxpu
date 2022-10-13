package se.wingez.ast

import se.wingez.tokens.Token

enum class NodeTypes {
    Body,
    Identifier,
    String,
    NewVariable,
    Assign,
    Constant,
    Call,
    Function,
    If,
    Break,
    While,
    Return,
    Struct,
    MemberDeref,
    MemberAccess,
    ArrayAccess,
}


data class AstNode(
    val type: NodeTypes,
    val data: Any?,
    val childNodes: List<AstNode> = emptyList(),
) : Iterable<AstNode> {

    fun hasChildren(): Boolean {
        return childNodes.isNotEmpty()
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

    fun asIdentifier(): String {
        return data as String
    }

    fun asString(): String {
        return data as String
    }

    fun asConstant(): Int {
        return data as Int
    }

    class AssignNode(val node: AstNode) {
        val target = node.childNodes[0]
        val value = node.childNodes[1]
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

    class IfNode(
        val node: AstNode,
    ) {
        val condition
            get() = node.childNodes[0]

        val ifBody
            get() = node.childNodes[1].childNodes

        val elseBody
            get() = node.childNodes[2].childNodes

        val hasElse
            get() = node.childNodes[2].hasChildren()
    }

    fun asIf(): IfNode {
        return IfNode(this)
    }

    class WhileNode(
        val node: AstNode
    ) {
        val condition
            get() = node.childNodes[0]
        val body
            get() = node.childNodes[1].childNodes
    }

    fun asWhile(): WhileNode {
        return WhileNode(this)
    }

    class ReturnNode(val node: AstNode) {
        fun hasValue(): Boolean {
            return node.hasChildren()

        }

        val value: AstNode
            get() {
                if (!hasValue())
                // TODO: type
                    throw Exception("Return node has no value")
                return node.childNodes[0]
            }
    }

    fun asReturn(): ReturnNode {
        return ReturnNode(this)
    }

    class ArrayAccess(val node: AstNode) {
        val parent get() = node.childNodes[0]
        val index get() = node.childNodes[1]
    }

    fun asArrayAccess(): ArrayAccess {
        return ArrayAccess(this)
    }

    class NewVariable constructor(private val node: AstNode) {
        private val data
            get() = node.data as NewVariableData

        val name = data.name
        val optionalTypeDefinition = data.optionalTypeDefinition

        val hasTypeFromAssignment = node.hasChildren()
        val assignmentType
            get() = node.childNodes.first()

    }

    fun asNewVariable(): NewVariable {
        assert(type == NodeTypes.NewVariable)
        return NewVariable(this)
    }

    companion object {


        fun fromOperation(type: Token, left: AstNode, right: AstNode): AstNode {

            val name = operatorToNodesType.getValue(type)
            return fromCall(name, listOf(left, right))
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

        fun fromNewVariable(
            memberName: String,
            optionalTypeDefinition: TypeDefinition?,
            optionalTypeHint: AstNode?
        ): AstNode {
            val childNodes = if (optionalTypeHint == null) emptyList() else listOf(optionalTypeHint)

            return AstNode(
                NodeTypes.NewVariable, NewVariableData(memberName, optionalTypeDefinition), childNodes
            )
        }

        fun fromAssign(
            target: AstNode,
            value: AstNode,
        ): AstNode {
            return AstNode(NodeTypes.Assign, null, listOf(target, value))
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
            return fromFunction(name, arguments, body, TypeDefinition(returnType))
        }

        fun fromFunction(
            name: String,
            arguments: List<AstNode>,
            body: List<AstNode>,
            returnType: TypeDefinition
        ): AstNode {
            return AstNode(
                NodeTypes.Function,
                FunctionData(name, arguments, returnType),
                body
            )
        }

        fun fromIf(
            condition: AstNode,
            body: List<AstNode>,
            elseBody: List<AstNode>,
        ): AstNode {
            return AstNode(NodeTypes.If, null, listOf(condition, fromBody(body), fromBody(elseBody)))
        }

        fun fromWhile(
            condition: AstNode,
            body: List<AstNode>,
        ): AstNode {
            return AstNode(NodeTypes.While, null, listOf(condition, fromBody(body)))
        }

        fun fromReturn(
            value: AstNode? = null,
        ): AstNode {
            return AstNode(NodeTypes.Return, null, if (value != null) listOf(value) else emptyList())
        }

        fun fromStruct(name: String, arguments: List<AstNode>): AstNode {
            return AstNode(NodeTypes.Struct, name, arguments)
        }

        fun fromArrayAccess(
            parent: AstNode,
            index: AstNode
        ): AstNode {
            return AstNode(NodeTypes.ArrayAccess, null, listOf(parent, index))
        }

        fun fromString(string: String): AstNode {
            return AstNode(NodeTypes.String, string)
        }

        fun fromBreak(): AstNode {
            return AstNode(NodeTypes.Break, null, emptyList())
        }
    }
}

private data class NewVariableData(
    val name: String,
    val optionalTypeDefinition: TypeDefinition?,
)

data class FunctionData(
    val name: String,
    val arguments: List<AstNode>,
    val returnType: TypeDefinition,
)

data class TypeDefinition(
    val typeName: String,
    val explicitNew: Boolean = false,
    val isArray: Boolean = false,
)
