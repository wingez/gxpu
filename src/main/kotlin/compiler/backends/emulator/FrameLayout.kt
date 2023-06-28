package compiler.backends.emulator

import compiler.frontend.Datatype
import ast.FunctionType
import compiler.frontend.FunctionSignature

const val STACK_START = 100


class SignatureBuilder(val name: String) {
    private val parameters = mutableListOf<Datatype>()
    private var returnType: Datatype = Datatype.Void
    private var functionType = FunctionType.Normal

    fun addParameter(type: Datatype): SignatureBuilder {
        parameters.add(type)
        return this
    }

    fun setReturnType(type: Datatype): SignatureBuilder {
        returnType = type
        return this
    }

    fun setFunctionType(type: FunctionType): SignatureBuilder {
        functionType = type
        return this
    }

    fun getSignature(): FunctionSignature {
        return FunctionSignature(name, parameters, returnType, functionType)
    }
}
