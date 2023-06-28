package compiler.backends.emulator

import compiler.BuiltInSignatures
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*

interface FunctionContext : CodeGenerator {
    val fieldLayout: LayedOutDatatype
}

val builtinInlinedSignatures = listOf(
    BuiltInSignatures.print,
    BuiltInSignatures.bool,
    BuiltInSignatures.arraySize,
    BuiltInSignatures.createArray,
    BuiltInSignatures.arrayRead,
    BuiltInSignatures.arrayWrite,
    BuiltInSignatures.notEquals,
    BuiltInSignatures.equals,
    BuiltInSignatures.lessThan,
)


enum class WhereToPutResult {
    A,
    TopStack,
    Flag,
}

fun tryGetValueWhere(expr: ValueExpression, where: WhereToPutResult, context: FunctionContext): GetValueResult {

    return when (expr) {
        is ConstantExpression -> {
            context.addInstruction(
                when (where) {
                    WhereToPutResult.A -> emulate(DefaultEmulator.lda_constant, "val" to expr.value)
                    WhereToPutResult.TopStack -> emulate(DefaultEmulator.push, "val" to expr.value)
                    else -> TODO()
                }
            )
            DynamicValue(where)
        }

        is VariableExpression -> {

            val field = context.fieldLayout.getField(expr.variable.name)
            FpField(field)
        }

        is CallExpression -> {
            handleCall(expr, where, context)
            DynamicValue(where)
        }

        is AddressOf -> {

            val addressType = getAddressOf(expr.value, context)
            when (addressType) {

                is FpField -> {
                    val field = addressType.field
                    context.addInstruction(
                        emulate(DefaultEmulator.lda_fp_offset, "offset" to field.offset)
                    )
                    when (where) {
                        WhereToPutResult.A -> {
                            //OK
                        }

                        WhereToPutResult.TopStack -> {
                            context.addInstruction(
                                emulate(DefaultEmulator.pusha)
                            )
                        }

                        else -> TODO()
                    }

                }

                else -> TODO()
            }
            DynamicValue(where)
        }

        is DerefToValue -> {
            val address = getAddressOf(expr.value, context)

            when (address) {

                is FpField -> {
                    val field = address.field
                    context.addInstruction(
                        //TODO instruction without offset??
                        emulate(DefaultEmulator.lda_at_fp_offset, "offset" to field.offset)
                    )
                }

                else -> TODO(address.toString())
            }

            DynamicPointerValue(WhereToPutResult.A)
        }

        is ValueMemberAccess -> {
            val valueResult = tryGetValueWhere(expr.of, WhereToPutResult.TopStack, context)
            when (valueResult) {
                is FpField -> {
                    val existingField = LayedOutStruct(valueResult.field.type).getField(expr.memberName)
                    FpField(existingField.copy(offset = existingField.offset + valueResult.field.offset))
                }
                is DynamicPointerValue -> {
                    val offset = LayedOutStruct(expr.of.type).getField(expr.memberName).offset
                    context.addInstruction(emulate(DefaultEmulator.adda, "val" to offset))
                    DynamicPointerValue(WhereToPutResult.A)
                }
                else -> TODO(valueResult.toString())
            }

        }

        else -> TODO(expr.toString())
    }
}

fun requireGetValueIn(expr: ValueExpression, where: WhereToPutResult, context: FunctionContext) {
    val resultPlace = tryGetValueWhere(expr, where, context)

    when (resultPlace) {
        is DynamicValue -> {
            require(resultPlace.where == where)
        }

        is FpField -> {
            context.addInstruction(
                when (where) {
                    WhereToPutResult.A -> emulate(
                        DefaultEmulator.lda_at_fp_offset,
                        "offset" to resultPlace.field.offset
                    )

                    WhereToPutResult.TopStack -> emulate(
                        DefaultEmulator.push_fp_offset, "offset" to resultPlace.field.offset
                    )

                    else -> TODO()
                }
            )
        }

        is DynamicPointerValue -> {
            require(resultPlace.where==WhereToPutResult.A)

            context.addInstruction(
                //TODO instruction without offset??
                emulate(DefaultEmulator.lda_at_a_offset, "offset" to 0)
            )
            when (where) {
                WhereToPutResult.A -> {
                    // OK!
                }

                WhereToPutResult.TopStack -> {
                    context.addInstruction(
                        emulate(DefaultEmulator.pusha)
                    )
                }

                else -> TODO()
            }
            DynamicValue(where)
        }

        else -> TODO()
    }

}

