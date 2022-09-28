package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.ast.OperatorBuiltIns

class WalkerException(msg: String = "") : Exception(msg)

class WalkerOutput() {

    val result = mutableListOf<String>()
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
    "print", listOf(Datatype.Integer), Datatype.Void
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        output.result.add(variables[0].getPrimitiveValue().toString())
        return Variable(Datatype.Void)
    }
}

class BuiltInAddition : Function(
    name, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        return Variable(Datatype.Integer, variables[0].getPrimitiveValue() + variables[1].getPrimitiveValue())
    }

    companion object {
        const val name = OperatorBuiltIns.Addition
    }
}

class BuiltInNotEqual : Function(
    OperatorBuiltIns.NotEqual, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        if (variables[0].getPrimitiveValue() != variables[1].getPrimitiveValue()) {
            return Variable(Datatype.Integer, 1)
        } else {
            return Variable(Datatype.Integer, 0)
        }
    }
}

val builtInList = listOf(
    BuiltInPrint(),
    BuiltInAddition(),
    BuiltInNotEqual(),
)

class WalkFrame {

    val variables = mutableMapOf<String, Variable>()

}

fun walk(node: AstNode): WalkerOutput {
    return walk(listOf(node))
}

fun walk(nodes: List<AstNode>): WalkerOutput {

    val output = WalkerOutput()
    for (node in nodes) {
        assert(node.type == NodeTypes.Function || node.type == NodeTypes.Struct)

        if (node.type == NodeTypes.Struct) {

        }

        if (node.type == NodeTypes.Function) {

            val frame = WalkFrame()

            for (child in node.childNodes) {
                walkRecursive(child, frame, output)
            }
        }
    }

    return output
}

private fun walkRecursive(node: AstNode, frame: WalkFrame, output: WalkerOutput) {


    when (node.type) {
        NodeTypes.Call -> {
            getValueOf(node, frame, output)
        }

        NodeTypes.Assign -> {
            handleAssign(node, frame, output)
        }

        NodeTypes.If -> {
            handleIf(node, frame, output)
        }

        NodeTypes.While -> {
            handleWhile(node, frame, output)
        }

        else -> throw WalkerException()
    }
}

fun handleWhile(node: AstNode, frame: WalkFrame, output: WalkerOutput) {
    assert(node.type == NodeTypes.While)
    val whileNode = node.asWhile()

    while (true) {
        val conditionResult = getValueOf(whileNode.condition, frame, output)
        if (conditionResult.datatype != Datatype.Integer) {
            throw WalkerException("Expect conditional to be of integer type")
        }

        if (conditionResult.getPrimitiveValue() != 1) {
            break
        }

        for (child in whileNode.body) {
            walkRecursive(child, frame, output)
        }
    }
}

private fun handleIf(node: AstNode, frame: WalkFrame, output: WalkerOutput) {
    assert(node.type == NodeTypes.If)
    val ifNode = node.asIf()

    val conditionResult = getValueOf(ifNode.condition, frame, output)

    if (conditionResult.datatype != Datatype.Integer) {
        throw WalkerException("Expect conditional to be of integer type")
    }

    if (conditionResult.getPrimitiveValue() != 0) {
        for (child in ifNode.ifBody) {
            walkRecursive(child, frame, output)
        }
    } else {
        if (ifNode.hasElse) {
            for (child in ifNode.elseBody) {
                walkRecursive(child, frame, output)
            }
        }
    }
}

private fun handleAssign(
    child: AstNode,
    frame: WalkFrame,
    output: WalkerOutput
) {
    val assignNode = child.asAssign()

    assert(assignNode.target.type == NodeTypes.Identifier)

    val assignName = assignNode.target.asIdentifier()
    val isNewAssign = assignName !in frame.variables

    val valueToAssign = getValueOf(assignNode.value, frame, output)

    if (!isNewAssign) {
        // Check type match
        if (frame.variables.getValue(assignName).datatype != valueToAssign.datatype) {
            throw WalkerException()
        }
    }
    frame.variables[assignName] = valueToAssign
}

fun handleCall(node: AstNode, frame: WalkFrame, output: WalkerOutput): Variable {
    assert(node.type == NodeTypes.Call)
    val callNode = node.asCall()

    val arguments = callNode.parameters
        .map { getValueOf(it, frame, output) }



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


fun getValueOf(node: AstNode, frame: WalkFrame, output: WalkerOutput): Variable {

    return when (node.type) {
        NodeTypes.Constant -> Variable(Datatype.Integer, node.asConstant())
        NodeTypes.Call -> handleCall(node, frame, output)
        NodeTypes.Identifier -> {
            val variableName = node.asIdentifier()
            if (variableName !in frame.variables) {
                throw WalkerException("No variable named $variableName")
            }

            frame.variables.getValue(variableName)
        }

        else -> {
            throw WalkerException()
        }
    }
}

