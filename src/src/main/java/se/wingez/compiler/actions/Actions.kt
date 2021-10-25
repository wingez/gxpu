package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.byte
import se.wingez.compiler.actions.AdditionProvider
import se.wingez.compiler.actions.SubtractionProvider
import se.wingez.emulator.DefaultEmulator


interface Action {
    val cost: Int

    fun compile(generator: CodeGenerator)
}

interface ActionConverter {
    fun putInRegister(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
        return null
    }

    fun putOnStack(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
        return null
    }


    fun buildStatement(node: StatementNode, frame: FrameLayout): Action? {
        return null
    }
}

class CompositeAction(
    vararg actions: Action
) : Action, Iterable<Action> {
    private val actions = actions.asList()
    override val cost: Int
        get() = actions.sumOf { it.cost }

    override fun compile(generator: CodeGenerator) {
        for (action in actions) {
            action.compile(generator)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CompositeAction)
            return false
        return this.actions == other.actions
    }

    override fun hashCode(): Int {
        return actions.hashCode()
    }

    override fun iterator(): Iterator<Action> {
        return actions.iterator()
    }

    override fun toString(): String {

        return "CompositeAction $actions"
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

    override fun buildStatement(node: StatementNode, frame: FrameLayout): Action? {
        if (node !is PrintNode)
            return null

        val value = getActionInRegister(node.target, byteType, frame) ?: return null
        return CompositeAction(value, PrintAction())
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

    override fun putInRegister(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
        if (node is ConstantNode)
            return PutByteInRegisterAction(byte(node.value))
        return null
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

    override fun putOnStack(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
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

    override fun buildStatement(node: StatementNode, frame: FrameLayout): Action? {
        if (node !is AssignNode)
            return null
        if (node.value == null) {
            return null
        }
        if (node.target.member.actions.isNotEmpty()) {
            //To complex..
            return null
        }
        val field = node.target.member.name
        if (!frame.hasField(field)) {
            throw CompileError("No field with name: $field")
        }
        if (frame.fields.getValue(field).type != byteType) {
            return null
        }

        val putValueInRegister = getActionInRegister(node.value, byteType, frame) ?: return null

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

    override fun putInRegister(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {

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

    override fun putOnStack(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
        if (type != byteType) {
            return null
        }
        val putInRegister = getActionInRegister(node, type, frame) ?: return null
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
)


fun getActionOnStack(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
    for (action in actions) {
        val result = action.putOnStack(node, type, frame)
        if (result != null)
            return result
    }
    return null
}

fun getActionInRegister(node: ValueProviderNode, type: DataType, frame: FrameLayout): Action? {
    for (action in actions) {
        val result = action.putInRegister(node, type, frame)
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

fun buildStatement(node: StatementNode, frame: FrameLayout): Action {
    for (a in actions) {
        val result = a.buildStatement(node, frame)
        if (result != null) {
            return result
        }
    }

    throw CompileError("Dont know how to build $node")
}
