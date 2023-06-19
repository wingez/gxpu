package compiler.backends.astwalker

import ast.AstNode
import compiler.frontend.TypeProvider
import compiler.frontend.FunctionContent
import compiler.frontend.FunctionDefinition
import compiler.frontend.FunctionDefinitionResolver
import compiler.frontend.compileFunction

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