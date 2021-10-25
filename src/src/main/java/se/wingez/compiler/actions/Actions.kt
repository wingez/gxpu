package se.wingez.compiler.actions

import se.wingez.ast.*
import se.wingez.byte
import se.wingez.compiler.*
import se.wingez.emulator.DefaultEmulator


interface Action {
    val cost: Int

    fun compile(generator: CodeGenerator)
}

interface ActionConverter {
    fun putInRegister(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        return null
    }

    fun putOnStack(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        return null
    }


    fun buildStatement(
        node: StatementNode,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        return null
    }
}

data class PopStack(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.popa.build())
    }

}

class PrintFromRegister : ActionConverter {

    data class PrintAction(
        override val cost: Int = 1
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.print.build())
        }
    }

    override fun buildStatement(node: StatementNode, frame: FrameLayout, functionProvider: FunctionProvider): Action? {
        if (node !is PrintNode)
            return null

        var value = getActionInRegister(node.target, byteType, frame, functionProvider)
        if (value != null)
            return CompositeAction(value, PrintAction())

        value = getActionOnStack(node.target, byteType, frame, functionProvider)
        value ?: return null
        return CompositeAction(
            value,
            PopStack(),
            PrintAction(),
        )

    }

}

class PutConstantInRegister(
) : ActionConverter {

    data class PutByteInRegisterAction(
        val value: UByte
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.lda.build(mapOf("val" to value)))
        }
    }

    override fun putInRegister(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        if (node !is ConstantNode) return null
        if (type != byteType) return null
        return PutByteInRegisterAction(byte(node.value))
    }
}

class PutByteOnStack : ActionConverter {
    data class PutByteOnStackAction(
        val value: UByte
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.lda.build(mapOf("val" to value)))
            generator.generate(DefaultEmulator.pusha.build())
        }
    }

    override fun putOnStack(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        if (node !is ConstantNode || type != byteType) {
            return null
        }
        return PutByteOnStackAction(byte(node.value))
    }
}

class AssignFrameByte : ActionConverter {
    data class AssignFrameRegister(
        val frame: FrameLayout,
        val field: String,
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(
                DefaultEmulator.sta_fp_offset.build(
                    mapOf("offset" to frame.fields.getValue(field).offset)
                )
            )
        }
    }

    override fun buildStatement(node: StatementNode, frame: FrameLayout, functionProvider: FunctionProvider): Action? {
        if (node !is AssignNode)
            return null
        if (node.value == null) {
            return null
        }
        val target = node.target
        if (target !is MemberAccess) return null

        val field = target.name
        if (!frame.hasField(field)) {
            throw CompileError("No field with name: $field")
        }
        if (frame.fields.getValue(field).type != byteType) {
            return null
        }

        val putValueInRegister = getActionInRegister(node.value, byteType, frame, functionProvider) ?: return null

        return CompositeAction(
            putValueInRegister,
            AssignFrameRegister(frame, field)
        )
    }
}

class FieldByteToRegister : ActionConverter {
    data class FieldByteToRegisterAction(
        val frame: FrameLayout,
        val field: String,
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(
                DefaultEmulator.lda_fp_offset.build(
                    mapOf("offset" to frame.fields.getValue(field).offset)
                )
            )
        }
    }

    override fun putInRegister(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {

        if (node !is MemberAccess)
            return null
        if (type != byteType)
            return null

        val field = node.name
        if (!frame.hasField(field)) {
            throw CompileError("No field with name: $field")
        }
        if (frame.fields.getValue(field).type != byteType) {
            return null
        }

        return FieldByteToRegisterAction(frame, field)
    }
}

class PutRegisterOnStack : ActionConverter {
    data class PushRegister(
        override val cost: Int = 1
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.pusha.build())
        }
    }

    override fun putOnStack(
        node: ValueProviderNode,
        type: DataType,
        frame: FrameLayout,
        functionProvider: FunctionProvider
    ): Action? {
        if (type != byteType) {
            return null
        }
        val putInRegister = getActionInRegister(node, type, frame, functionProvider) ?: return null
        return CompositeAction(
            putInRegister,
            PushRegister(),
        )
    }
}

val actions = listOf(
    PutConstantInRegister(),
    PrintFromRegister(),
    PutByteOnStack(),
    AssignFrameByte(),
    FieldByteToRegister(),
    AdditionProvider(),
    SubtractionProvider(),
    PutRegisterOnStack(),

    NotEqualProvider(),
    CallProvider(),
)


fun getActionOnStack(
    node: ValueProviderNode,
    type: DataType,
    frame: FrameLayout,
    functionProvider: FunctionProvider
): Action? {
    for (action in actions) {
        val result = action.putOnStack(node, type, frame, functionProvider)
        if (result != null)
            return result
    }
    return null
}

fun getActionInRegister(
    node: ValueProviderNode,
    type: DataType,
    frame: FrameLayout,
    functionProvider: FunctionProvider
): Action? {
    for (action in actions) {
        val result = action.putInRegister(node, type, frame, functionProvider)
        if (result != null)
            return result
    }
    return null
}

fun flatten(topAction: Action): List<Action> {
    val result = mutableListOf<Action>()

    fun visitRecursive(action: Action) {
        if (action is CompositeAction) {
            for (child in action) {
                visitRecursive(child)
            }
        } else {
            result.add(action)
        }
    }
    visitRecursive(topAction)
    return result
}

fun buildStatement(node: StatementNode, frame: FrameLayout, functionProvider: FunctionProvider): Action {
    for (a in actions) {
        val result = a.buildStatement(node, frame, functionProvider)
        if (result != null) {
            return result
        }
    }

    throw CompileError("Dont know how to build $node")
}
