package se.wingez.compiler.backends.emulator

import ast.expression.OperatorBuiltIns
import compiler.backends.emulator.*
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import se.wingez.ast.FunctionType
import se.wingez.compiler.frontend.*

interface FunctionContext : CodeGenerator {
    fun getField(name: String): StructDataField
    val datatypeLayoutProvider: DatatypeLayoutProvider
}


private val BuiltInSignatures = object {
    val print = SignatureBuilder("print")
        .addParameter(Datatype.Integer)
        .getSignature()
    val bool = SignatureBuilder("bool")
        .addParameter(Datatype.Integer)
        .setReturnType(Datatype.Boolean)
        .getSignature()
    val arraySize = SignatureBuilder("size")
        .setFunctionType(FunctionType.Instance)
        .setReturnType(Datatype.Integer)
        .addParameter(Datatype.ArrayPointer(Datatype.Integer))
        .getSignature()
    val createArray = SignatureBuilder("createArray")
        .setFunctionType(FunctionType.Normal)
        .setReturnType(Datatype.ArrayPointer(Datatype.Integer))
        .addParameter(Datatype.Integer)
        .getSignature()
    val arrayRead = SignatureBuilder(OperatorBuiltIns.ArrayRead)
        .setFunctionType(FunctionType.Operator)
        .setReturnType(Datatype.Integer)
        .addParameter(Datatype.ArrayPointer(Datatype.Integer))
        .addParameter(Datatype.Integer)
        .getSignature()
    val arrayWrite = SignatureBuilder(OperatorBuiltIns.ArrayWrite)
        .setFunctionType(FunctionType.Operator)
        .setReturnType(Datatype.Void)
        .addParameter(Datatype.ArrayPointer(Datatype.Integer))
        .addParameter(Datatype.Integer)
        .addParameter(Datatype.Integer)
        .getSignature()
    val notEquals = SignatureBuilder(OperatorBuiltIns.NotEqual)
        .setFunctionType(FunctionType.Operator)
        .setReturnType(Datatype.Boolean)
        .addParameter(Datatype.Integer)
        .addParameter(Datatype.Integer)
        .getSignature()
    val equals = SignatureBuilder(OperatorBuiltIns.NotEqual)
        .setFunctionType(FunctionType.Operator)
        .setReturnType(Datatype.Boolean)
        .addParameter(Datatype.Integer)
        .addParameter(Datatype.Integer)
        .getSignature()
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
)


enum class WhereToPutResult {
    A,
    TopStack,
}

fun getValue(expr: ValueExpression, where: WhereToPutResult, context: FunctionContext) {

    when (expr) {
        is ConstantExpression -> {
            context.addInstruction(
                when (where) {
                    WhereToPutResult.A -> emulate(DefaultEmulator.lda_constant, "val" to expr.value)
                    WhereToPutResult.TopStack -> emulate(DefaultEmulator.push, "val" to expr.value)
                }
            )
        }

        is VariableExpression -> {
            assert(expr.type == Datatype.Integer || expr.type.isPointer())

            val field = context.getField(expr.variable.name)

            context.addInstruction(
                when (where) {
                    WhereToPutResult.A -> emulate(DefaultEmulator.lda_at_fp_offset, "offset" to field.offset)
                    WhereToPutResult.TopStack -> emulate(
                        DefaultEmulator.push_fp_offset, "offset" to field.offset
                    )
                }
            )
        }

        is CallExpression -> {
            handleCall(expr, where, context)
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

            // Address is now in a
            // Now dereference it


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
        }

        else -> TODO(expr.toString())
    }
}

