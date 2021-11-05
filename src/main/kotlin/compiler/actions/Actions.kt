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

class Print : ActionConverter {

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

        val value = builder.getActionOnStack(node.target, byteType) ?: return null
        return CompositeAction(
            value,
            PopRegister(),
            PrintAction(),
        )
    }
}

class PutByteOnStack : ActionConverter {
    //TODO remove?? and split to lda+ push???

    override fun putOnStack(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder,
    ): Action? {
        if (node !is ConstantNode || type != byteType) {
            return null
        }
        return PushConstant(byte(node.value))
    }
}

class SizeofToInt : ActionConverter {
    override fun putOnStack(node: ValueNode, type: DataType, builder: ActionBuilder): Action? {
        if (node !is SizeofNode) return null

        val provideType = builder.getType(node.type)
        val size = provideType.size

        return builder.getActionOnStack(ConstantNode(size.toInt()), type)
    }
}


class AssignFrameByte : ActionConverter {


    override fun buildStatement(node: StatementNode, builder: ActionBuilder): Action? {
        if (node !is AssignNode)
            return null
        if (node.value == null) {
            return null
        }

        val pushMemberAddress = pushAddressCheckType(node.target, builder.currentFunction, byteType)

        val putValueOnStack = builder.getActionOnStack(node.value, byteType) ?: return null

        return CompositeAction(
            pushMemberAddress,
            putValueOnStack,
            PopRegister(),
            StoreRegisterAtStackAddress(0u),
            PopThrow(),
        )
    }
}

class CreateArray : ActionConverter {

    data class MakeSpaceOnStack(
        override val cost: Int = 1,
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.sub_sp_a.build())
        }
    }

    data class RemoveSpaceOnStack(
        val amount: UByte,
        override val cost: Int = 1,
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to amount)))
        }
    }


    data class PushStackPointer(
        override val cost: Int = 1,
    ) : Action {
        override fun compile(generator: CodeGenerator) {
            generator.generate(DefaultEmulator.pushsp.build())
        }
    }

    override fun buildStatement(node: StatementNode, builder: ActionBuilder): Action? {

        if (node !is AssignNode) return null
        node.value ?: return null

        val callNode = node.value

        if (callNode !is CallNode) return null
        if (callNode.targetName != "createArray") return null
        if (callNode.parameters.size != 1) return null

        val actions = mutableListOf<Action>()

        //size on stack
        actions.add(builder.getActionOnStack(callNode.parameters[0], byteType) ?: return null)
        //Pop the size from the stack
        actions.add(PopRegister())
        //Size is now in a register
        //TODO: multiply with element size
        //Make space for array
        actions.add(MakeSpaceOnStack())

        //Push the size-value to the array struct
        actions.add(PushRegister())

        //Push stackpointer, this is the pointer to the new array
        actions.add(PushStackPointer())

        val address = pushAddress(node.target, builder.currentFunction)
        if (address.resultingType !is Pointer) return null
        if (address.resultingType.type != ArrayType(byteType))
            throw CompileError("Not supported yet")

        //Push address
        actions.add(address.action)

        //Load array position, which is at offset 1
        actions.add(ByteToStack.LoadRegisterStackAddress(1u))

        //Store address at array-pointer-position
        actions.add(StoreRegisterAtStackAddress(0u))

        //Restore stack
        actions.add(RemoveSpaceOnStack(2u))

        return CompositeAction(*actions.toTypedArray())
    }
}


class ByteToStack : ActionConverter {
    data class LoadRegisterStackAddressDeref(
        val offset: UByte,
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(
                DefaultEmulator.lda_at_sp_offset_deref.build(
                    mapOf("offset" to offset)
                )
            )
        }
    }

    data class LoadRegisterStackAddress(
        val offset: UByte,
    ) : Action {
        override val cost: Int = 1
        override fun compile(generator: CodeGenerator) {
            generator.generate(
                DefaultEmulator.lda_at_sp_offset.build(
                    mapOf("offset" to offset)
                )
            )
        }
    }


    override fun putOnStack(
        node: ValueNode,
        type: DataType,
        builder: ActionBuilder
    ): Action? {
        if (node !is Identifier && node !is MemberAccess && node !is MemberDeref) return null

        if (type != byteType) return null

        val pushMemberAddress = pushAddressCheckType(node, builder.currentFunction, byteType)

        return CompositeAction(
            pushMemberAddress,
            LoadRegisterStackAddressDeref(0u),
            PopThrow(),
            PushRegister(),
        )
    }
}

class FieldToPointer : ActionConverter {
    override fun putOnStack(node: ValueNode, type: DataType, builder: ActionBuilder): Action? {
        if (type !is Pointer) return null

        return pushAddressCheckType(node, builder.currentFunction, type.type)
    }
}

val actions = listOf(
    CreateArray(),
    Print(),
    PutByteOnStack(),
    AssignFrameByte(),
    ByteToStack(),
    AdditionProvider(),
    SubtractionProvider(),

    NotEqualProvider(),
    CallProvider(),
    FieldToPointer(),
    SizeofToInt(),
)

class ActionBuilder(
    private val function: FunctionInfo,
    private val functionProvider: FunctionProvider,
    private val typeProvider: TypeProvider,
) : FunctionProvider, TypeProvider {
    val currentFunction: FunctionInfo
        get() = function

    override fun getFunction(name: String): FunctionInfo {
        return functionProvider.getFunction(name)
    }

    override fun getType(name: String): DataType {
        return typeProvider.getType(name)
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


