package se.wingez.compiler.backends.astwalker

import se.wingez.ast.AstNode
import se.wingez.astwalker.TypeProvider
import se.wingez.compiler.flattener.Function
import se.wingez.compiler.flattener.flattenFunction
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.FunctionProvider
import se.wingez.compiler.frontend.IFunction

interface IWalkerFunction : IFunction {
    fun execute(values: List<Value>, state: WalkerState): Value
}

class UserFunction(
    private val frontendFunction: Function
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
    functionProvider: FunctionProvider<IWalkerFunction>
): UserFunction {

    val function = flattenFunction(node, functionProvider)

    return UserFunction(function)
}