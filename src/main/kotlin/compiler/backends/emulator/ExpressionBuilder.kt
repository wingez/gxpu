package se.wingez.compiler.backends.emulator

import compiler.backends.emulator.*
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import se.wingez.compiler.frontend.*

interface FunctionContext : CodeGenerator {
    fun getField(name: String): StructDataField
    val datatypeLayoutProvider: DatatypeLayoutProvider
}


private val BuiltInSignatures = object {
    val print = SignatureBuilder("print")
        .addParameter(Datatype.Integer)
        .getSignature()
}
val builtinInlinedSignatures = listOf(
    BuiltInSignatures.print
)


private enum class WhereToPutResult {
    A,
    TopStack,
}

fun putOnStack(expr: ValueExpression, context: FunctionContext) {
    getValue(expr, WhereToPutResult.TopStack, context)
}

private fun getValue(expr: ValueExpression, where: WhereToPutResult, context: FunctionContext) {

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
            assert(expr.type == Datatype.Integer)

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
            handleCall(expr, context)
            if (where == WhereToPutResult.A) {
                assert(expr.function.returnType == Datatype.Integer)
                context.addInstruction(emulate(DefaultEmulator.popa))
            }
        }

        else -> TODO(expr.toString())
    }
}

private fun handleGenericCall(expr: CallExpression, context: FunctionContext) {

    //TODO: extract a generic way to inline stuff like this
    if (expr.function == Bool().signature) {
        putOnStack(expr.parameters.first(), context)
        //Do nothing in this case. Conversation is implicit
        return
    }

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
        putOnStack(parameterExpr, context)
    }

    context.addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(expr.function, functionEntryLabel)))

    // pop arguments if neccesary
    val argumentSize = expr.parameters.sumOf { context.datatypeLayoutProvider.sizeOf(it.type) }
    if (argumentSize > 0) {
        context.addInstruction(emulate(DefaultEmulator.sub_sp, "val" to argumentSize))
    }

}

fun handleCall(expr: CallExpression, context: FunctionContext) {

    if (expr.function == BuiltInSignatures.print) {
        getValue(expr.parameters[0], WhereToPutResult.A, context)
        context.addInstruction(emulate(DefaultEmulator.print))
    } else {
        handleGenericCall(expr, context)
    }
}


