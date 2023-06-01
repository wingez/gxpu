package se.wingez.compiler.backends.astwalker

import se.wingez.ast.AstNode
import compiler.frontend.TypeProvider
import se.wingez.compiler.frontend.FunctionContent
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionDefinitionResolver
import se.wingez.compiler.frontend.compileFunction

interface IWalkerFunction {
    val definition: FunctionDefinition
    fun execute(values: List<Value>, state: WalkerState): Value
}

class UserFunction(
    val functionContent: FunctionContent
) : IWalkerFunction {
    override val definition: FunctionDefinition = functionContent.definition
    val code = functionContent.code

    override fun execute(values: List<Value>, state: WalkerState): Value {
        return state.walkUserFunction(this, values)
    }
}

fun definitionFromFuncNode(
    node: AstNode,
    typeProvider: TypeProvider,
    functionProvider: FunctionDefinitionResolver
): UserFunction {

    val function = compileFunction(node, functionProvider, typeProvider)

    return UserFunction(function)
}