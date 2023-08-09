package compiler

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.*

const val BuiltInSourceFile = "builtins"

fun DefinitionBuilder.getBuiltInDefinition():FunctionDefinition{
    this.setSourceFile(BuiltInSourceFile)
    return this.getDefinition()
}

class BuiltInSignatures : BuiltInCollection {
    companion object {
        val print = DefinitionBuilder("print")
            .addParameter("value", Primitives.Integer)
            .getBuiltInDefinition()
        val printString = DefinitionBuilder("print")
            .addParameter("str", Primitives.Str)
            .getBuiltInDefinition()


        val bool = DefinitionBuilder("bool")
            .addParameter("value", Primitives.Integer)
            .setReturnType(Primitives.Boolean)
            .getBuiltInDefinition()

        val add = DefinitionBuilder(OperatorBuiltIns.Addition)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getBuiltInDefinition()

        val sub = DefinitionBuilder(OperatorBuiltIns.Subtraction)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getBuiltInDefinition()

        val arraySize = DefinitionBuilder("size")
            .setFunctionType(FunctionType.Instance)
            .setReturnType(Primitives.Integer)
            .addParameter("array", Primitives.Integer.arrayPointerOf())
            .getBuiltInDefinition()
        val createArray = DefinitionBuilder("createArray")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer.arrayPointerOf())
            .addParameter("size", Primitives.Integer)
            .getBuiltInDefinition()
        val arrayRead = DefinitionBuilder(OperatorBuiltIns.ArrayRead)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Integer)
            .addParameter("array", Primitives.Integer.arrayPointerOf())
            .addParameter("index", Primitives.Integer)
            .getBuiltInDefinition()
        val arrayWrite = DefinitionBuilder(OperatorBuiltIns.ArrayWrite)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Nothing)
            .addParameter("array", Primitives.Integer.arrayPointerOf())
            .addParameter("index", Primitives.Integer)
            .addParameter("value", Primitives.Integer)
            .getBuiltInDefinition()
        val notEquals = DefinitionBuilder(OperatorBuiltIns.NotEqual)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .getBuiltInDefinition()
        val equals = DefinitionBuilder(OperatorBuiltIns.Equal)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .getBuiltInDefinition()

        val lessThan = DefinitionBuilder(OperatorBuiltIns.LessThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .getBuiltInDefinition()
        val greaterThan = DefinitionBuilder(OperatorBuiltIns.GreaterThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .getBuiltInDefinition()


        val mod = DefinitionBuilder("mod")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .getBuiltInDefinition()
        val idiv = DefinitionBuilder("idiv")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter("arg1", Primitives.Integer)
            .addParameter("arg2", Primitives.Integer)
            .getBuiltInDefinition()

        val run = DefinitionBuilder("run")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Nothing)
            .addParameter("function", FunctionDatatype(Signature(emptyList(), Primitives.Nothing)))
            .getBuiltInDefinition()
    }

    override val functions = listOf(
        print,
        printString,
        bool,
        add,
        sub,
        arraySize,
        createArray,
        arrayRead,
        arrayWrite,
        notEquals,
        equals,
        lessThan,
        greaterThan,
        mod,
        idiv,
        run,
    )

    override val types = listOf(
        Primitives.Integer, Primitives.Nothing,
        //"byte" to Primitives.Integer,
        CompositeDatatype(
            "intpair",
            listOf(
                CompositeDataTypeField("first", Primitives.Integer),
                CompositeDataTypeField("second", Primitives.Integer)
            )
        )
    )
}