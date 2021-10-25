package se.wingez.compiler.actions

import se.wingez.ast.CallNode
import se.wingez.ast.StatementNode
import se.wingez.ast.ValueProviderNode
import se.wingez.compiler.*
import se.wingez.emulator.DefaultEmulator


class CallProvider : ActionConverter {

    data class PlaceReturnValueOnStack(
        val type: DataType,
        override val cost: Int = 1,
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            // Place return value
            if (type.size > 0u) {
                generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to type.size)))
            }
        }
    }

    data class CallAction(
        val function: AssemblyFunction,
        override val cost: Int = 2,
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.call_addr.build(mapOf("addr" to function.memoryPosition)))
        }
    }

    data class PopArguments(
        val function: AssemblyFunction,
        override val cost: Int = 1,
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            val paramSize = function.frameLayout.sizeOfParameters
            if (paramSize > 0u) {
                generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to paramSize)))
            }
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


    override fun buildStatement(node: StatementNode, frame: FrameLayout, functionProvider: FunctionProvider): Action? {
        if (node !is CallNode) return null

        val function = functionProvider.getFunction(node.targetName)
        if (function.frameLayout.returnType != voidType) return null

        val callAction = putOnStack(node, function.frameLayout.returnType, frame, functionProvider)
        callAction ?: return null
        return CompositeAction(
            callAction, PopResult(function.frameLayout.returnType)
        )
    }

    override fun putOnStack(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        if (node !is CallNode) return null

        val function = functionProvider.getFunction(node.targetName)

        if (function.frameLayout.returnType != type) return null

        if (function.frameLayout.parameters.size != node.parameters.size) {
            throw CompileError("Wrong amount of parameters provided")
        }
        val actions = mutableListOf<Action>()

        //Make space for return value
        actions.add(PlaceReturnValueOnStack(function.frameLayout.returnType))

        //place arguments
        for ((parameter, paramInfo) in node.parameters.zip(function.frameLayout.parameters)) {
            val action = getActionOnStack(parameter, paramInfo.type, frame, functionProvider)
                ?: throw CompileError("Type mismatch: ${paramInfo.type}")
            actions.add(action)
        }
        //Call
        actions.add(CallAction(function))

        //Pop arguments
        actions.add(PopArguments(function))
        return CompositeAction(*actions.toTypedArray())
    }
}

