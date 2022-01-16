package se.wingez.compiler.actions

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.compiler.*


data class PushAddressResult(
    val action: Action,
    val resultingType: DataType,
)

private class AddressCalculator(
    val builder: ActionBuilder
) {

    // Start address is always relative to FP
    // Needs to change if we ever introduces global variables
    private val result = mutableListOf<Action>(LoadRegisterFP())


    // Recursive because we want to start with the innermost node
    private fun buildRecursive(currentNode: AstNode, providedType: DataType): DataType {

        when (currentNode.type) {
            NodeTypes.Identifier -> {
                return access(providedType, currentNode.asIdentifier().name)
            }

            NodeTypes.MemberAccess -> {
                val nextType = buildRecursive(currentNode.childNodes[0], providedType)
                return access(nextType, currentNode.data as String)
            }
            NodeTypes.MemberDeref -> {
                val nextType = buildRecursive(currentNode.childNodes[0], providedType)
                return deref(nextType, currentNode.data as String)
            }
            NodeTypes.ArrayAccess -> {
                val nextType = buildRecursive(currentNode.asArrayAccess().parent, providedType)
                return arrayAccess(nextType, currentNode.asArrayAccess().index)
            }
            else -> {
                throw CompileError("Cannot calculate address from node $currentNode")
            }
        }

    }

    private fun access(currentType: DataType, memberName: String): DataType {
        if (currentType !is StructType) {
            throw CompileError("Cannot access member of $currentType")
        }
        if (!currentType.hasField(memberName)) {
            throw CompileError("Type $currentType has no member $memberName")
        }

        val field = currentType.getField(memberName)

        result.add(AddRegister(field.offset))
        return field.type
    }

    private fun deref(currentType: DataType, memberName: String): DataType {
        if (currentType !is Pointer) {
            throw CompileError("Cannot deref non-pointer")
        }
        if (currentType.type !is FieldContainer) {
            throw CompileError("Cannot access member of $currentType")
        }

        if (!currentType.type.hasField(memberName)) {
            throw CompileError("Type $currentType has no member $memberName")
        }

        val field = currentType.type.getField(memberName)

        result.add(DerefByteAction(0u))
        result.add(AddRegister(field.offset))
        return field.type
    }

    private fun arrayAccess(currentType: DataType, indexNode: AstNode): DataType {
        // Deref and access index
        if (currentType !is Pointer) {
            throw CompileError("Cannot deref non-pointer")
        }
        if (currentType.type !is ArrayType) {
            throw CompileError("Cannot array-access of ${currentType.type}")
        }
        val arrayType = currentType.type.type

        if (arrayType != byteType) {
            throw CompileError("Only arrays of byte supported yet")
        }


        result.add(DerefByteAction(0u))
        result.add(PushRegister())

        val calculateIndex = builder.getActionOnStack(indexNode, byteType) ?: throw CompileError("BAd index")
        //TODO: multiply with size
        result.add(calculateIndex)

        result.add(PopRegister())
        // Add 1 to handle size-field of array

        result.add(AddRegister(1u))

        // Add index to previous value
        result.add(AdditionAction())
        // Pop previous value from stack. we should only store in A
        result.add(PopThrow())

        return arrayType
    }


    fun build(node: AstNode, topType: DataType): PushAddressResult {
        val resultingType = buildRecursive(node, topType)
        result.add(PushRegister())

        return PushAddressResult(CompositeAction(*result.toTypedArray()), resultingType)
    }


}


fun pushAddressCheckType(
    topNode: AstNode,
    function: FunctionInfo,
    expectedType: DataType,
    builder: ActionBuilder
): Action {

    val pushResult = pushAddress(topNode, function, builder)

    if (pushResult.resultingType != expectedType) {
        throw CompileError("Expected type to be $expectedType")
    }
    return pushResult.action
}

fun pushAddress(topNode: AstNode, function: FunctionInfo, builder: ActionBuilder): PushAddressResult {
    return AddressCalculator(builder).build(topNode, function)
}