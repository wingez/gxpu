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

data class Call(
    val toCall: FunctionSignature,
)

data class PopArgumentsVariables(
    val toCall: FunctionSignature,
)

fun flatten(node: AstNode, functionProvider: FunctionProvider): Pair<List<Any>, DataType> {

    if (node.type == NodeTypes.Constant) {
        return Pair(listOf(PlaceConstant(node.asConstant())), byteType)
    }

    val functionName: String
    val parameterNodes: List<AstNode>

    if (node.type == NodeTypes.Addition) {
        functionName = "add"
        parameterNodes = node.childNodes
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
        val (actions, paramType) = flatten(paramNode, functionProvider)
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


fun findStoreAddress(target: AstNode, functionInfo: FunctionSignature): Int {

    if (target.type != NodeTypes.Identifier) {
        throw AssertionError()
    }

    return functionInfo.getField(target.asIdentifier()).offset

}

fun buildStatement(
    node: AstNode,
    functionInfo: FunctionSignature,
    generator: CodeGenerator,
    functionProvider: FunctionProvider
) {

    val valueProviderNode: AstNode
    val isAssignment = node.type == NodeTypes.Assign

    if (isAssignment) {
        valueProviderNode = node.asAssign().value
    } else {
        valueProviderNode = node
    }


    val (actions, resultType) = flatten(valueProviderNode, functionProvider)

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
            else -> throw AssertionError()
        }
    }

    // Handle result
    if (isAssignment) {

        if (resultType != byteType) {
            throw AssertionError()
        }

        val storeOffset = findStoreAddress(node.asAssign().target, functionInfo)
        generator.generate(
            DefaultEmulator.memcpy_stack_to_frame.build(
                mapOf("spoffset" to 0, "fpoffset" to storeOffset)
            )
        )
    }

    // pop value of stack
    if (resultType.size > 0) {
        generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to resultType.size)))
    }
}