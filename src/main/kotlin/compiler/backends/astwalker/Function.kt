package se.wingez.compiler.backends.astwalker

import se.wingez.ast.AstNode
import se.wingez.astwalker.TypeProvider
import se.wingez.compiler.frontend.FunctionContent
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionDefinitionResolver
import se.wingez.compiler.frontend.flattenFunction

interface IWalkerFunction {
    val definition: FunctionDefinition
    fun execute(values: List<Value>, state: WalkerState): Value
}

class UserFunction(
    frontendFunction: FunctionContent
) : IWalkerFunction {
    override val definition: FunctionDefinition = frontendFunction.definition
    val codeBlock = frontendFunction.codeBlock

    override fun execute(values: List<Value>, state: WalkerState): Value {
        return state.walkUserFunction(this, values)
    }
}

fun definitionFromFuncNode(
    node: AstNode,
    typeProvider: TypeProvider,
    functionProvider: FunctionDefinitionResolver
): UserFunction {

    val function = flattenFunction(node, functionProvider)

    return UserFunction(function)
}