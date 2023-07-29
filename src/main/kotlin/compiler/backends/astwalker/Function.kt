package compiler.backends.astwalker

import compiler.frontend.*

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
