package ast

import SourceInfo
import ast.expression.binaryOperatorToNodesType
import tokens.Token
import tokens.TokenType

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
    MemberAccess,
    ArrayAccess,
    Array,
    AddressOf,
    Deref,
    Lambda,
    FunctionReference,
}

enum class FunctionType {
    Normal,
    Operator,
    Instance,
}

data class AstNode(
    val type: NodeTypes,
    val data: Any?,
    val childNodes: List<AstNode> = emptyList(),
    val sourceInfo: SourceInfo,
) : Iterable<AstNode> {

    val child
        get() = when (childNodes.size) {
            1 -> childNodes.first()
            else -> throw AssertionError(this.toString())
        }

    fun hasChildren(): Boolean {
        return childNodes.isNotEmpty()
    }

    override fun iterator(): Iterator<AstNode> {
        return childNodes.iterator()
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

    private data class CallInfo(val targetName: String, val functionType: FunctionType)

    class CallNode(
        val node: AstNode
    ) {
        val targetName = (node.data as CallInfo).targetName
        val functionType = (node.data as CallInfo).functionType

        val parameters get() = node.childNodes
    }

    fun asCall(): CallNode {
        return CallNode(this)
    }

    class FunctionNode(private val node: AstNode) {

        private val extraData = node.data as FunctionData

        val name = extraData.name
        val type = extraData.type
        val returnType = extraData.returnType

        val arguments = node.childNodes[0]
        val body = node.childNodes[1]

    }

    fun asFunction(): FunctionNode {
        return FunctionNode(this)
    }

    class IfNode(
        val node: AstNode,
    ) {
        val condition
            get() = node.childNodes[0]

        val ifBody
            get() = node.childNodes[1]

        val elseBody
            get() = node.childNodes[2]

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
            get() = node.childNodes[1]
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

    fun iterateBody(): Iterable<AstNode> {
        require(type == NodeTypes.Body)
        return childNodes
    }

    companion object {


        fun fromBinaryOperation(type: TokenType, left: AstNode, right: AstNode, sourceInfo: SourceInfo): AstNode {

            val name = binaryOperatorToNodesType.getValue(type)
            return fromCall(name, FunctionType.Operator, listOf(left, right), sourceInfo)
        }

        fun fromBody(body: List<AstNode>): AstNode {
            return AstNode(NodeTypes.Body, null, body, SourceInfo.notApplicable)
        }

        fun fromIdentifier(name: String, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Identifier, name, sourceInfo = sourceInfo)
        }

        fun fromIdentifier(identifierToken: Token): AstNode {
            require(identifierToken.type == TokenType.Identifier)
            return fromIdentifier(identifierToken.additionalData, identifierToken.sourceInfo)
        }

        fun fromConstant(value: Int, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Constant, value, emptyList(), sourceInfo)
        }

        fun fromNewVariable(
            memberName: String,
            optionalTypeDefinition: TypeDefinition?,
            optionalTypeHint: AstNode?,
            sourceInfo: SourceInfo,
        ): AstNode {
            val childNodes = if (optionalTypeHint == null) emptyList() else listOf(optionalTypeHint)

            return AstNode(
                NodeTypes.NewVariable, NewVariableData(memberName, optionalTypeDefinition), childNodes, sourceInfo
            )
        }

        fun fromAssign(
            target: AstNode,
            value: AstNode,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(NodeTypes.Assign, null, listOf(target, value), sourceInfo)
        }

        fun fromCall(
            targetName: String,
            functionType: FunctionType,
            parameters: List<AstNode>,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(NodeTypes.Call, CallInfo(targetName, functionType), parameters, sourceInfo)
        }


        fun fromFunction(
            name: String,
            type: FunctionType,
            arguments: List<AstNode>,
            body: List<AstNode>,
            returnType: TypeDefinition?,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(
                NodeTypes.Function,
                FunctionData(name, type, returnType),
                listOf(
                    fromBody(arguments),
                    fromBody(body)
                ),
                sourceInfo,
            )
        }

        fun fromIf(
            condition: AstNode,
            body: List<AstNode>,
            elseBody: List<AstNode>,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(NodeTypes.If, null, listOf(condition, fromBody(body), fromBody(elseBody)), sourceInfo)
        }

        fun fromWhile(
            condition: AstNode,
            body: List<AstNode>,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(NodeTypes.While, null, listOf(condition, fromBody(body)), sourceInfo)
        }

        fun fromReturn(
            value: AstNode? = null,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(NodeTypes.Return, null, if (value != null) listOf(value) else emptyList(), sourceInfo)
        }

        fun fromStruct(name: String, arguments: List<AstNode>, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Struct, name, arguments, sourceInfo)
        }

        fun fromArrayAccess(
            parent: AstNode,
            index: AstNode,
            sourceInfo: SourceInfo,
        ): AstNode {
            return AstNode(NodeTypes.ArrayAccess, null, listOf(parent, index), sourceInfo)
        }

        fun fromString(string: String, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.String, string, sourceInfo = sourceInfo)
        }

        fun fromBreak(sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Break, null, emptyList(), sourceInfo)
        }

        fun newArray(content: List<AstNode>, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Array, null, content, sourceInfo)
        }

        fun fromAddressOf(node: AstNode, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.AddressOf, null, listOf(node), sourceInfo)
        }

        fun fromDeref(node: AstNode, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Deref, null, listOf(node), sourceInfo)
        }

        fun fromMemberAccess(node: AstNode, member: String, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.MemberAccess, member, listOf(node), sourceInfo)
        }

        fun fromLambda(body: List<AstNode>, sourceInfo: SourceInfo): AstNode {
            return AstNode(NodeTypes.Lambda, null, listOf(AstNode.fromBody(body)), sourceInfo)
        }
    }
}

private data class NewVariableData(
    val name: String,
    val optionalTypeDefinition: TypeDefinition?,
)

data class FunctionData(
    val name: String,
    val type: FunctionType,
    val returnType: TypeDefinition?,
)
