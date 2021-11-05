package se.wingez.compiler.actions

import se.wingez.ast.Identifier
import se.wingez.ast.MemberAccess
import se.wingez.ast.MemberDeref
import se.wingez.ast.ValueNode
import se.wingez.compiler.*

enum class MemberAccessAction {
    Access,
    Deref,
}

data class PushAddressResult(
    val action: Action,
    val resultingType: DataType,
)

fun pushAddressCheckType(topNode: ValueNode, function: FunctionInfo, expectedType: DataType): Action {

    val pushResult = pushAddress(topNode, function)

    if (pushResult.resultingType != expectedType) {
        throw CompileError("Expected type to be $expectedType")
    }
    return pushResult.action
}

fun pushAddress(topNode: ValueNode, function: FunctionInfo): PushAddressResult {

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
                if (currentType.type !is FieldContainer) {
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

    result.add(PushRegister())

    return PushAddressResult(CompositeAction(*result.toTypedArray()), resultingType)
}