package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.ast.OperatorBuiltIns

class WalkerException() : Exception()

class WalkerOutput() {

    val result = mutableListOf<String>()
}

class Datatype {

}

val DatatypeInteger = Datatype()
val DatatypeVoid = Datatype()

class Variable(
    val datatype: Datatype,
    val value: Int
) {

}


interface IFunction {
    val name: String
    val parameterTypes: List<Datatype>
    val returnType: Datatype

    fun execute(variables: List<Variable>, output: WalkerOutput): Variable
}

abstract class Function(
    override val name: String,
    override val parameterTypes: List<Datatype>,
    override val returnType: Datatype,
) : IFunction {

}

class BuiltInPrint : Function(
    "print", listOf(DatatypeInteger), DatatypeVoid
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        output.result.add(variables[0].value.toString())
        return Variable(DatatypeVoid, 0)
    }
}

class BuiltInAddition : Function(
    name, listOf(DatatypeInteger, DatatypeInteger), DatatypeInteger
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        return Variable(DatatypeInteger, variables[0].value + variables[1].value)
    }
    companion object {
        const val name = OperatorBuiltIns.Addition
    }
}

val builtInList = listOf(
    BuiltInPrint(),
    BuiltInAddition(),
)

fun walk(node: AstNode, output: WalkerOutput) {

    assert(node.type == NodeTypes.Function)

    for (child in node.childNodes) {

        when (child.type) {
            NodeTypes.Call -> {
                getValueOf(child, output)
            }
            else->throw WalkerException()
        }
    }
}

fun handleCall(node: AstNode, output: WalkerOutput): Variable {
    assert(node.type == NodeTypes.Call)
    val callNode = node.asCall()

    val arguments = callNode.parameters
        .map { getValueOf(it, output) }



    for (function in builtInList) {
        if (function.name != callNode.targetName) {
            continue
        }

        if (!matchesTheseArgumentsSignature(arguments, function.parameterTypes)) {
            continue
        }

        return function.execute(arguments, output)
    }

    throw WalkerException()
}

fun matchesTheseArgumentsSignature(arguments: List<Variable>, requiredParameters: List<Datatype>): Boolean {
    if (arguments.size != requiredParameters.size) {
        return false
    }

    for (i in requiredParameters.indices) {
        if (requiredParameters[i] != arguments[i].datatype) {
            return false
        }
    }
    return true
}


fun getValueOf(node: AstNode, output: WalkerOutput): Variable {

    return when (node.type) {
        NodeTypes.Constant -> Variable(DatatypeInteger, node.asConstant())
        NodeTypes.Call -> handleCall(node, output)

        else -> {
            throw WalkerException()
        }
    }

}