private fun handleGenericCall(expr: CallExpression, where: WhereToPutResult, context: FunctionContext) {

    //Maks space for result variable
    if (expr.function.returnType != Datatype.Void) {
        context.addInstruction(
            emulate(
                DefaultEmulator.add_sp,
                "val" to sizeOf(expr.function.returnType)
            )
        )
    }

    for (parameterExpr in expr.parameters) {
        requireGetValueIn(parameterExpr, WhereToPutResult.TopStack, context)
    }

    context.addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(expr.function, functionEntryLabel)))

    // pop arguments if neccesary
    val argumentSize = expr.parameters.sumOf { sizeOf(it.type) }
    if (argumentSize > 0) {
        context.addInstruction(emulate(DefaultEmulator.sub_sp, "val" to argumentSize))
    }

    // Pop value from stack if required

    if (where == WhereToPutResult.A) {
        val retType = expr.function.returnType

        if (retType != Datatype.Void) {

            if (retType != Datatype.Integer && retType != Datatype.Boolean) {
                TODO(expr.function.returnType.toString())
            }
            context.addInstruction(emulate(DefaultEmulator.popa))
        }

    }
}

fun handleCall(expr: CallExpression, where: WhereToPutResult, context: FunctionContext) {

    val whereWasValueActuallyPut: WhereToPutResult = when (expr.function) {
        BuiltInSignatures.print -> {
            requireGetValueIn(expr.parameters[0], WhereToPutResult.A, context)
            context.addInstruction(emulate(DefaultEmulator.print))
            WhereToPutResult.A
        }

        BuiltInSignatures.bool -> {
            // Do nothing in this case. Conversation is implicit
            when (where) {
                WhereToPutResult.Flag -> {
                    requireGetValueIn(expr.parameters.first(), WhereToPutResult.A, context)
                    context.addInstruction(emulate(DefaultEmulator.test_nz_a))
                    WhereToPutResult.Flag
                }

                else -> {
                    requireGetValueIn(expr.parameters.first(), where, context)
                    where
                }
            }
        }


        BuiltInSignatures.arraySize -> {
            requireGetValueIn(expr.parameters.first(), WhereToPutResult.A, context)
            context.addInstruction(emulate(DefaultEmulator.lda_at_a_offset, "offset" to 0))
            WhereToPutResult.A
        }

        BuiltInSignatures.createArray -> {
            requireGetValueIn(expr.parameters.first(), WhereToPutResult.TopStack, context)
            context.addInstruction(emulate(DefaultEmulator.lda_sp_offset, "offset" to -1))
            context.addInstruction(emulate(DefaultEmulator.addsp_at_sp_offset, "offset" to -1))
            WhereToPutResult.A
        }

        BuiltInSignatures.arrayRead -> {
            // Array pointeraddress
            requireGetValueIn(expr.parameters[0], WhereToPutResult.TopStack, context)
            // Array offset
            // TODO: mul type size
            requireGetValueIn(expr.parameters[1], WhereToPutResult.A, context)

            context.addInstruction(emulate(DefaultEmulator.pop_adda))
            // Add one to get adapt to size location
            context.addInstruction(emulate(DefaultEmulator.adda, "val" to 1))
            // Deref
            context.addInstruction(emulate(DefaultEmulator.lda_at_a_offset, "offset" to 0))

            WhereToPutResult.A
        }

        BuiltInSignatures.arrayWrite -> {
            // Value
            requireGetValueIn(expr.parameters[2], WhereToPutResult.TopStack, context)
            // Array pointer
            requireGetValueIn(expr.parameters[0], WhereToPutResult.TopStack, context)
            // index
            // TODO: type size
            requireGetValueIn(expr.parameters[1], WhereToPutResult.A, context)

            context.addInstruction(emulate(DefaultEmulator.pop_adda))

            // add 1 to since array size is stored at index 1
            context.addInstruction(emulate(DefaultEmulator.pop_at_a_offset, "offset" to 1))

            WhereToPutResult.A
        }

        BuiltInSignatures.notEquals -> {
            when (where) {
                WhereToPutResult.Flag -> {
                    // Try to do the test directly here

                    requireGetValueIn(
                        CallExpression(ByteSubtraction().signature, expr.parameters),
                        WhereToPutResult.A,
                        context
                    )
                    context.addInstruction(emulate(DefaultEmulator.test_nz_a))
                    WhereToPutResult.Flag
                }

                else -> {
                    // Otherwise just implicit convert from int to bool
                    requireGetValueIn(CallExpression(ByteSubtraction().signature, expr.parameters), where, context)
                    where
                }
            }
        }

        BuiltInSignatures.equals -> {
            when (where) {
                // try to inline the test
                WhereToPutResult.Flag -> {
                    requireGetValueIn(
                        CallExpression(ByteSubtraction().signature, expr.parameters),
                        WhereToPutResult.A,
                        context
                    )
                    context.addInstruction(emulate(DefaultEmulator.test_z_a))
                    WhereToPutResult.Flag
                }

                else -> {
                    // We need to implicit convert from int to bool but invert the boolean value of the subtraction result
                    requireGetValueIn(
                        CallExpression(ByteSubtraction().signature, expr.parameters),
                        WhereToPutResult.A,
                        context
                    )
                    context.addInstruction(emulate(DefaultEmulator.log_inv_a))
                    WhereToPutResult.A
                }
            }
        }

        BuiltInSignatures.lessThan -> {
            when (where) {
                // try to inline the test
                WhereToPutResult.Flag -> {
                    requireGetValueIn(
                        CallExpression(ByteSubtraction().signature, expr.parameters),
                        WhereToPutResult.A,
                        context
                    )
                    context.addInstruction(emulate(DefaultEmulator.test_neg_a))
                    WhereToPutResult.Flag
                }

                else -> {
                    TODO()
                }
            }
        }


        else -> {
            handleGenericCall(expr, where, context)
            where
        }
    }

    // take case of cases where value was not put correctly already
    if (where != whereWasValueActuallyPut) {

        when (whereWasValueActuallyPut to where) {
            WhereToPutResult.A to WhereToPutResult.TopStack -> {
                context.addInstruction(emulate(DefaultEmulator.pusha))
            }

            else -> TODO((whereWasValueActuallyPut to where).toString())
        }
    }
}

