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
    val parameters: List<StructDataField>,
    val returnType: DataType,
    val sizeOfParameters: UByte,
    val sizeOfMeta: UByte,
    val sizeOfVars: UByte,
) : StructType(name, fields) {
    val sizeOfReturn = returnType.size
    val hasReturnSize = sizeOfReturn > 0u


}


fun calculateFrameLayout(
    node: FunctionNode,
    typeProvider: TypeProvider,
    memoryPosition: UByte,
): FunctionInfo {
    // We first calculate the offsets from the top. Then we reverse it when we know the total size
    val fieldBuilder = StructBuilder(typeProvider)

    // TODO: handle explicit new
    val returnType = typeProvider.getType(node.returnType).instantiate(false)
    if (returnType != voidType) {
        fieldBuilder.addMember("result", returnType)
    }

    val sizeOfRet = fieldBuilder.getCurrentSize()
    val parameterNames = mutableListOf<String>()

    for (arg in node.arguments) {
        if (arg.explicitNew) {
            throw CompileError("Explicit new not allowed for parameters")
        }

        val paramType = typeProvider.getType(arg.type).instantiate(false)
        fieldBuilder.addMember(arg.name, paramType)
        parameterNames.add(arg.name)

    }
    val sizeOfParams = fieldBuilder.getCurrentSize() - sizeOfRet

    // Make space for pc+fp
    fieldBuilder.addMember("frame", stackFrameType)
    val sizeOfMeta = fieldBuilder.getCurrentSize() - sizeOfRet - sizeOfParams

    // Traverse recursively. Can probably be flattened but meh
    fun traverseNode(nodeToVisit: NodeContainer) {
        for (visitNode in nodeToVisit.getNodes()) {
            var name: String
            var typeName: String
            var explicitNew: Boolean

            if (visitNode is AssignNode) {
                if (visitNode.target !is Identifier) {
                    // TODO what should happen here??
                    //throw CompileError("To complex for now")
                    continue
                }
                name = visitNode.target.name
                typeName = visitNode.type
                explicitNew = visitNode.explicitNew

                if (fieldBuilder.hasField(name))
                // TODO check type here perhaps??
                    continue

            } else if (visitNode is PrimitiveMemberDeclaration) {
                name = visitNode.name
                typeName = visitNode.type
                explicitNew = visitNode.explicitNew
            } else {
                if (visitNode is NodeContainer) {
                    traverseNode(visitNode)
                }
                continue
            }

            val type = typeProvider.getType(typeName).instantiate(explicitNew)
            fieldBuilder.addMember(name, type)
        }
    }
    traverseNode(node)
    val sizeOfVars = fieldBuilder.getCurrentSize() - sizeOfRet - sizeOfParams - sizeOfMeta

    //We've built the frame from top to buttom (param first, variables last). But on the stack the order is reversed
    //So we need to flip all offsets so it's relative to the bottom instead
    val fields = mutableMapOf<String, StructDataField>()
    fieldBuilder.getFields().forEach {
        val offset = fieldBuilder.getCurrentSize() - it.value.type.size.toInt() - it.value.offset.toInt()
        fields[it.key] = StructDataField(it.value.name, byte(offset), it.value.type)
    }

    val parameters = mutableListOf<StructDataField>()
    for (paramName in parameterNames) {
        parameters.add(fields.getValue(paramName))
    }


    return FunctionInfo(
        memoryPosition,
        node.name,
        fields,
        parameters,
        returnType,
        byte(sizeOfParams),
        byte(sizeOfMeta),
        byte(sizeOfVars),
    )

}