package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.byte

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1
const val STACK_START = 255


class FunctionInfo(
    size: UByte,
    val memoryPosition: UByte,
    name: String,
    fields: Map<String, StructDataField>,
    val parameters: List<StructDataField>,
    val returnType: DataType,
    val sizeOfParameters: UByte,
    val sizeOfMeta: UByte,
    val sizeOfVars: UByte,
) : StructType(size, name, fields) {
    val sizeOfReturn = returnType.size
    val hasReturnSize = sizeOfReturn > 0u


}


fun calculateFrameLayout(
    node: FunctionNode,
    typeProvider: TypeProvider,
    memoryPosition: UByte,
): FunctionInfo {
    // We first calculate the offsets from the top. Then we reverse it when we know the total size
    val fieldsOffsetFromTop = mutableMapOf<String, StructDataField>()

    val returnType = typeProvider.getType(node.returnType)
    if (returnType != voidType) {
        fieldsOffsetFromTop["result"] = StructDataField("result", 0u, returnType)
    }

    val sizeOfRet = returnType.size
    var currentSize = sizeOfRet.toInt()
    var sizeOfParams = 0

    val parameterNames = mutableListOf<String>()
    for (arg in node.arguments) {
        if (arg.name in parameterNames) throw CompileError("Duplicate parameter")

        parameterNames.add(arg.name)
        val paramType = typeProvider.getType(arg.type)
        fieldsOffsetFromTop[arg.name] = StructDataField(arg.name, byte(currentSize), paramType)
        currentSize += paramType.size.toInt()
        sizeOfParams += paramType.size.toInt()
    }

    val sizeOfMeta = stackFrameType.size
    currentSize += sizeOfMeta.toInt()

    var sizeOfVars = 0


    // Traverse recursively. Can probably be flattened but meh
    fun traverseNode(nodeToVisit: NodeContainer) {
        for (visitNode in nodeToVisit.getNodes()) {
            var name: String
            var typeName: String

            if (visitNode is AssignNode) {
                if (visitNode.target !is Identifier) {
                    // TODO what should happen here??
                    //throw CompileError("To complex for now")
                    continue
                }
                name = visitNode.target.name
                typeName = visitNode.type
            } else if (visitNode is PrimitiveMemberDeclaration) {
                name = visitNode.name
                typeName = visitNode.type
            } else {
                if (visitNode is NodeContainer) {
                    traverseNode(visitNode)
                }
                continue
            }

            val type = typeProvider.getType(typeName)
            if (name in fieldsOffsetFromTop) {
                // TODO: Do something here??
            } else {
                fieldsOffsetFromTop[name] = StructDataField(name, byte(currentSize), type)
                currentSize += type.size.toInt()
                sizeOfVars += type.size.toInt()
            }
        }
    }


    traverseNode(node)

    //Reverse the offsets
    val fields = mutableMapOf<String, StructDataField>()
    fieldsOffsetFromTop.forEach {
        val offset = currentSize - it.value.type.size.toInt() - it.value.offset.toInt()
        fields[it.key] = StructDataField(it.value.name, byte(offset), it.value.type)
    }

    val parameters = mutableListOf<StructDataField>()
    for (paramName in parameterNames) {
        parameters.add(fields.getValue(paramName))
    }


    return FunctionInfo(
        byte(currentSize),
        memoryPosition,
        node.name,
        fields,
        parameters,
        returnType,
        byte(sizeOfParams),
        sizeOfMeta,
        byte(sizeOfVars),
    )

}