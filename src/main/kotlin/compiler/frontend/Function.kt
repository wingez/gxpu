package se.wingez.compiler.frontend

import se.wingez.ast.FunctionType
import se.wingez.astwalker.Datatype


data class FunctionDefinition(
    val name: String,
    val parameterTypes: List<Datatype>,
    val returnType: Datatype,
    val functionType: FunctionType,
) {
    fun matches(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && functionType == this.functionType && parameterTypes == this.parameterTypes
    }
}

interface IFunction {
    val definition: FunctionDefinition
}

interface FunctionProvider<T : IFunction> {
    fun getFunctionMatching(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): T
}
