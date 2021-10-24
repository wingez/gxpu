package se.wingez.compiler.actions

import se.wingez.ast.Operation
import se.wingez.ast.SingleOperationNode
import se.wingez.ast.ValueProviderNode
import se.wingez.compiler.*
import se.wingez.emulator.DefaultEmulator


abstract class ArithmeticProvider(
    private val operation: Operation
) : ActionConverter {

    abstract fun generate(): Action

    override fun putInRegister(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
        if (node !is SingleOperationNode) return null
        else if (node.operation != operation) return null
        else if (type != byteType) return null

        val putLeftOnStack = getActionOnStack(node.left, byteType, frame)
        val putRightOnStack = getActionOnStack(node.right, byteType, frame)

        if (putLeftOnStack == null || putRightOnStack == null) {
            return null
        }

        return CompositeAction(putRightOnStack, putLeftOnStack, generate())
    }
}

class AdditionProvider : ArithmeticProvider(Operation.Addition) {
    data class AdditionAction(
        override val cost: Int = 2
    ) : Action {


        override fun compile(generator: CodeGenerator) {
            //Pop left
            generator.generate(DefaultEmulator.popa.build())
            generator.generate(DefaultEmulator.adda_sp.build(mapOf("offset" to 0u)))
            //Pop left
            generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to 1u)))
        }

    }


    override fun generate(): Action {
        return AdditionAction()
    }

}

