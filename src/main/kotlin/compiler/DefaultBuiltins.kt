package compiler

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.*

class BuiltInSignatures : BuiltInCollection {
    companion object {
        val print = DefinitionBuilder("print")
            .addParameter("value", Primitives.Integer)
            .getDefinition()
        val printString=DefinitionBuilder("print")
            .addParameter("str",Primitives.Str)
            .getDefinition()


        val bool = DefinitionBuilder("bool")
            .addParameter("value",Primitives.Integer)
            .setReturnType(Primitives.Boolean)
            .getDefinition()

        val add = DefinitionBuilder(OperatorBuiltIns.Addition)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getDefinition()

        val sub = DefinitionBuilder(OperatorBuiltIns.Subtraction)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getDefinition()

        val arraySize = DefinitionBuilder("size")
            .setFunctionType(FunctionType.Instance)
            .setReturnType(Primitives.Integer)
            .addParameter("array",Primitives.Integer.arrayPointerOf())
            .getDefinition()
        val createArray = DefinitionBuilder("createArray")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer.arrayPointerOf())
            .addParameter("size",Primitives.Integer)
            .getDefinition()
        val arrayRead = DefinitionBuilder(OperatorBuiltIns.ArrayRead)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Integer)
            .addParameter("array",Primitives.Integer.arrayPointerOf())
            .addParameter("index",Primitives.Integer)
            .getDefinition()
        val arrayWrite = DefinitionBuilder(OperatorBuiltIns.ArrayWrite)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Nothing)
            .addParameter("array",Primitives.Integer.arrayPointerOf())
            .addParameter("index",Primitives.Integer)
            .addParameter("value",Primitives.Integer)
            .getDefinition()
        val notEquals = DefinitionBuilder(OperatorBuiltIns.NotEqual)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .getDefinition()
        val equals = DefinitionBuilder(OperatorBuiltIns.Equal)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .getDefinition()

        val lessThan = DefinitionBuilder(OperatorBuiltIns.LessThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .getDefinition()
        val greaterThan = DefinitionBuilder(OperatorBuiltIns.GreaterThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .getDefinition()


        val mod = DefinitionBuilder("mod")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .getDefinition()
        val idiv = DefinitionBuilder("idiv")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter("arg1",Primitives.Integer)
            .addParameter("arg2",Primitives.Integer)
            .getDefinition()


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