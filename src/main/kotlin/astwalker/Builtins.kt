package se.wingez.astwalker

import se.wingez.ast.OperatorBuiltIns

abstract class Function(
    name: String,
    parameterTypes: List<Datatype>,
    returnType: Datatype,
) : IFunction {
    override val definition = FunctionDefinition(name, parameterTypes, returnType)
}

class BuiltInPrintInteger : Function(
    "print", listOf(Datatype.Integer), Datatype.Void
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        state.output.result.add(variables[0].getPrimitiveValue().toString())
        return Variable.void()
    }
}

class BuiltInPrintString : Function(
    "print", listOf(Datatype.Array(Datatype.Integer)), Datatype.Void
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        val arraySize = variables.first().getField("size").getPrimitiveValue()

        var result = ""
        for (i in 0 until arraySize) {
            result += Char(variables.first().arrayAccess(i).getPrimitiveValue())
        }
        state.output.result.add(result)
        return Variable.void()
    }
}

class IntegerComparator(
    functionName: String,
    private val compareFunction: (val1: Int, val2: Int) -> Boolean
) : Function(
    functionName, listOf(Datatype.Integer, Datatype.Integer), Datatype.Boolean
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        val value1 = variables[0].getPrimitiveValue()
        val value2 = variables[1].getPrimitiveValue()

        val result = when (compareFunction.invoke(value1, value2)) {
            true -> 1
            false -> 0
        }
        return Variable.primitive(Datatype.Boolean, result)
    }
}

class IntegerArithmetic(
    functionName: String,
    private val arithmeticFunction: (val1: Int, val2: Int) -> Int
) : Function(
    functionName, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        val value1 = variables[0].getPrimitiveValue()
        val value2 = variables[1].getPrimitiveValue()

        val result = arithmeticFunction.invoke(value1, value2)

        return Variable.primitive(Datatype.Integer, result)
    }
}


class BuiltInCreateArray : Function(
    "createArray", listOf(Datatype.Integer), Datatype.Array(Datatype.Integer)
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        val size = variables[0].getPrimitiveValue()
        return Variable.array(definition.returnType, size)
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
)