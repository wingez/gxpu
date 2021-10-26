package se.wingez.compiler.actions

import se.wingez.ast.Operation
import se.wingez.ast.SingleOperationNode
import se.wingez.ast.ValueNode
import se.wingez.compiler.CodeGenerator
import se.wingez.compiler.DataType
import se.wingez.compiler.byteType
import se.wingez.compiler.compareType
import se.wingez.emulator.DefaultEmulator


abstract class ArithmeticProvider(
    private val operation: Operation
) : ActionConverter {

    abstract fun generate(): Action

    override fun putInRegister(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder,
    ): Action? {
        if (node !is SingleOperationNode) return null
        else if (node.operation != operation) return null
        else if (type != byteType) return null

        val putLeftInRegister = builder.getActionInRegister(node.left, byteType)
        val putRightOnStack = builder.getActionOnStack(node.right, byteType)

        if (putLeftInRegister == null || putRightOnStack == null) {
            return null
        }

        return CompositeAction(putRightOnStack, putLeftInRegister, generate())
    }
}

class AdditionProvider : ArithmeticProvider(Operation.Addition) {
    data class AdditionAction(
        override val cost: Int = 2
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            // Left already in stack
            generator.generate(DefaultEmulator.adda_sp.build(mapOf("offset" to 0u)))
            //Pop right
            generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to 1u)))
        }
    }

    override fun generate(): Action {
        return AdditionAction()
    }
}

class SubtractionProvider : ArithmeticProvider(Operation.Subtraction) {
    data class SubtractionAction(
        override val cost: Int = 2
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            //Left already on stack
            generator.generate(DefaultEmulator.suba_sp.build(mapOf("offset" to 0u)))
            // Pop right
            generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to 1u)))
        }
    }

    override fun generate(): Action {
        return SubtractionAction()
    }
}

class NotEqualProvider : ActionConverter {
    data class NotEqualCompare(
        override val cost: Int = 1
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.testa.build())
        }
    }


    override fun putInRegister(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder
    ): Action? {
        if (type != compareType) return null
        if (node !is SingleOperationNode) return null
        if (node.operation != Operation.NotEquals) return null

        val subtract = builder.getActionInRegister(
            SingleOperationNode(
                Operation.Subtraction, node.left, node.right
            ), byteType
        ) ?: return null

        return CompositeAction(
            subtract,
            NotEqualCompare()
        )
    }
}

