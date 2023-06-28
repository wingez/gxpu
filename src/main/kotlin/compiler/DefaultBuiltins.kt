package compiler

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.CompositeDataTypeField
import compiler.frontend.Datatype
import compiler.frontend.SignatureBuilder

class BuiltInSignatures : BuiltInCollection {
    companion object {
        val print = SignatureBuilder("print")
            .addParameter(Datatype.Integer)
            .getSignature()
        val printString=SignatureBuilder("print")
            .addParameter(Datatype.Str)
            .getSignature()


        val bool = SignatureBuilder("bool")
            .addParameter(Datatype.Integer)
            .setReturnType(Datatype.Boolean)
            .getSignature()

        val add = SignatureBuilder(OperatorBuiltIns.Addition)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .setReturnType(Datatype.Integer)
            .setFunctionType(FunctionType.Operator)
            .getSignature()

        val sub = SignatureBuilder(OperatorBuiltIns.Subtraction)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .setReturnType(Datatype.Integer)
            .setFunctionType(FunctionType.Operator)
            .getSignature()

        val arraySize = SignatureBuilder("size")
            .setFunctionType(FunctionType.Instance)
            .setReturnType(Datatype.Integer)
            .addParameter(Datatype.ArrayPointer(Datatype.Integer))
            .getSignature()
        val createArray = SignatureBuilder("createArray")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Datatype.ArrayPointer(Datatype.Integer))
            .addParameter(Datatype.Integer)
            .getSignature()
        val arrayRead = SignatureBuilder(OperatorBuiltIns.ArrayRead)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Datatype.Integer)
            .addParameter(Datatype.ArrayPointer(Datatype.Integer))
            .addParameter(Datatype.Integer)
            .getSignature()
        val arrayWrite = SignatureBuilder(OperatorBuiltIns.ArrayWrite)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Datatype.Void)
            .addParameter(Datatype.ArrayPointer(Datatype.Integer))
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .getSignature()
        val notEquals = SignatureBuilder(OperatorBuiltIns.NotEqual)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Datatype.Boolean)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .getSignature()
        val equals = SignatureBuilder(OperatorBuiltIns.Equal)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Datatype.Boolean)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .getSignature()

        val lessThan = SignatureBuilder(OperatorBuiltIns.LessThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Datatype.Boolean)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .getSignature()
        val greaterThan = SignatureBuilder(OperatorBuiltIns.GreaterThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Datatype.Boolean)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .getSignature()


        val mod = SignatureBuilder("mod")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .getSignature()
        val idiv = SignatureBuilder("idiv")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Datatype.Integer)
            .addParameter(Datatype.Integer)
            .addParameter(Datatype.Integer)
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
        Datatype.Integer, Datatype.Void,
        //"byte" to Datatype.Integer,
        Datatype.Composite(
            "intpair",
            listOf(
                CompositeDataTypeField("first", Datatype.Integer),
                CompositeDataTypeField("second", Datatype.Integer)
            )
        )
    )
}