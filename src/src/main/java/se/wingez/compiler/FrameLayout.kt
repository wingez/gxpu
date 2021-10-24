package se.wingez.compiler

import se.wingez.ast.AssignNode
import se.wingez.ast.AssignTarget
import se.wingez.ast.FunctionNode
import se.wingez.ast.NodeContainer
import se.wingez.byte

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1
const val STACK_START = 255


class FrameLayout(
    size: UByte,
    name: String,
    fields: Map<String, StructDataField>,
    val sizeOfParameters: UByte,
    val sizeOfMeta: UByte,
    val sizeOfVars: UByte,
    val sizeOfReturn: UByte,
) : StructType(size, name, fields) {
    val hasReturnSize
        get() = sizeOfReturn > 0u


}


fun calculateFrameLayout(
    node: FunctionNode,
    typeProvider: TypeProvider,
): FrameLayout {
    // We first calculate the offsets from the top. Then we reverse it when we know the total size
    val fieldsOffsetFromTop = mutableMapOf<String, StructDataField>()

    val returnType = typeProvider.getType(node.returnType)
    if (returnType != voidType) {
        fieldsOffsetFromTop["result"] = StructDataField("result", 0u, returnType)
    }

    val sizeOfRet = returnType.size
    var currentSize = sizeOfRet.toInt()
    var sizeOfParams = 0

    for (arg in node.arguments) {
        val paramType = typeProvider.getType(arg.type)
        fieldsOffsetFromTop[arg.member.name] = StructDataField(arg.member.name, byte(currentSize), paramType)
        currentSize += paramType.size.toInt()
        sizeOfParams += paramType.size.toInt()
    }

    val sizeOfMeta = stackFrameType.size
    currentSize += sizeOfMeta.toInt()

    var sizeOfVars = 0


    // Traverse recursively. Can probably be flattened but meh
    fun traverseNode(nodeToVisit: NodeContainer) {
        for (visitNode in nodeToVisit.getNodes()) {
            var assignTarget: AssignTarget? = null

            if (visitNode is AssignNode) {
                assignTarget = visitNode.target
            } else if (visitNode is AssignTarget) {
                assignTarget = visitNode
            } else if (visitNode is NodeContainer) {
                traverseNode(visitNode)
            }

            if (assignTarget != null) {
                val name = assignTarget.member.name
                val type = typeProvider.getType(assignTarget.type)
                if (name in fieldsOffsetFromTop) {
                    // TODO: Do something here??
                } else {
                    fieldsOffsetFromTop[name] = StructDataField(name, byte(currentSize), type)
                    currentSize += type.size.toInt()
                    sizeOfVars += type.size.toInt()
                }
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


    return FrameLayout(
        byte(currentSize),
        node.name,
        fields,
        byte(sizeOfParams),
        sizeOfMeta,
        byte(sizeOfVars),
        sizeOfRet,
    )

}