package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.byte

const val POINTER_SIZE = 1

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1
const val STACK_START = 255


class FunctionInfo(
    val memoryPosition: UByte,
    name: String,
    fields: Map<String, StructDataField>,
    parameterNames: List<String>,
    val returnType: DataType,

    ) : StructType(name, fields) {
    val sizeOfReturn = returnType.size

    val sizeOfParameters: UByte
    val sizeOfMeta: UByte
    val sizeOfVars: UByte

    val parameters: List<StructDataField>

    init {

        parameters = parameterNames.map { getField(it) }

        // TODO check overflow
        sizeOfParameters = byte(parameters.sumOf { it.type.size.toInt() })
        sizeOfMeta = stackFrameType.size

        // Fields include parameters and returnPC
        sizeOfVars =
            byte(fields.values.sumOf { it.type.size.toInt() } - sizeOfParameters.toInt() - sizeOfMeta.toInt() - sizeOfReturn.toInt())
    }

}


fun calculateFrameLayout(
    node: FunctionNode,
    typeProvider: TypeProvider,
    memoryPosition: UByte,
): FunctionInfo {
    // We first calculate the offsets from the top. Then we reverse it when we know the total size
    val fieldBuilder = StructBuilder(typeProvider)

    val returnType = if (node.returnType.isEmpty())
        voidType
    else
    // TODO: handle explicit new
        typeProvider.getType(node.returnType).instantiate(false)

    fieldBuilder.addMember("result", returnType)

    // Figure out what parameters to use. But we should not add them to the layout until we've added the local variables
    val parameters = mutableListOf<Pair<String, DataType>>()
    for (arg in node.arguments) {
        if (arg.explicitNew) {
            throw CompileError("Explicit new not allowed for parameters")
        }

        var paramType = typeProvider.getType(arg.type)
        if (arg.isArray) {
            paramType = ArrayType(paramType)
        }
        paramType = paramType.instantiate(false)
        parameters.add(Pair(arg.name, paramType))
    }




    // Traverse recursively. Can probably be flattened but meh
    fun traverseNode(nodeToVisit: NodeContainer) {
        for (visitNode in nodeToVisit.getNodes()) {
            var name: String
            var typeName: String
            var explicitNew: Boolean
            var isArray = false

            if (visitNode is AssignNode) {
                if (visitNode.target !is Identifier) {
                    // TODO what should happen here??
                    //throw CompileError("To complex for now")
                    continue
                }
                name = visitNode.target.name
                typeName = visitNode.type
                explicitNew = visitNode.explicitNew


            } else if (visitNode is PrimitiveMemberDeclaration) {
                name = visitNode.name
                typeName = visitNode.type
                explicitNew = visitNode.explicitNew
                isArray = visitNode.isArray
            } else {
                if (visitNode is NodeContainer) {
                    traverseNode(visitNode)
                }
                continue
            }

            if (fieldBuilder.hasField(name) || parameters.any { it.first == name })
            // TODO check type here perhaps??
                continue

            var type = typeProvider.getType(typeName)
            if (isArray) {
                type = ArrayType(type)
            }

            type = type.instantiate(explicitNew)
            fieldBuilder.addMember(name, type)
        }
    }
    traverseNode(node)

    //Place parameters in layout
    parameters.forEach { fieldBuilder.addMember(it.first, it.second) }


    // Make space for pc+fp
    fieldBuilder.addMember("frame", stackFrameType)

    //We've built the frame from top to buttom (param first, variables last). But on the stack the order is reversed
    //So we need to flip all offsets so it's relative to the bottom instead
    val fields = mutableMapOf<String, StructDataField>()
    fieldBuilder.getFields().forEach {
        val offset = fieldBuilder.getCurrentSize() - it.value.type.size.toInt() - it.value.offset.toInt()
        fields[it.key] = StructDataField(it.value.name, byte(offset), it.value.type)
    }

    val parameterNames = parameters.map { it.first }

    return FunctionInfo(
        memoryPosition,
        node.name,
        fields,
        parameterNames, returnType
    )

}