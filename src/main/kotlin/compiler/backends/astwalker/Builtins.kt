package compiler.backends.astwalker

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.BuiltInSourceFile
import compiler.frontend.FunctionDatatype
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
        state.output.result.add(values[0].asPrimitive.integer.toString())
        return Value.nothing
    }
}

class BuiltInPrintString : Function(
    BuiltInSignatures.printString,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {

        val arrayView = values[0].asPrimitive.pointer

        val chars = mutableListOf<Char>()

        for (i in 0 until arrayView.arraySize()) {
            chars.add(arrayView.arrayRead(i).getPrimitiveValue().integer.toChar())
        }

        state.output.result.add(chars.joinToString(""))
        return Value.nothing
    }
}

class BuiltInArraySize : Function(
    BuiltInSignatures.arraySize,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val arrayView = values[0].asPrimitive.pointer
        return Value.primitive(Primitives.Integer, arrayView.arraySize())
    }
}

class BuiltInArrayRead : Function(
    BuiltInSignatures.arrayRead
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val arrayView = values[0].asPrimitive.pointer
        val index = values[1].asPrimitive.integer

        val arraySize = arrayView.arraySize()
        if (index !in 0 until arraySize) {
            throw WalkerException("trying to read at index $index which is outside array bounds($arraySize)")
        }

        return arrayView.arrayRead(index).getValue()
    }
}

class BuiltInArrayWrite : Function(
    BuiltInSignatures.arrayWrite
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {

        val arrayView = values[0].asPrimitive.pointer
        val index = values[1].asPrimitive.integer

        arrayView.arrayRead(index).applyValue(values[2])
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
        val value1 = values[0].asPrimitive.integer
        val value2 = values[1].asPrimitive.integer

        val result = when (compareFunction.invoke(value1, value2)) {
            true -> 1
            false -> 0
        }
        return Value.primitive(Primitives.Boolean, result)
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
        val value1 = values[0].asPrimitive.integer
        val value2 = values[1].asPrimitive.integer

        val result = arithmeticFunction.invoke(value1, value2)

        return Value.primitive(Primitives.Integer, result)
    }
}


class BuiltInCreateArray : Function(
    BuiltInSignatures.createArray
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val size = values[0].asPrimitive.integer
        return createArray(Primitives.Integer, size)
    }
}

class BoolConverter : Function(
    BuiltInSignatures.bool
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        return Value(Primitives.Boolean, values[0].primitives)
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
        require(functionReference.datatype is FunctionDatatype)

        val index = functionReference.asPrimitive.integer

        val toCall = state.availableFunctions[index]

        state.call(toCall, emptyList())

        return Value.nothing
    }
}

val builtInList = listOf(
    BuiltInPrintInteger(),
    BuiltInPrintString(),
    BuiltInArraySize(),
    BuiltInArrayRead(),
    BuiltInArrayWrite(),

    IntegerArithmetic(OperatorBuiltIns.Addition, FunctionType.Operator) { val1, val2 -> val1 + val2 },
    IntegerArithmetic(OperatorBuiltIns.Subtraction, FunctionType.Operator) { val1, val2 -> val1 - val2 },
    modulus,
    integerDiv,

    IntegerComparator(OperatorBuiltIns.Equal) { val1, val2 -> val1 == val2 },
    IntegerComparator(OperatorBuiltIns.NotEqual) { val1, val2 -> val1 != val2 },
    IntegerComparator(OperatorBuiltIns.LessThan) { val1, val2 -> val1 < val2 },
    IntegerComparator(OperatorBuiltIns.GreaterThan) { val1, val2 -> val1 > val2 },
    BoolConverter(),

    BuiltInCreateArray(),

    Run(),
)