interface GetValueResult

class DynamicValue(
    val where: WhereToPutResult
) : GetValueResult

class DynamicPointerValue(
    val where: WhereToPutResult
): GetValueResult

interface GetAddressResult

class FpField(
    val field: StructDataField,
) : GetAddressResult, GetValueResult

class DynamicAddress(
    val instructions: List<EmulatorInstruction>,
) : GetAddressResult {
    val where: WhereToPutResult = WhereToPutResult.A

}

fun getAddressOf(expr: AddressExpression, context: FunctionContext): GetAddressResult {

    return when (expr) {
        is VariableExpression -> {
            val field = context.fieldLayout.getField(expr.variable.name)
            FpField(field)
        }

        is DerefToAddress -> {
            if (expr.value !is VariableExpression) {
                TODO()
            }
            val field = context.fieldLayout.getField(expr.value.variable.name)

            val instructions = listOf(
                emulate(DefaultEmulator.lda_at_fp_offset, "offset" to field.offset)
            )

            DynamicAddress(instructions)
        }
        is AddressMemberAccess -> {
            val valueResult = getAddressOf(expr.of, context)
            when (valueResult) {
                is FpField -> {
                    val existingField = LayedOutStruct(valueResult.field.type).getField(expr.memberName)
                    FpField(existingField.copy(offset = existingField.offset + valueResult.field.offset))
                }
                is DynamicAddress -> {
                    require(valueResult.where==WhereToPutResult.A)

                    val offset = LayedOutStruct(expr.of.type).getField(expr.memberName).offset

                    val instructions = valueResult.instructions + listOf(
                        emulate(DefaultEmulator.adda, "val" to offset)
                    )

                    DynamicAddress(instructions)
                }

                else -> TODO(valueResult.toString())
            }
        }

        else -> TODO(expr.toString())
    }
}


