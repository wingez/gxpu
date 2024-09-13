package compiler.frontend

import ast.AstNode
import ast.FunctionType
import ast.NodeTypes

data class FunctionDefinition(
    val functionName: String,
    val sourceFile: String,
    val parameters: List<Pair<String, Datatype>>,
    val returnType: Datatype,
    val functionType: FunctionType,

    ) : Datatype {
    fun matches(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return name == this.functionName && functionType == this.functionType && parameterTypes == this.parameterTypes
    }

    val hasReturnType = returnType != Primitives.Nothing

    val parameterTypes get() = parameters.map { it.second }
    val parameterNames get() = parameters.map { it.first }

    override val name: String
        get() = generateSignatureName()

    private fun generateSignatureName(): String {
        val paramdescip = parameters.map { "${it.first}: ${it.second.name}" }
        val params = paramdescip.joinToString ( ", " )
        return "fun $functionName($params): ${returnType.name}"

    }

    override fun toString(): String {
        return name
    }
}


private fun parameters(functionNode: AstNode, symbolTable: SymbolTable): List<Pair<String, Datatype>> {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    return function.arguments.map {
        it.asNewVariable().name to requireTypeFromTypeDefinition(it.asNewVariable().optionalTypeDefinition!!, symbolTable)
    }
}

fun definitionFromFunctionNode(
    functionNode: AstNode,
    sourceFile: String,
    symbolTable: SymbolTable,
): FunctionDefinition {
    assert(functionNode.type == NodeTypes.Function)

    val function = functionNode.asFunction()

    val params = parameters(functionNode, symbolTable)

    val returnType =
        if (function.returnType != null) requireTypeFromTypeDefinition(function.returnType, symbolTable) else Primitives.Nothing

    return FunctionDefinition(
        functionName = function.name,
        sourceFile = sourceFile,
        parameters = params,
        returnType = returnType,
        functionType = function.type,
    )
}

class DefinitionBuilder(val name: String) {
    private val parameters = mutableListOf<Pair<String, Datatype>>()
    private var returnType: Datatype = Primitives.Nothing
    private var functionType = FunctionType.Normal
    private var sourceFile: String? = null

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

    fun setSourceFile(filename: String): DefinitionBuilder {
        sourceFile = filename
        return this
    }

    fun getDefinition(): FunctionDefinition {
        require(sourceFile != null)
        return FunctionDefinition(name, sourceFile!!, parameters, returnType, functionType)
    }
}