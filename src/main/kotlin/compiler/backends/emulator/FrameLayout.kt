package compiler.backends.emulator

import compiler.frontend.Datatype
import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import se.wingez.ast.NodeTypes
import se.wingez.compiler.frontend.FunctionDefinition

const val POINTER_SIZE = 1

const val SP_STACK_SIZE = 1
const val PC_STACK_SIZE = 1
const val STACK_START = 100


class SignatureBuilder(val name: String) {
    private val parameters = mutableListOf<Datatype>()
    private var returnType: Datatype = Datatype.Void

    fun addParameter(type: Datatype): SignatureBuilder {
        parameters.add(type)
        return this
    }

    fun setReturnType(type: Datatype): SignatureBuilder {
        returnType = type
        return this
    }

    fun getSignature(): FunctionDefinition {
        return FunctionDefinition(name, parameters, returnType, FunctionType.Normal)
    }
}
