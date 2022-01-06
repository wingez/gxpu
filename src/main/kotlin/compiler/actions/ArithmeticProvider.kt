package se.wingez.compiler.actions

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.ast.OperationNode
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

fun arithmeticToStack(node: AstNode, type: DataType, builder: ActionBuilder): Action? {
    if (node !is OperationNode) return null
    else if (type != byteType) return null

    val operationAction = when (node.type) {
        NodeTypes.Addition -> AdditionAction()
        NodeTypes.Subtraction -> SubtractionAction()
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

fun notEqualCompare(node: AstNode, builder: ActionBuilder): Action? {
    if (node !is OperationNode) return null
    if (node.type != NodeTypes.NotEquals) return null

    val subtractToStack = builder.getActionOnStack(
        OperationNode(
            NodeTypes.Subtraction, node.left, node.right
        ), byteType
    ) ?: return null

    return CompositeAction(
        subtractToStack,
        PopRegister(),
        NotEqualCompare()
    )
}

