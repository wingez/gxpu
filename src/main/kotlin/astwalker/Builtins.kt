package se.wingez.astwalker

import se.wingez.ast.OperatorBuiltIns


abstract class Function(
    name: String,
    parameterTypes: List<Datatype>,
    returnType: Datatype,
) : IFunction {
    override val definition = FunctionDefinition(name, parameterTypes, returnType)
}

class BuiltInPrint : Function(
    "print", listOf(Datatype.Integer), Datatype.Void
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        state.output.result.add(variables[0].getPrimitiveValue().toString())
        return Variable(Datatype.Void)
    }
}

class BuiltInAddition : Function(
    OperatorBuiltIns.Addition, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        return Variable(Datatype.Integer, variables[0].getPrimitiveValue() + variables[1].getPrimitiveValue())
    }
}

class BuiltInSubtraction : Function(
    OperatorBuiltIns.Subtraction, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        return Variable(Datatype.Integer, variables[0].getPrimitiveValue() - variables[1].getPrimitiveValue())
    }
}

class BuiltInNotEqual : Function(
    OperatorBuiltIns.NotEqual, listOf(Datatype.Integer, Datatype.Integer), Datatype.Boolean
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        if (variables[0].getPrimitiveValue() != variables[1].getPrimitiveValue()) {
            return Variable(Datatype.Boolean, 1)
        } else {
            return Variable(Datatype.Boolean, 0)
        }
    }
}

class BuiltInCreateArray : Function(
    "createArray", listOf(Datatype.Integer), Datatype.Integer.toArray()
) {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        val size = variables[0].getPrimitiveValue()
        return Variable(definition.returnType, size, size)
    }
}

val builtInList = listOf(
    BuiltInPrint(),
    BuiltInAddition(),
    BuiltInSubtraction(),
    BuiltInNotEqual(),
    BuiltInCreateArray(),
)