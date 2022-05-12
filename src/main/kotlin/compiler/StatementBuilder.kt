package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.emulator.DefaultEmulator


data class AllocateReturnAndVariables(
    val toCall: FunctionSignature
)

data class PlaceConstant(
    val constant: Int
)

data class PlaceFrameVariable(
    val frameVariable: StructDataField
)

data class Call(
    val toCall: FunctionSignature,
)

data class PopArgumentsVariables(
    val toCall: FunctionSignature,
)

fun flatten(
    node: AstNode,
    functionSignature: FunctionSignature,
    functionProvider: FunctionProvider
): Pair<List<Any>, DataType> {

    if (node.type == NodeTypes.Constant) {
        return Pair(listOf(PlaceConstant(node.asConstant())), byteType)
    }

    if (node.type == NodeTypes.Identifier || node.type == NodeTypes.MemberAccess) {

        val variable = findStoreAddress(node, functionSignature)


        return Pair(listOf(PlaceFrameVariable(variable)), variable.type)
    }


    val functionName: String
    val parameterNodes: List<AstNode>

    if (node.type == NodeTypes.Addition) {
        functionName = "add"
        parameterNodes = node.childNodes
    } else if (node.type == NodeTypes.Subtraction) {
        functionName = "subtract"

        parameterNodes = node.childNodes.reversed() // Subtractor first
    } else if (node.type == NodeTypes.Print) {
        functionName = "print"
        parameterNodes = node.childNodes
    } else if (node.type == NodeTypes.Call) {
        val callInfo = node.asCall()
        functionName = callInfo.targetName
        parameterNodes = callInfo.parameters
    } else {
        throw AssertionError()
    }

    val placeArgumentActions = mutableListOf<Any>()
    val parameterSignature = mutableListOf<DataType>()

    for (paramNode in parameterNodes) {
        val (actions, paramType) = flatten(paramNode, functionSignature, functionProvider)
        placeArgumentActions.addAll(actions)
        parameterSignature.add(paramType)
    }

    val toCall = functionProvider.includeFunction(functionName, parameterSignature)


    val allActions = mutableListOf<Any>()
    allActions.add(AllocateReturnAndVariables(toCall))
    //Place arguments
    allActions.addAll(placeArgumentActions)
    allActions.add(Call(toCall))
    allActions.add(PopArgumentsVariables(toCall))

    return Pair(allActions, toCall.returnType)
}

fun findStoreAddressNested(target: AstNode, functionInfo: FunctionSignature): StructDataField {

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

    var field = functionInfo.getField(accessOrder.removeLast())
    var currentOffset = field.offset
    

    for (nextAccess in accessOrder.reversed()) {
        val currentType = field.type
        if (currentType !is StructType) {
            TODO()
        }
        field = currentType.getField(nextAccess)
        currentOffset += field.offset
    }
    return StructDataField(field.name, currentOffset, field.type)
}

fun findStoreAddress(target: AstNode, functionInfo: FunctionSignature): StructDataField {
    return findStoreAddressNested(target, functionInfo)

}

fun putOnStack(
    node: AstNode,
    functionInfo: FunctionSignature,
    generator: CodeGenerator,
    functionProvider: FunctionProvider,
): DataType {


    val (actions, resultType) = flatten(node, functionInfo, functionProvider)

    for (action in actions) {
        when (action) {
            is PlaceConstant -> {
                generator.generate(DefaultEmulator.push.build(mapOf("val" to action.constant)))
            }
            is AllocateReturnAndVariables -> {
                val size = action.toCall.sizeOfReturn + action.toCall.sizeOfVars
                if (size > 0) {
                    generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to size)))
                }
            }
            is Call -> {
                generator.link(DefaultEmulator.call_addr, action.toCall)
            }
            is PopArgumentsVariables -> {
                val size = action.toCall.sizeOfVars + action.toCall.sizeOfParameters
                if (size > 0) {
                    generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to size)))
                }
            }
            is PlaceFrameVariable -> {
                generator.generate(DefaultEmulator.push_fp_offset.build(mapOf("offset" to action.frameVariable.offset)))
            }
            else -> throw AssertionError()
        }
    }

    return resultType
}

fun buildAssignment(
    node: AstNode,
    functionInfo: FunctionSignature,
    generator: CodeGenerator,
    functionProvider: FunctionProvider,
) {

    val valueProviderNode = node.asAssign().value
    val resultType = putOnStack(valueProviderNode, functionInfo, generator, functionProvider)

    if (resultType != byteType) {
        TODO("Need to take order into account")
    }

    val storeOffset = findStoreAddress(node.asAssign().target, functionInfo).offset
    generator.generate(DefaultEmulator.pop_fp_offset.build(mapOf("offset" to storeOffset)))
}

fun buildNoResultStatement(
    node: AstNode,
    functionInfo: FunctionSignature,
    generator: CodeGenerator,
    functionProvider: FunctionProvider,
) {

    val resultType = putOnStack(node, functionInfo, generator, functionProvider)

    // pop value of stack
    if (resultType.size > 0) {
        generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to resultType.size)))
    }
}