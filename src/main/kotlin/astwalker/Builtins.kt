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

class BuiltInAddition : Function(
    OperatorBuiltIns.Addition, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        return Variable.primitive(Datatype.Integer, variables[0].getPrimitiveValue() + variables[1].getPrimitiveValue())
    }
}

class BuiltInSubtraction : Function(
    OperatorBuiltIns.Subtraction, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        return Variable.primitive(Datatype.Integer, variables[0].getPrimitiveValue() - variables[1].getPrimitiveValue())
    }
}

class BuiltInNotEqual : Function(
    OperatorBuiltIns.NotEqual, listOf(Datatype.Integer, Datatype.Integer), Datatype.Boolean
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        if (variables[0].getPrimitiveValue() != variables[1].getPrimitiveValue()) {
            return Variable.primitive(Datatype.Boolean, 1)
        } else {
            return Variable.primitive(Datatype.Boolean, 0)
        }
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

val builtInList = listOf(
    BuiltInPrintInteger(),
    BuiltInPrintString(),
    BuiltInAddition(),
    BuiltInSubtraction(),
    BuiltInNotEqual(),
    BuiltInCreateArray(),
)