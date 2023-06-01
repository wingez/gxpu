package se.wingez.compiler

import compiler.backends.emulator.DataType
import compiler.backends.emulator.StructDataField
import compiler.backends.emulator.TypeProvider
import compiler.backends.emulator.voidType
import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

const val POINTER_SIZE = 1

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1
const val STACK_START = 255

enum class FunctionAnnotation {
    NoFrame,
}

data class FunctionSignature(
    val name: String,
    val returnType: DataType,
    val parameters: List<StructDataField>,
    val annotations: List<FunctionAnnotation>

) {
    fun matchesHeader(name: String, parameters: List<DataType>): Boolean {
        return name == this.name && parameters == this.parameters.map { it.type }
    }

    companion object {
        fun fromNode(node: AstNode, typeProvider: TypeProvider): FunctionSignature {
            assert(node.type == NodeTypes.Function)

            val functionData = node.asFunction()

            val name = functionData.name
            val parameters = mutableListOf<StructDataField>()


            val returnType = if (functionData.returnType.typeName.isNotEmpty())
                typeProvider.getType(functionData.returnType)
            else
                voidType

            var currentOffset = 0
            for (paramNode in functionData.arguments) {
                assert(paramNode.type == NodeTypes.NewVariable)
                val member = paramNode.asNewVariable()

                val type = typeProvider.getType(member.optionalTypeDefinition!!)

                parameters.add(StructDataField(member.name, currentOffset, type))
                currentOffset += type.size
            }
            return FunctionSignature(name, returnType, parameters, emptyList())
        }
    }

}


class SignatureBuilder(val name: String) {
    private val parameters = mutableListOf<StructDataField>()
    private var returnType: DataType = voidType
    private val annotations = mutableListOf<FunctionAnnotation>()

    fun addParameter(name: String, type: DataType): SignatureBuilder {
        parameters.add(StructDataField(name, parameters.sumOf { it.offset }, type))
        return this
    }

    fun setReturnType(type: DataType): SignatureBuilder {
        returnType = type
        return this
    }

    fun addAnnotation(annotation: FunctionAnnotation): SignatureBuilder {
        annotations.add(annotation)
        return this
    }

    fun getSignature(): FunctionSignature {
        return FunctionSignature(name, returnType, parameters, annotations)
    }
}
