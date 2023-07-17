package compiler

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.*

class BuiltInSignatures : BuiltInCollection {
    companion object {
        val print = SignatureBuilder("print")
            .addParameter(Primitives.Integer)
            .getSignature()
        val printString=SignatureBuilder("print")
            .addParameter(Primitives.Str)
            .getSignature()


        val bool = SignatureBuilder("bool")
            .addParameter(Primitives.Integer)
            .setReturnType(Primitives.Boolean)
            .getSignature()

        val add = SignatureBuilder(OperatorBuiltIns.Addition)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getSignature()

        val sub = SignatureBuilder(OperatorBuiltIns.Subtraction)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getSignature()

        val arraySize = SignatureBuilder("size")
            .setFunctionType(FunctionType.Instance)
            .setReturnType(Primitives.Integer)
            .addParameter(Primitives.Integer.arrayPointerOf())
            .getSignature()
        val createArray = SignatureBuilder("createArray")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer.arrayPointerOf())
            .addParameter(Primitives.Integer)
            .getSignature()
        val arrayRead = SignatureBuilder(OperatorBuiltIns.ArrayRead)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Integer)
            .addParameter(Primitives.Integer.arrayPointerOf())
            .addParameter(Primitives.Integer)
            .getSignature()
        val arrayWrite = SignatureBuilder(OperatorBuiltIns.ArrayWrite)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Nothing)
            .addParameter(Primitives.Integer.arrayPointerOf())
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()
        val notEquals = SignatureBuilder(OperatorBuiltIns.NotEqual)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()
        val equals = SignatureBuilder(OperatorBuiltIns.Equal)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()

        val lessThan = SignatureBuilder(OperatorBuiltIns.LessThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()
        val greaterThan = SignatureBuilder(OperatorBuiltIns.GreaterThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()


        val mod = SignatureBuilder("mod")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()
        val idiv = SignatureBuilder("idiv")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .addParameter(Primitives.Integer)
            .getSignature()


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