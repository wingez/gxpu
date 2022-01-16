package se.wingez.compiler.actions

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.byte
import se.wingez.compiler.*
import se.wingez.emulator.DefaultEmulator


data class CallAction(
    val function: FunctionInfo,
    override val cost: Int = 2,
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.call_addr.build(mapOf("addr" to function.memoryPosition)))
    }
}

data class PopResult(
    val type: DataType,
    override val cost: Int = 1,
) : Action {
    override fun compile(generator: CodeGenerator) {
        if (type.size > 0u) {
            generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to type.size)))
        }
    }
}


fun callStatement(node: AstNode, builder: ActionBuilder): Action? {
    if (node.type != NodeTypes.Call) return null

    val function = builder.getFunction(node.asCall().targetName)
    if (function.returnType != voidType) return null

    val callAction = callToStack(node, function.returnType, builder) ?: return null
    return CompositeAction(
        callAction, PopResult(function.returnType)
    )
}

fun callToStack(node: AstNode, type: DataType, builder: ActionBuilder): Action? {
    if (node.type != NodeTypes.Call) return null
    val callInfo = node.asCall()

    val function = builder.getFunction(callInfo.targetName)

    if (function.returnType != type) return null

    if (function.parameters.size != callInfo.parameters.size) {
        throw CompileError("Wrong amount of parameters provided")
    }
    val actions = mutableListOf<Action>()

    //Make space for return value
    actions.add(AllocSpaceOnStack(function.sizeOfReturn))

    //make space for frame
    actions.add(AllocSpaceOnStack(function.sizeOfVars))

    //place arguments
    for ((parameter, paramInfo) in callInfo.parameters.zip(function.parameters)) {
        val action = builder.getActionOnStack(parameter, paramInfo.type)
            ?: throw CompileError("Type mismatch: ${paramInfo.type}")
        actions.add(action)
    }



    //Call
    actions.add(CallAction(function))

    //Pop arguments and vars
    actions.add(RemoveSpaceOnStack(byte(function.sizeOfVars + function.sizeOfParameters)))
    return CompositeAction(*actions.toTypedArray())
}

