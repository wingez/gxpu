package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.FunctionNode
import se.wingez.ast.NodeTypes
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

    val returnType = if (node.functionData.returnType.isEmpty())
        voidType
    else
    // TODO: handle explicit new
        typeProvider.getType(node.functionData.returnType).instantiate(false)

    fieldBuilder.addMember("result", returnType)

    // Figure out what parameters to use. But we should not add them to the layout until we've added the local variables
    val parameters = mutableListOf<Pair<String, DataType>>()
    for (arg in node.functionData.arguments) {
        val memberData = arg.asMemberDeclaration()
        if (memberData.explicitNew) {
            throw CompileError("Explicit new not allowed for parameters")
        }

        var paramType = typeProvider.getType(memberData.type)
        if (memberData.isArray) {
            paramType = ArrayType(paramType)
        }
        paramType = paramType.instantiate(false)
        parameters.add(Pair(memberData.name, paramType))
    }




    // Traverse recursively. Can probably be flattened but meh
    fun traverseNode(nodeToVisit: AstNode) {
        for (visitNode in nodeToVisit) {
            var name: String
            var typeName: String
            var explicitNew: Boolean
            var isArray = false

            if (visitNode.type == NodeTypes.Assign) {
                val assignNode = visitNode.asAssign()
                if (assignNode.target.type != NodeTypes.Identifier) {
                    // TODO what should happen here??
                    //throw CompileError("To complex for now")
                    continue
                }
                name = assignNode.target.asIdentifier().name
                typeName = assignNode.assignData.type
                explicitNew = assignNode.assignData.explicitNew


            } else if (visitNode.type == NodeTypes.MemberDeclaration) {
                val memberData = visitNode.asMemberDeclaration()
                name = memberData.name
                typeName = memberData.type
                explicitNew = memberData.explicitNew
                isArray = memberData.isArray
            } else {
                if (visitNode.hasChildren()) {
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
        node.functionData.name,
        fields,
        parameterNames, returnType
    )

}