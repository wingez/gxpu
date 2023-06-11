package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes


data class AllocateReturnAndVariables(
    val toCall: FunctionSignature
)

data class PlaceConstant(
    val constant: Int
)

data class PlaceFrameVariable(
    val baseVariableName: String,
    val offset: Int,
)

data class Call(
    val toCall: FunctionSignature,
)

data class PopArgumentsVariables(
    val toCall: FunctionSignature,
)

fun flatten(
    node: AstNode,
    builder: FunctionBuilder,
): Pair<List<Any>, DataType> {

    if (node.type == NodeTypes.Constant) {
        return Pair(listOf(PlaceConstant(node.asConstant())), byteType)
    }

    if (node.type == NodeTypes.Identifier || node.type == NodeTypes.MemberAccess) {

        val (baseVariableName, type, offset) = findStoreAddress(node, builder)


        return Pair(listOf(PlaceFrameVariable(baseVariableName, offset)), type)
    }


    val functionName: String
    val parameterNodes: List<AstNode>

    if (node.type == NodeTypes.Call) {
        val callInfo = node.asCall()
        functionName = callInfo.targetName
        parameterNodes = callInfo.parameters
    } else {
        throw AssertionError()
    }

    val placeArgumentActions = mutableListOf<Any>()
    val parameterSignature = mutableListOf<DataType>()

    for (paramNode in parameterNodes.reversed()) {
        val (actions, paramType) = flatten(paramNode, builder)
        placeArgumentActions.addAll(actions)
        parameterSignature.add(paramType)
    }

    val toCallSignature = builder.functionProvider.findSignature(functionName, parameterSignature)


    val allActions = mutableListOf<Any>()
    allActions.add(AllocateReturnAndVariables(toCallSignature))
    //Place arguments
    allActions.addAll(placeArgumentActions)
    allActions.add(Call(toCallSignature))
    allActions.add(PopArgumentsVariables(toCallSignature))

    return Pair(allActions, toCallSignature.returnType)
}

fun findStoreAddress(target: AstNode, builder: FunctionBuilder): Triple<String, DataType, Int> {

    var currentNode = target

    val accessOrder = mutableListOf<String>()

    while (true) {
        when (currentNode.type) {
            NodeTypes.Identifier -> {
                accessOrder.add(currentNode.asIdentifier())
                break
            }

            NodeTypes.MemberAccess -> {
                accessOrder.add(currentNode.data as String)
                currentNode = currentNode.childNodes[0]
            }

            else -> {
                throw AssertionError()
            }
        }
    }

    val baseFieldName = accessOrder.removeLast()

    val baseFieldType = builder.getLocalVariableType(baseFieldName)

    var currentType = baseFieldType
    var currentOffset = 0

    for (nextAccess in accessOrder.reversed()) {
        if (currentType !is StructType) {
            TODO()
        }
        val nextField = currentType.getField(nextAccess)
        currentType = nextField.type
        currentOffset += nextField.offset
    }
    return Triple(baseFieldName, currentType, currentOffset)
}


fun putOnStack(
    node: AstNode,
    builder: FunctionBuilder
): DataType {


    val (actions, resultType) = flatten(node, builder)

    for (action in actions) {
        when (action) {
            is PlaceConstant -> {
                builder.generator.generate(DefaultEmulator.push.build(mapOf("val" to action.constant)))
            }

            is AllocateReturnAndVariables -> {
                val returnSize = action.toCall.returnType.size

                if (action.toCall.annotations.contains(FunctionAnnotation.NoFrame)) {
                    if (returnSize > 0) {
                        builder.generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to returnSize)))
                    }
                } else {
                    builder.generator.link(DefaultEmulator.sub_sp, action.toCall, LinkType.VarsSize, returnSize)
                }

            }

            is Call -> {
                builder.generator.link(DefaultEmulator.call_addr, action.toCall, LinkType.FunctionAddress)
            }

            is PopArgumentsVariables -> {

                val parameterSize = action.toCall.parameters.sumOf { it.type.size }

                if (action.toCall.annotations.contains(FunctionAnnotation.NoFrame)) {
                    if (parameterSize > 0) {
                        builder.generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to parameterSize)))
                    }
                } else {
                    builder.generator.link(DefaultEmulator.add_sp, action.toCall, LinkType.VarsSize, parameterSize)
                }
            }

            is PlaceFrameVariable -> {
                builder.linkVariable(DefaultEmulator.push_fp_offset, action.baseVariableName, action.offset)
            }

            else -> throw AssertionError()
        }
    }

    return resultType
}

fun buildAssignment(
    node: AstNode,
    builder: FunctionBuilder,
) {

    val valueProviderNode = node.asAssign().value
    val resultType = putOnStack(valueProviderNode, builder)

    if (resultType != byteType) {
        TODO("Need to take order into account")
    }

    val (baseTypeName, resultingType, offset) = findStoreAddress(node.asAssign().target, builder)

    if (resultType != resultingType) {
        TODO()
    }

    builder.linkVariable(DefaultEmulator.pop_fp_offset, baseTypeName, offset)
}

fun buildNoResultStatement(
    node: AstNode,
    builder: FunctionBuilder,
) {

    val resultType = putOnStack(node, builder)

    // pop value of stack
    if (resultType.size > 0) {
        builder.generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to resultType.size)))
    }
}