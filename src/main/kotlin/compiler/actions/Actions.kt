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
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder,
    ): Action? {
        return null
    }

    fun putOnStack(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder,
    ): Action? {
        return null
    }


    fun buildStatement(
        node: StatementNode,
        builder: ActionBuilder,
    ): Action? {
        return null
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

    override fun buildStatement(node: StatementNode, builder: ActionBuilder): Action? {
        if (node !is PrintNode)
            return null

        var value = builder.getActionInRegister(node.target, byteType)
        if (value != null)
            return CompositeAction(value, PrintAction())

        value = builder.getActionOnStack(node.target, byteType)
        value ?: return null
        return CompositeAction(
            value,
            PopRegister(),
            PrintAction(),
        )
    }
}

class PutConstantInRegister : ActionConverter {

    override fun putInRegister(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder,
    ): Action? {
        if (node !is ConstantNode) return null
        if (type != byteType) return null
        return LoadRegister(byte(node.value))
    }
}

class PutByteOnStack : ActionConverter {

    override fun putOnStack(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder,
    ): Action? {
        if (node !is ConstantNode || type != byteType) {
            return null
        }
        return PushByte(byte(node.value))
    }
}

fun calculateFrameMemberOffset(node: ValueNode, function: FunctionInfo): StructDataField? {
    var currentNode = node
    val accessOrder = mutableListOf<String>()
    while (true) {
        if (currentNode is Identifier) {
            accessOrder.add(currentNode.name)
            break
        }
        if (currentNode is MemberAccess) {
            accessOrder.add(currentNode.member)
            currentNode = currentNode.left
        } else {
            return null
        }
    }
    val fieldDescription = accessOrder.reversed().joinToString(".")

    var currentOffset = byte(0)
    var currentType: DataType = function

    while (accessOrder.isNotEmpty()) {
        val access = accessOrder.removeLast()

        if (currentType !is StructType) {
            throw CompileError("Cannot read $access from type $currentType ")
        }
        if (!currentType.hasField(access)) {
            throw CompileError("Type $currentType has no member $access")
        }

        val field = currentType.getField(access)
        currentOffset = byte(currentOffset + field.offset)
        currentType = field.type
    }
    return StructDataField(fieldDescription, currentOffset, byteType)
}

class AssignFrameByte : ActionConverter {
    data class AssignFrameRegister(
        val field: StructDataField,
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(
                DefaultEmulator.sta_fp_offset.build(
                    mapOf("offset" to field.offset)
                )
            )
        }
    }

    override fun buildStatement(node: StatementNode, builder: ActionBuilder): Action? {
        if (node !is AssignNode)
            return null
        if (node.value == null) {
            return null
        }

        val member = calculateFrameMemberOffset(node.target, builder.currentFunction) ?: return null

        if (member.type != byteType) {
            return null
        }

        val putValueInRegister = builder.getActionInRegister(node.value, byteType) ?: return null

        return CompositeAction(
            putValueInRegister,
            AssignFrameRegister(member)
        )
    }
}

class FieldByteToRegister : ActionConverter {
    data class FieldByteToRegisterAction(
        val field: StructDataField,
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(
                DefaultEmulator.lda_fp_offset.build(
                    mapOf("offset" to field.offset)
                )
            )
        }
    }

    override fun putInRegister(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder
    ): Action? {

        if (type != byteType)
            return null

        val field = calculateFrameMemberOffset(node, builder.currentFunction) ?: return null

        return FieldByteToRegisterAction(field)
    }
}

class PutRegisterOnStack : ActionConverter {

    override fun putOnStack(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder
    ): Action? {
        if (type != byteType) {
            return null
        }
        val putInRegister = builder.getActionInRegister(node, type) ?: return null
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

class ActionBuilder(
    private val function: FunctionInfo,
    private val functionProvider: FunctionProvider,
) : FunctionProvider {
    val currentFunction: FunctionInfo
        get() = function

    override fun getFunction(name: String): FunctionInfo {
        return functionProvider.getFunction(name)
    }

    fun getActionOnStack(
        node: ValueNode,
        type: DataType,
    ): Action? {
        for (action in actions) {
            val result = action.putOnStack(node, type, this)
            if (result != null)
                return result
        }
        return null
    }

    fun getActionInRegister(
        node: ValueNode,
        type: DataType,
    ): Action? {
        for (action in actions) {
            val result = action.putInRegister(node, type, this)
            if (result != null)
                return result
        }
        return null
    }

    fun buildStatement(node: StatementNode): Action {
        for (a in actions) {
            val result = a.buildStatement(node, this)
            if (result != null) {
                return result
            }
        }

        throw CompileError("Dont know how to build $node")
    }
}


