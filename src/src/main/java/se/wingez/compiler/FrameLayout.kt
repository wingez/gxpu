package se.wingez.compiler

import se.wingez.ast.AssignNode
import se.wingez.ast.AssignTarget
import se.wingez.ast.FunctionNode
import se.wingez.ast.NodeContainer

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1

interface DataType {
    val size: Int
    val name: String
}


interface TypeProvider {
    fun getType(name: String): DataType
}

data class PrimitiveDatatype(
        override val size: Int,
        override val name: String
) : DataType

val byteType = PrimitiveDatatype(1, "byte")
val voidType = PrimitiveDatatype(0, "void")
val stackFrameType = PrimitiveDatatype(SP_STACK_SIZE + PC_STACK_SIZE, "stackFrame")

data class StructDataField(
        val name: String,
        val offset: Int,
        val type: DataType,
)

open class StructType(
        override val size: Int,
        override val name: String,
        val fields: Map<String, StructDataField>,
) : DataType {

    fun getDescription(): List<String> {
        val result = mutableListOf<String>()
        val items = fields.entries.toList()

        items.sortedBy { it.value.offset }.forEach { field ->
            val offset = field.value.offset.toString().padStart(3)
            result.add("$offset: ${field.key}: ${field.value.type.name}")
        }

        return result
    }
}


class FrameLayout(
        size: Int,
        name: String,
        fields: Map<String, StructDataField>,
        val sizeOfParameters: Int,
        val sizeOfMeta: Int,
        val sizeOfVars: Int,
        val sizeOfReturn: Int,
) : StructType(size, name, fields) {

}


fun calculateFrameLayout(
        node: FunctionNode,
        typeProvider: TypeProvider,
): FrameLayout {
    // We first calculate the offsets from the top. Then we reverse it when we know the total size
    val fieldsOffsetFromTop = mutableMapOf<String, StructDataField>()

    val returnType = typeProvider.getType(node.returnType)
    if (returnType != voidType) {
        fieldsOffsetFromTop["result"] = StructDataField("result", 0, returnType)
    }

    val sizeOfRet = returnType.size
    var currentSize = sizeOfRet
    var sizeOfParams = 0

    for (arg in node.arguments) {
        val paramType = typeProvider.getType(arg.type)
        fieldsOffsetFromTop[arg.member.name] = StructDataField(arg.member.name, currentSize, paramType)
        currentSize += paramType.size
        sizeOfParams += paramType.size
    }

    val sizeOfMeta = stackFrameType.size
    currentSize += sizeOfMeta

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
                    fieldsOffsetFromTop[name] = StructDataField(name, currentSize, type)
                    currentSize += type.size
                    sizeOfVars += type.size
                }
            }
        }
    }

    traverseNode(node)

    //Reverse the offsets
    val fields = mutableMapOf<String, StructDataField>()
    fieldsOffsetFromTop.forEach {
        val offset = currentSize - it.value.type.size - it.value.offset
        fields[it.key] = StructDataField(it.value.name, offset, it.value.type)
    }


    return FrameLayout(
            currentSize,
            node.name,
            fields,
            sizeOfParams,
            sizeOfMeta,
            sizeOfVars,
            sizeOfRet,
    )

}