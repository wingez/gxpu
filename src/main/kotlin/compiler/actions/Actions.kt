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



enum class MemberAccessAction {
    Access,
    Deref,
}

fun pushAddress(topNode: ValueNode, function: FunctionInfo, expectedType: DataType): Action {

    val result = mutableListOf<Action>(LoadRegisterFP())


    // Recursive because we want to start with the innermost node
    fun buildRecursive(currentNode: ValueNode, providedType: DataType): DataType {

        val member: String
        val currentType: DataType
        val action: MemberAccessAction

        when (currentNode) {
            is Identifier -> {
                member = currentNode.name
                currentType = providedType
                action = MemberAccessAction.Access
            }
            is MemberAccess -> {
                currentType = buildRecursive(currentNode.left, providedType)
                member = currentNode.member
                action = MemberAccessAction.Access
            }
            is MemberDeref -> {
                currentType = buildRecursive(currentNode.left, providedType)
                member = currentNode.member
                action = MemberAccessAction.Deref

            }
            else -> {
                throw CompileError("Cannot calculate address from node $currentNode")
            }
        }



        when (action) {
            MemberAccessAction.Access -> {
                if (currentType !is StructType) {
                    throw CompileError("Cannot access member of $currentType")
                }
                if (!currentType.hasField(member)) {
                    throw CompileError("Type $currentType has no member $member")
                }

                val field = currentType.getField(member)

                result.add(AddRegister(field.offset))
                return field.type
            }
            MemberAccessAction.Deref -> {
                if (currentType !is Pointer) {
                    throw CompileError("Cannot deref non-pointer")
                }
                if (currentType.type !is StructType) {
                    throw CompileError("Cannot access member of $currentType")
                }

                if (!currentType.type.hasField(member)) {
                    throw CompileError("Type $currentType has no member $member")
                }

                val field = currentType.type.getField(member)

                result.add(DerefByteAction(0u))
                result.add(AddRegister(field.offset))
                return field.type
            }
        }
    }

    val resultingType = buildRecursive(topNode, function)
    if (resultingType != expectedType) {
        throw CompileError("Expected type to be $expectedType")
    }

    result.add(PushRegister())
    return CompositeAction(*result.toTypedArray())
}




class AssignFrameByte : ActionConverter {


    override fun buildStatement(node: StatementNode, builder: ActionBuilder): Action? {
        if (node !is AssignNode)
            return null
        if (node.value == null) {
            return null
        }

        val pushMemberAddress = pushAddress(node.target, builder.currentFunction, byteType)

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

class ByteToStack : ActionConverter {
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

        val pushMemberAddress = pushAddress(node, builder.currentFunction, byteType)

        return CompositeAction(
            pushMemberAddress,
            LoadRegisterStackAddress(0u),
            PopThrow(),
            PushRegister(),
        )
    }
}

class FieldToPointer : ActionConverter {
    override fun putOnStack(node: ValueNode, type: DataType, builder: ActionBuilder): Action? {
        if (type !is Pointer) return null

        return pushAddress(node, builder.currentFunction, type.type)
    }
}

val actions = listOf(
    Print(),
    PutByteOnStack(),
    AssignFrameByte(),
    ByteToStack(),
    AdditionProvider(),
    SubtractionProvider(),

    NotEqualProvider(),
    CallProvider(),
    FieldToPointer(),
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


