package compiler

import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.frontend.*

class BuiltInSignatures : BuiltInCollection {
    companion object {
        val print = DefinitionBuilder("print")
            .addParameter("value", Primitives.Integer)
            .getDefinition()
        val printString = DefinitionBuilder("print")
            .addParameter("value", Primitives.Str)
            .getDefinition()


        val bool = DefinitionBuilder("bool")
            .addParameter("value", Primitives.Integer)
            .setReturnType(Primitives.Boolean)
            .getDefinition()

        val add = DefinitionBuilder(OperatorBuiltIns.Addition)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getDefinition()

        val sub = DefinitionBuilder(OperatorBuiltIns.Subtraction)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .setReturnType(Primitives.Integer)
            .setFunctionType(FunctionType.Operator)
            .getDefinition()

        val arraySize = DefinitionBuilder("size")
            .setFunctionType(FunctionType.Instance)
            .setReturnType(Primitives.Integer)
            .addParameter("array", Primitives.Integer.arrayPointerOf())
            .getDefinition()
        val createArray = DefinitionBuilder("createArray")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer.arrayPointerOf())
            .addParameter("size", Primitives.Integer)
            .getDefinition()
        val arrayRead = DefinitionBuilder(OperatorBuiltIns.ArrayRead)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Integer)
            .addParameter("array", Primitives.Integer.arrayPointerOf())
            .addParameter("index", Primitives.Integer)
            .getDefinition()
        val arrayWrite = DefinitionBuilder(OperatorBuiltIns.ArrayWrite)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Nothing)
            .addParameter("array", Primitives.Integer.arrayPointerOf())
            .addParameter("index", Primitives.Integer)
            .addParameter("elementSize", Primitives.Integer)
            .getDefinition()
        val notEquals = DefinitionBuilder(OperatorBuiltIns.NotEqual)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .getDefinition()
        val equals = DefinitionBuilder(OperatorBuiltIns.Equal)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .getDefinition()

        val lessThan = DefinitionBuilder(OperatorBuiltIns.LessThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .getDefinition()
        val greaterThan = DefinitionBuilder(OperatorBuiltIns.GreaterThan)
            .setFunctionType(FunctionType.Operator)
            .setReturnType(Primitives.Boolean)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .getDefinition()


        val mod = DefinitionBuilder("mod")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
            .getDefinition()
        val idiv = DefinitionBuilder("idiv")
            .setFunctionType(FunctionType.Normal)
            .setReturnType(Primitives.Integer)
            .addParameter("first", Primitives.Integer)
            .addParameter("second", Primitives.Integer)
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

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {


        if (name == "invoke" && functionType == FunctionType.Instance && parameterTypes.isNotEmpty()) {
            // Special case for invoke

            val toInvoke = parameterTypes.first()

            if (toInvoke is FunctionDefinitionDatatype) {
                val invokeDefinition = toInvoke.definition
                // Check arguments match
                if (invokeDefinition.signature.parameterTypes == parameterTypes.drop(1)) {
                    val returnType = invokeDefinition.signature.returnType

                    return FunctionDefinition(
                        FunctionSignature(name, parameterTypes, returnType, functionType),
                        parameterTypes.map { "invoke parameter" }
                    )
                }
            }
        }
        return FunctionCollection(functions).getFunctionDefinitionMatching(name, functionType, parameterTypes)
    }

    override fun getFunctionDefinitionMatchingName(name: String): FunctionDefinition {
        TODO("Not yet implemented")
    }


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