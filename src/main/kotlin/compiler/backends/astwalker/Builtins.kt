package compiler.backends.astwalker

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.BuiltInSourceFile
import compiler.frontend.FunctionDefinition
import compiler.frontend.Primitives

abstract class Function(
    override val definition: FunctionDefinition,
) : IWalkerFunction {

    override fun toString(): String {
        return "${definition.name}${definition.parameterTypes}: ${definition.returnType}"

    }
}

class BuiltInPrintInteger : Function(
    BuiltInSignatures.print,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        state.output.result.add(values[0].primitive.toString())
        return Value.nothing
    }
}

class BuiltInPrintString : Function(
    BuiltInSignatures.printString,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {

        val arrayPointer = values[0].pointer!!

        val size = arrayPointer.readField("size").pointer!!.getDeref().primitive

        val rawArray = arrayPointer.readField("array").pointer!!


        val chars = mutableListOf<Char>()

        for (i in 0 until size) {
            chars.add(rawArray.arrayIndex(i).pointer!!.getDeref().primitive.toChar())
        }

        state.output.result.add(chars.joinToString(""))
        return Value.nothing
    }
}


class IntegerComparator(
    functionName: String,
    private val compareFunction: (val1: Int, val2: Int) -> Boolean
) : Function(
    FunctionDefinition(
        functionName,
        BuiltInSourceFile,
        listOf("arg1" to Primitives.Integer, "arg2" to Primitives.Integer),
        Primitives.Boolean,
        FunctionType.Operator
    ),
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val value1 = values[0].primitive
        val value2 = values[1].primitive

        val result = when (compareFunction.invoke(value1, value2)) {
            true -> 1
            false -> 0
        }
        return Value(Primitives.Boolean, result)
    }
}

class IntegerArithmetic(
    functionName: String,
    functionType: FunctionType,
    private val arithmeticFunction: (val1: Int, val2: Int) -> Int
) : Function(
    FunctionDefinition(
        functionName,
        BuiltInSourceFile,
        listOf("arg1" to Primitives.Integer, "arg2" to Primitives.Integer),
        Primitives.Integer,
        functionType
    ),
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val value1 = values[0].primitive
        val value2 = values[1].primitive

        val result = arithmeticFunction.invoke(value1, value2)

        return Value(Primitives.Integer, primitive = result)
    }
}

class BoolConverter : Function(
    BuiltInSignatures.bool
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        if (values[0].primitive == 0)
            return Value(Primitives.Boolean, primitive = 0)
        return Value(Primitives.Boolean, primitive = 1)
    }
}

val modulus = IntegerArithmetic("mod", FunctionType.Normal) { val1, val2 ->
    val1 % val2
}

val integerDiv = IntegerArithmetic("idiv", FunctionType.Normal) { val1, val2 ->
    val1 / val2
}

class Run : Function(
    BuiltInSignatures.run
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val functionReference = values[0]
        require(functionReference.type is FunctionDefinition)

        val index = functionReference.primitive

        val toCall = state.availableFunctions[index]

        state.call(toCall, emptyList())

        return Value.nothing
    }
}

val builtInList = listOf(
    BuiltInPrintInteger(),
    BuiltInPrintString(),

    IntegerArithmetic(OperatorBuiltIns.Addition, FunctionType.Operator) { val1, val2 -> val1 + val2 },
    IntegerArithmetic(OperatorBuiltIns.Subtraction, FunctionType.Operator) { val1, val2 -> val1 - val2 },
    modulus,
    integerDiv,

    IntegerComparator(OperatorBuiltIns.Equal) { val1, val2 -> val1 == val2 },
    IntegerComparator(OperatorBuiltIns.NotEqual) { val1, val2 -> val1 != val2 },
    IntegerComparator(OperatorBuiltIns.LessThan) { val1, val2 -> val1 < val2 },
    IntegerComparator(OperatorBuiltIns.GreaterThan) { val1, val2 -> val1 > val2 },
    BoolConverter(),

    Run(),
)