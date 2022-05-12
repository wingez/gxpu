package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

const val POINTER_SIZE = 1

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1
const val STACK_START = 255


class FunctionSignature(
    name: String,
    fields: Map<String, StructDataField>,
    parameterNames: List<String>,
    val returnType: DataType,

    ) : StructType(name, fields) {
    val sizeOfReturn = returnType.size

    val sizeOfParameters: Int
    val sizeOfMeta: Int
    val sizeOfVars: Int

    val parameters: List<StructDataField>

    val parameterSignature: List<DataType>
        get() = parameters.map { it.type }

    init {

        parameters = parameterNames.map { getField(it) }

        // TODO check overflow
        sizeOfParameters = parameters.sumOf { it.type.size }
        sizeOfMeta = stackFrameType.size

        // Fields include parameters and returnPC
        sizeOfVars =
            fields.values.sumOf { it.type.size } - sizeOfParameters - sizeOfMeta - sizeOfReturn
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as FunctionSignature

        if (returnType != other.returnType) return false
        if (parameterSignature != other.parameterSignature) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + parameterSignature.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}


fun calculateSignature(
    node: AstNode,
    typeProvider: TypeProvider,
): FunctionSignature {
    // We first calculate the offsets from the top. Then we reverse it when we know the total size
    val fieldBuilder = StructBuilder()
    val functionData = node.asFunction()

    val returnType = if (functionData.returnType.isEmpty())
        voidType
    else
    // TODO: handle explicit new
        typeProvider.getType(functionData.returnType)

    fieldBuilder.addMember("result", returnType)

    // Figure out what parameters to use. But we should not add them to the layout until we've added the local variables
    val parameters = mutableListOf<Pair<String, DataType>>()
    for (arg in functionData.arguments) {
        val memberData = arg.asMemberDeclaration()
        if (memberData.explicitNew) {
            throw CompileError("Explicit new not allowed for parameters")
        }

        var paramType = typeProvider.getType(memberData.type)
        // TODO
//        if (memberData.isArray) {
//            paramType = ArrayType(paramType)
//        }
//        paramType = paramType.instantiate(false)
        parameters.add(Pair(memberData.name, paramType))
    }




    // Traverse recursively. Can probably be flattened but meh
    fun traverseNode(nodeToVisit: AstNode) {
        for (visitNode in nodeToVisit) {
            if (visitNode.type != NodeTypes.MemberDeclaration) {
                if (visitNode.hasChildren()) {
                    traverseNode(visitNode)
                }
                continue
            }

            val memberData = visitNode.asMemberDeclaration()
            val name = memberData.name
            val typeName = memberData.type


            if (fieldBuilder.hasField(name) || parameters.any { it.first == name })
            // TODO check type here perhaps??
                continue

            var type = typeProvider.getType(typeName)
            // TODO
//            if (memberData.isArray) {
//                type = ArrayType(type)
//            }
//
//            type = type.instantiate(memberData.explicitNew)
            fieldBuilder.addMember(name, type)
        }
    }
    traverseNode(node)

    //Place parameters in layout
    parameters.forEach { fieldBuilder.addMember(it.first, it.second) }


    // Make space for pc+fp
    fieldBuilder.addMember("frame", stackFrameType)

    //We've built the frame from top to bottom (param first, variables last). But on the stack the order is reversed
    //So we need to flip all offsets, so it's relative to the bottom instead
    val fields = mutableMapOf<String, StructDataField>()
    fieldBuilder.getFields().forEach {
        val offset = fieldBuilder.getCurrentSize() - it.value.type.size - it.value.offset
        fields[it.key] = StructDataField(it.value.name, offset, it.value.type)
    }

    val parameterNames = parameters.map { it.first }

    return FunctionSignature(
        functionData.name,
        fields,
        parameterNames,
        returnType,
    )
}

private data class SignatureEntry(
    val name: String,
    val type: DataType,
)

class SignatureBuilder(val name: String) {
    private val parameters = mutableListOf<SignatureEntry>()
    private val fields = mutableListOf<SignatureEntry>()
    private var returnType: DataType = voidType

    fun addParameter(name: String, type: DataType): SignatureBuilder {
        parameters.add(SignatureEntry(name, type))
        return this
    }

    fun addField(name: String, type: DataType): SignatureBuilder {
        parameters.add(SignatureEntry(name, type))
        return this
    }

    fun setReturnType(type: DataType): SignatureBuilder {
        returnType = type
        return this
    }

    fun getSignature(): FunctionSignature {

        val builder = StructBuilder()
        builder.addMember("frame", stackFrameType)
        for ((name, type) in fields) {
            builder.addMember(name, type)
        }
        for ((name, type) in parameters) {
            builder.addMember(name, type)
        }
        builder.addMember("result", returnType)

        return FunctionSignature(
            name,
            builder.getFields(), parameters.map { it.name }, returnType
        )
    }
}