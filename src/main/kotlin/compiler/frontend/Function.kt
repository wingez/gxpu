package se.wingez.compiler.frontend

import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import se.wingez.ast.NodeTypes


data class FunctionDefinition(
    val name: String,
    val parameterTypes: List<Datatype>,
    val returnType: Datatype,
    val functionType: FunctionType,
) {
    fun matches(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && functionType == this.functionType && parameterTypes == this.parameterTypes
    }

    companion object {
        fun fromFunctionNode(node: AstNode, typeProvider: TypeProvider) = definitionFromFunctionNode(node, typeProvider)
    }
}

interface FunctionDefinitionResolver {
    fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition
}

fun parameterTypes(functionNode: AstNode, typeProvider: TypeProvider): List<Pair<String, Datatype>> {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    return function.arguments.map {
        assert(it.type == NodeTypes.NewVariable)
        val type = typeProvider.getType(it.asNewVariable().optionalTypeDefinition!!)

        it.asNewVariable().name to type
    }
}

private fun definitionFromFunctionNode(functionNode: AstNode, typeProvider: TypeProvider): FunctionDefinition {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    val parameters = parameterTypes(functionNode, typeProvider).map { it.second }

    val returnType = if (function.returnType != null) typeProvider.getType(function.returnType) else Datatype.Void

    return FunctionDefinition(
        name = function.name,
        parameterTypes = parameters,
        returnType = returnType,
        functionType = function.type,
    )
}