package se.wingez.compiler.actions

import se.wingez.ast.Operation
import se.wingez.ast.SingleOperationNode
import se.wingez.ast.StatementNode
import se.wingez.ast.ValueNode
import se.wingez.compiler.CodeGenerator
import se.wingez.compiler.DataType
import se.wingez.compiler.byteType
import se.wingez.emulator.DefaultEmulator


data class SubtractionAction(
    override val cost: Int = 2
) : Action {
    override fun compile(generator: CodeGenerator) {
        //Left already on stack
        generator.generate(DefaultEmulator.suba_sp.build(mapOf("offset" to 0u)))
    }
}

data class AdditionAction(
    override val cost: Int = 2
) : Action {
    override fun compile(generator: CodeGenerator) {
        // Add  top of stack to A
        generator.generate(DefaultEmulator.adda_sp.build(mapOf("offset" to 0u)))

    }
}

data class NotEqualCompare(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.testa.build())
    }
}


/**
 * Right is in A and Left on top of stack.
 * Result should be stored in A and Stack should not be touched
 */

fun arithmeticToStack(node: ValueNode, type: DataType, builder: ActionBuilder): Action? {
    if (node !is SingleOperationNode) return null
    else if (type != byteType) return null

    val operationAction = when (node.operation) {
        Operation.Addition -> AdditionAction()
        Operation.Subtraction -> SubtractionAction()
        else -> return null
    }

    val putRightOnStack = builder.getActionOnStack(node.right, byteType) ?: return null
    val putLeftOnStack = builder.getActionOnStack(node.left, byteType) ?: return null

    return CompositeAction(
        putRightOnStack,
        putLeftOnStack,
        PopRegister(),
        operationAction,
        PopThrow(),
        PushRegister(),
    )
}

fun notEqualCompare(node: StatementNode, builder: ActionBuilder): Action? {
    if (node !is SingleOperationNode) return null
    if (node.operation != Operation.NotEquals) return null

    val subtractToStack = builder.getActionOnStack(
        SingleOperationNode(
            Operation.Subtraction, node.left, node.right
        ), byteType
    ) ?: return null

    return CompositeAction(
        subtractToStack,
        PopRegister(),
        NotEqualCompare()
    )
}