private fun handleGenericCall(expr: CallExpression, where: WhereToPutResult, context: FunctionContext) {

    //Maks space for result variable
    if (expr.function.returnType != Datatype.Void) {
        context.addInstruction(
            emulate(
                DefaultEmulator.add_sp,
                "val" to context.datatypeLayoutProvider.sizeOf(expr.function.returnType)
            )
        )
    }

    for (parameterExpr in expr.parameters) {
        getValue(parameterExpr, WhereToPutResult.TopStack, context)
    }

    context.addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(expr.function, functionEntryLabel)))

    // pop arguments if neccesary
    val argumentSize = expr.parameters.sumOf { context.datatypeLayoutProvider.sizeOf(it.type) }
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
            getValue(expr.parameters[0], WhereToPutResult.A, context)
            context.addInstruction(emulate(DefaultEmulator.print))
            WhereToPutResult.A
        }

        BuiltInSignatures.bool -> {
            // Do nothing in this case. Conversation is implicit
            getValue(expr.parameters.first(), where, context)
            where
        }


        BuiltInSignatures.arraySize -> {
            getValue(expr.parameters.first(), WhereToPutResult.A, context)
            context.addInstruction(emulate(DefaultEmulator.lda_at_a_offset, "offset" to 0))
            WhereToPutResult.A
        }

        BuiltInSignatures.createArray -> {
            getValue(expr.parameters.first(), WhereToPutResult.TopStack, context)
            context.addInstruction(emulate(DefaultEmulator.lda_sp_offset, "offset" to -1))
            context.addInstruction(emulate(DefaultEmulator.addsp_at_sp_offset, "offset" to -1))
            WhereToPutResult.A
        }

        BuiltInSignatures.arrayRead -> {
            // Array pointeraddress
            getValue(expr.parameters[0], WhereToPutResult.TopStack, context)
            // Array offset
            // TODO: mul type size
            getValue(expr.parameters[1], WhereToPutResult.A, context)

            context.addInstruction(emulate(DefaultEmulator.pop_adda))
            // Add one to get adapt to size location
            context.addInstruction(emulate(DefaultEmulator.adda, "val" to 1))
            // Deref
            context.addInstruction(emulate(DefaultEmulator.lda_at_a_offset, "offset" to 0))

            WhereToPutResult.A
        }

        BuiltInSignatures.arrayWrite -> {
            // Value
            getValue(expr.parameters[2], WhereToPutResult.TopStack, context)
            // Array pointer
            getValue(expr.parameters[0], WhereToPutResult.TopStack, context)
            // index
            // TODO: type size
            getValue(expr.parameters[1], WhereToPutResult.A, context)

            context.addInstruction(emulate(DefaultEmulator.pop_adda))

            // add 1 to since array size is stored at index 1
            context.addInstruction(emulate(DefaultEmulator.pop_at_a_offset, "offset" to 1))

            WhereToPutResult.A
        }

        BuiltInSignatures.notEquals -> {
            // Implicit just convert from int to bool
            getValue(CallExpression(ByteSubtraction().signature, expr.parameters), where, context)
            where
        }

        BuiltInSignatures.equals -> {
            // Implicit just convert from int to bool
            getValue(CallExpression(ByteSubtraction().signature, expr.parameters), WhereToPutResult.A, context)
            // Invert it
            context.addInstruction(emulate(DefaultEmulator.log_inv_a))
            WhereToPutResult.A
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

interface GetAddressResult {

}

class FpField(
    val field: StructDataField,
) : GetAddressResult

class DynamicAddress(
    val instructions: List<EmulatorInstruction>,
) : GetAddressResult {
    val where: WhereToPutResult = WhereToPutResult.A

}

fun getAddressOf(expr: AddressExpression, context: FunctionContext): GetAddressResult {

    return when (expr) {
        is VariableExpression -> {
            val field = context.getField(expr.variable.name)
            FpField(field)
        }

        is DerefToAddress -> {
            if (expr.value !is VariableExpression) {
                TODO()
            }
            val field = context.getField(expr.value.variable.name)

            val instructions = listOf(
                emulate(DefaultEmulator.lda_at_fp_offset, "offset" to field.offset)
            )

            DynamicAddress(instructions)


        }

        else -> TODO(expr.toString())
    }
}


