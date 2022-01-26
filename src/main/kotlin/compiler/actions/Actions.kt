package se.wingez.compiler.actions

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.compiler.*
import se.wingez.emulator.DefaultEmulator


interface Action {
    val cost: Int

    fun compile(generator: CodeGenerator)
}

data class LoadRegisterStackAddressDeref(
    val offset: Int,
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
    val offset: Int,
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

data class PrintAction(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.print.build())
    }
}


data class AllocDynamicSpaceOnStack(
    override val cost: Int = 1,
) : Action {
    /**
     * Space should be in a-register
     */
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.sub_sp_a.build())
    }
}

data class AllocSpaceOnStack(
    val space: Int,
) : Action {
    override val cost: Int = 1

    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to space)))
    }
}

data class RemoveSpaceOnStack(
    val amount: Int,
) : Action {
    override val cost: Int = 1

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

fun printStatement(node: AstNode, builder: ActionBuilder): Action? {
    if (node.type != NodeTypes.Print)
        return null

    val value = builder.getActionOnStack(node.childNodes[0], byteType) ?: return null
    return CompositeAction(
        value,
        PopRegister(),
        PrintAction(),
    )
}

fun putByteOnStack(node: AstNode, type: DataType, builder: ActionBuilder): Action? {
    //TODO remove?? and split to lda+ push???
    if (node.type != NodeTypes.Constant || type != byteType) {
        return null
    }
    return PushConstant(node.asConstant().value)
}

fun assignFrameByte(node: AstNode, builder: ActionBuilder): Action? {
    if (node.type != NodeTypes.Assign)
        return null

    val assign = node.asAssign()

    val pushMemberAddress = pushAddressCheckType(assign.target, builder.currentFunction, byteType, builder)

    val putValueOnStack = builder.getActionOnStack(assign.value, byteType) ?: return null

    return CompositeAction(
        pushMemberAddress,
        putValueOnStack,
        PopRegister(),
        StoreRegisterAtStackAddress(0),
        PopThrow(),
    )
}


fun createArray(node: AstNode, builder: ActionBuilder): Action? {

    if (node.type != NodeTypes.Assign) return null

    val callNode = node.asAssign().value

    if (callNode.type != NodeTypes.Call) return null
    val callInfo = callNode.asCall()
    if (callInfo.targetName != "createArray") return null
    if (callInfo.parameters.size != 1) return null

    val actions = mutableListOf<Action>()

    //size on stack
    actions.add(builder.getActionOnStack(callInfo.parameters[0], byteType) ?: return null)
    //Pop the size from the stack
    actions.add(PopRegister())
    //Size is now in a register
    //TODO: multiply with element size
    //Make space for array
    actions.add(AllocDynamicSpaceOnStack())

    //Push the size-value to the array struct
    actions.add(PushRegister())

    //Push stackpointer, this is the pointer to the new array
    actions.add(PushStackPointer())

    val address = pushAddress(node.asAssign().target, builder.currentFunction, builder)
    if (address.resultingType !is Pointer) return null
    if (address.resultingType.type != ArrayType(byteType))
        throw CompileError("Not supported yet")

    //Push address
    actions.add(address.action)

    //Load array position, which is at offset 1
    actions.add(LoadRegisterStackAddress(1))

    //Store address at array-pointer-position
    actions.add(StoreRegisterAtStackAddress(0))

    //Restore stack
    actions.add(RemoveSpaceOnStack(2))

    return CompositeAction(*actions.toTypedArray())
}


fun byteToStack(node: AstNode, type: DataType, builder: ActionBuilder): Action? {
    if (node.type != NodeTypes.Identifier && node.type != NodeTypes.MemberAccess && node.type != NodeTypes.MemberDeref && node.type != NodeTypes.ArrayAccess) return null

    if (type != byteType) return null

    val pushMemberAddress = pushAddressCheckType(node, builder.currentFunction, byteType, builder)

    return CompositeAction(
        pushMemberAddress,
        LoadRegisterStackAddressDeref(0),
        PopThrow(),
        PushRegister(),
    )
}


fun pushPointer(node: AstNode, type: DataType, builder: ActionBuilder): Action? {
    if (type !is Pointer) return null

    val addressResult = pushAddress(node, builder.currentFunction, builder)
    if (addressResult.resultingType != type) return null

    return CompositeAction(
        addressResult.action,
        LoadRegisterStackAddressDeref(0),
        PopThrow(),
        PushRegister(),
    )
}

fun fieldToPointer(node: AstNode, type: DataType, builder: ActionBuilder): Action? {
    if (type !is Pointer) return null

    val addressResult = pushAddress(node, builder.currentFunction, builder)
    if (addressResult.resultingType != type.type) return null

    return addressResult.action
}

typealias statementBuilder = (AstNode, ActionBuilder) -> Action?
typealias stackValueBuilder = (AstNode, DataType, ActionBuilder) -> Action?

private val statementBuilders = mutableListOf<Pair<String, statementBuilder>>(
    Pair("createArray", ::createArray),
    Pair("assignByteToFrame", ::assignFrameByte),
    Pair("callStatement", ::callStatement),
    Pair("notEqualCompare", ::notEqualCompare),

    Pair("print", ::printStatement)
)
private val stackBuilders = mutableListOf<Pair<String, stackValueBuilder>>(
    Pair("putByteOnStack", ::putByteOnStack),
    Pair("byteToStack", ::byteToStack),
    Pair("fieldToPointer", ::fieldToPointer),
    Pair("arithmeticToStack", ::arithmeticToStack),
    Pair("callToStack", ::callToStack),
    Pair("pushPointer", ::pushPointer),
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
        node: AstNode,
        type: DataType,
    ): Action? {
        for ((name, action) in stackBuilders) {
            action.invoke(node, type, this)?.also { return it }
        }
        return null
    }

    fun buildStatement(node: AstNode): Action {
        for ((name, action) in statementBuilders) {
            action.invoke(node, this)?.also { return it }
        }

        throw CompileError("Dont know how to build $node")
    }
}


