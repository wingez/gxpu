package compiler.frontend

import ast.AstNode
import ast.FunctionType
import ast.NodeTypes


data class FunctionDefinition(
    val name: String,
    val parameters: List<Pair<String, Datatype>>,
    val returnType: Datatype,
    val functionType: FunctionType,

    ) {
    fun matches(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && functionType == this.functionType && parameterTypes == this.parameterTypes
    }

    val hasReturnType = returnType != Primitives.Nothing

    val parameterTypes get() = parameters.map { it.second }
    val parameterNames get() = parameters.map { it.first }
}


interface FunctionSignatureResolver {
    fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition
}

private fun parameters(functionNode: AstNode, typeProvider: TypeProvider): List<Pair<String, Datatype>> {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    return function.arguments.map {
        it.asNewVariable().name to typeProvider.requireType(it.asNewVariable().optionalTypeDefinition!!)
    }
}

fun definitionFromFunctionNode(functionNode: AstNode, typeProvider: TypeProvider): FunctionDefinition {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    val params = parameters(functionNode, typeProvider)

    val returnType =
        if (function.returnType != null) typeProvider.requireType(function.returnType) else Primitives.Nothing

    return FunctionDefinition(
        name = function.name,
        parameters = params,
        returnType = returnType,
        functionType = function.type,
    )
}

class DefinitionBuilder(val name: String) {
    private val parameters = mutableListOf<Pair<String, Datatype>>()
    private var returnType: Datatype = Primitives.Nothing
    private var functionType = FunctionType.Normal

    fun addParameter(name: String, type: Datatype): DefinitionBuilder {
        parameters.add(name to type)
        return this
    }

    fun setReturnType(type: Datatype): DefinitionBuilder {
        returnType = type
        return this
    }

    fun setFunctionType(type: FunctionType): DefinitionBuilder {
        functionType = type
        return this
    }

    fun getDefinition(): FunctionDefinition {
        return FunctionDefinition(name, parameters, returnType, functionType)
    }
}