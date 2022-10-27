package se.wingez.astwalker

import se.wingez.ast.OperatorBuiltIns

abstract class Function(
    name: String,
    parameterTypes: List<Datatype>,
    returnType: Datatype,
) : IFunction {
    override val definition = FunctionDefinition(name, parameterTypes, returnType)
}

class DerefArray : Function(
    "deref", listOf(Datatype.ArrayPointer(Datatype.Integer)), Datatype.Array(Datatype.Integer)
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        return values[0].derefPointer().value
    }
}

class BuiltInPrintInteger : Function(
    "print", listOf(Datatype.Integer), Datatype.Void
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        state.output.result.add(values[0].getPrimitiveValue().toString())
        return Value.void()
    }
}

class BuiltInPrintString : Function(
    "print", listOf(Datatype.ArrayPointer(Datatype.Integer)), Datatype.Void
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {

        val array = values[0].derefPointer().value

        val arraySize = array.getFieldValueHolder("size").value.getPrimitiveValue()

        var result = ""
        for (i in 0 until arraySize) {
            result += Char(array.arrayAccess(i).value.getPrimitiveValue())
        }
        state.output.result.add(result)
        return Value.void()
    }
}

class IntegerComparator(
    functionName: String,
    private val compareFunction: (val1: Int, val2: Int) -> Boolean
) : Function(
    functionName, listOf(Datatype.Integer, Datatype.Integer), Datatype.Boolean
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
    private val arithmeticFunction: (val1: Int, val2: Int) -> Int
) : Function(
    functionName, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val value1 = values[0].getPrimitiveValue()
        val value2 = values[1].getPrimitiveValue()

        val result = arithmeticFunction.invoke(value1, value2)

        return Value.primitive(Datatype.Integer, result)
    }
}


class BuiltInCreateArray : Function(
    "createArray", listOf(Datatype.Integer), Datatype.ArrayPointer(Datatype.Integer)
) {
    override fun execute(values: List<Value>, state: WalkerState): Value {
        val size = values[0].getPrimitiveValue()
        val array = Value.array(Datatype.Array(Datatype.Integer), size)

        val holder = ValueHolder(array.datatype)
        holder.value = array

        return Value.pointer(holder)
    }
}

val modulus = IntegerArithmetic("mod") { val1, val2 ->
    val1 % val2
}

val integerDiv = IntegerArithmetic("idiv") { val1, val2 ->
    val1 / val2
}


val builtInList = listOf(
    BuiltInPrintInteger(),
    BuiltInPrintString(),

    IntegerArithmetic(OperatorBuiltIns.Addition) { val1, val2 -> val1 + val2 },
    IntegerArithmetic(OperatorBuiltIns.Subtraction) { val1, val2 -> val1 - val2 },
    modulus,
    integerDiv,

    IntegerComparator(OperatorBuiltIns.Equal) { val1, val2 -> val1 == val2 },
    IntegerComparator(OperatorBuiltIns.NotEqual) { val1, val2 -> val1 != val2 },
    IntegerComparator(OperatorBuiltIns.LessThan) { val1, val2 -> val1 < val2 },
    IntegerComparator(OperatorBuiltIns.GreaterThan) { val1, val2 -> val1 > val2 },

    BuiltInCreateArray(),

    DerefArray(),
)