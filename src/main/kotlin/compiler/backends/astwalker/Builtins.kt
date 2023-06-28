package compiler.backends.astwalker

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.frontend.Datatype
import compiler.frontend.FunctionSignature

abstract class Function(
    override val signature: FunctionSignature,
) : IWalkerFunction {

    override fun toString(): String {
        return "${signature.name}${signature.parameterTypes}: ${signature.returnType}"

    }
}

class BuiltInPrintInteger : Function(
    BuiltInSignatures.print,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        state.output.result.add(values[0].getPrimitiveValue().toString())
        return Value.void()
    }
}

class BuiltInPrintString : Function(
    BuiltInSignatures.printString,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {

        val array = values[0].derefPointer().asValue()

        val arraySize = array.arraySize

        var result = ""
        for (i in 0 until arraySize) {
            result += Char(array.arrayHolderAt(i).value.getPrimitiveValue())
        }
        state.output.result.add(result)
        return Value.void()
    }
}

class BuiltInArraySize : Function(
    BuiltInSignatures.arraySize,
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val array = values[0].derefPointer().asValue()

        val arraySize = array.arraySize
        return Value.primitive(Datatype.Integer, arraySize)
    }
}

class BuiltInArrayRead : Function(
    BuiltInSignatures.arrayRead
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val array = values[0].derefPointer().asValue()

        val index = values[1].getPrimitiveValue()

        return array.arrayHolderAt(index).value
    }
}

class BuiltInArrayWrite : Function(
    BuiltInSignatures.arrayWrite
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val array = values[0].derefPointer().asValue()

        val index = values[1].getPrimitiveValue()

        val value = values[2]

        array.arrayHolderAt(index).value = value

        return Value.void()
    }
}

class IntegerComparator(
    functionName: String,
    private val compareFunction: (val1: Int, val2: Int) -> Boolean
) : Function(
    FunctionSignature(
        functionName,
        listOf(Datatype.Integer, Datatype.Integer),
        Datatype.Boolean,
        FunctionType.Operator
    ),
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val value1 = values[0].getPrimitiveValue()
        val value2 = values[1].getPrimitiveValue()

        val result = when (compareFunction.invoke(value1, value2)) {
            true -> 1
            false -> 0
        }
        return Value.primitive(Datatype.Boolean, result)
    }
}

class IntegerArithmetic(
    functionName: String,
    functionType: FunctionType,
    private val arithmeticFunction: (val1: Int, val2: Int) -> Int
) : Function(
    FunctionSignature(functionName, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer, functionType),
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val value1 = values[0].getPrimitiveValue()
        val value2 = values[1].getPrimitiveValue()

        val result = arithmeticFunction.invoke(value1, value2)

        return Value.primitive(Datatype.Integer, result)
    }
}


class BuiltInCreateArray : Function(
    BuiltInSignatures.createArray
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val size = values[0].getPrimitiveValue()

        val holders = (0 until size).map {
            PrimitiveValueHolder(Datatype.Integer).apply {
                value = Value.primitive(Datatype.Integer, 0)
            }
        }


        val array = Value.array(Datatype.Array(Datatype.Integer), holders)

        val holder = PrimitiveValueHolder(array.datatype)
        holder.value = array

        return Value.pointer(array.datatype, CompositeValueHolder(array.datatype, emptyMap(), holder))
    }
}

class BoolConverter : Function(
    BuiltInSignatures.bool
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val primitive = values[0].getPrimitiveValue()

        return Value.primitive(Datatype.Boolean, primitive)
    }
}

val modulus = IntegerArithmetic("mod", FunctionType.Normal) { val1, val2 ->
    val1 % val2
}

val integerDiv = IntegerArithmetic("idiv", FunctionType.Normal) { val1, val2 ->
    val1 / val2
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
)