package compiler.backends.astwalker

import ast.AstNode
import compiler.frontend.*

interface IWalkerFunction {
    val signature: FunctionSignature
    fun execute(values: List<Value>, state: WalkerState): Value
}

class UserFunction(
    val functionContent: FunctionContent
) : IWalkerFunction {
    override val signature: FunctionSignature = functionContent.definition.signature
    val code = functionContent.code

    override fun execute(values: List<Value>, state: WalkerState): Value {
        return state.walkUserFunction(this, values)
    }
}
