package compiler.frontend

import ast.AstNode
import ast.FunctionType
import ast.NodeTypes


data class FunctionSignature(
    val name: String,
    val parameterTypes: List<Datatype>,
    val returnType: Datatype,
    val functionType: FunctionType,
) {
    fun matches(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && functionType == this.functionType && parameterTypes == this.parameterTypes
    }
}

data class FunctionDefinition(
    val signature: FunctionSignature,
    val parameterNames: List<String>,
)


interface FunctionSignatureResolver {
    fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionSignature
}

private fun parameters(functionNode: AstNode, typeProvider: TypeProvider): Pair<List<String>, List<Datatype>> {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    return function.arguments.map {
        it.asNewVariable().name
    } to function.arguments.map {
        typeProvider.requireType(it.asNewVariable().optionalTypeDefinition!!)
    }
}

fun definitionFromFunctionNode(functionNode: AstNode, typeProvider: TypeProvider): FunctionDefinition {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    val (paramNames, paramTypes) = parameters(functionNode, typeProvider)

    val returnType = if (function.returnType != null) typeProvider.requireType(function.returnType) else Datatype.Void

    val signature = FunctionSignature(
        name = function.name,
        parameterTypes = paramTypes,
        returnType = returnType,
        functionType = function.type,
    )

    return FunctionDefinition(signature, paramNames)
}