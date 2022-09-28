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
    OperatorBuiltIns.Addition, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        return Variable(Datatype.Integer, variables[0].getPrimitiveValue() + variables[1].getPrimitiveValue())
    }
}

class BuiltInSubtraction : Function(
    OperatorBuiltIns.Subtraction, listOf(Datatype.Integer, Datatype.Integer), Datatype.Integer
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        return Variable(Datatype.Integer, variables[0].getPrimitiveValue() - variables[1].getPrimitiveValue())
    }
}

class BuiltInNotEqual : Function(
    OperatorBuiltIns.NotEqual, listOf(Datatype.Integer, Datatype.Integer), Datatype.Boolean
) {
    override fun execute(variables: List<Variable>, output: WalkerOutput): Variable {
        if (variables[0].getPrimitiveValue() != variables[1].getPrimitiveValue()) {
            return Variable(Datatype.Boolean, 1)
        } else {
            return Variable(Datatype.Boolean, 0)
        }
    }
}

val builtInList = listOf(
    BuiltInPrint(),
    BuiltInAddition(),
    BuiltInSubtraction(),
    BuiltInNotEqual(),
)

class WalkFrame {

    val variables = mutableMapOf<String, Variable>()

}

fun walk(node: AstNode): WalkerOutput {
    return walk(listOf(node))
}

fun walk(nodes: List<AstNode>): WalkerOutput {


    val walker = Walker()
    walker.walk(nodes)
    return walker.output

}


private class Walker {

    val output = WalkerOutput()
    val frame = WalkFrame()

    val types = mutableMapOf(
        "int" to Datatype.Integer
    )

    fun walk(nodes: List<AstNode>): WalkerOutput {

        val output = WalkerOutput()
        for (node in nodes) {
            assert(node.type == NodeTypes.Function || node.type == NodeTypes.Struct)

            when (node.type) {
                NodeTypes.Struct -> {
                    val newType = createTypeFromNode(node, types)
                    if (newType.name in types) {
                        throw WalkerException()
                    }
                    types[newType.name] = newType
                }

                NodeTypes.Function -> {
                    for (child in node.childNodes) {
                        walkRecursive(child)
                    }
                }

                else -> {
                    assert(false)
                }
            }
        }

        return output
    }

    private fun walkRecursive(node: AstNode) {


        when (node.type) {
            NodeTypes.Call -> {
                getValueOf(node)
            }

            NodeTypes.Assign -> {
                handleAssign(node)
            }

            NodeTypes.If -> {
                handleIf(node)
            }

            NodeTypes.While -> {
                handleWhile(node)
            }

            NodeTypes.MemberDeclaration -> {
                handleMemberDeclaration(node)
            }

            else -> throw WalkerException()
        }
    }

    fun handleMemberDeclaration(node: AstNode) {
        val memberDef = node.asMemberDeclaration()

        val name = memberDef.name
        assert(name !in frame.variables)

        frame.variables[name] = createDefaultVariable(types.getValue(memberDef.type))
    }

    fun handleWhile(node: AstNode) {
        assert(node.type == NodeTypes.While)
        val whileNode = node.asWhile()

        while (true) {
            val conditionResult = getValueOf(whileNode.condition)
            if (conditionResult.datatype != Datatype.Boolean) {
                throw WalkerException("Expect conditional to be of boolean type")
            }

            if (conditionResult.getPrimitiveValue() != 1) {
                break
            }

            for (child in whileNode.body) {
                walkRecursive(child)
            }
        }
    }


    private fun handleIf(node: AstNode) {
        assert(node.type == NodeTypes.If)
        val ifNode = node.asIf()

        val conditionResult = getValueOf(ifNode.condition)

        if (conditionResult.datatype != Datatype.Boolean) {
            throw WalkerException("Expect conditional to be of bool type")
        }

        if (conditionResult.getPrimitiveValue() != 0) {
            for (child in ifNode.ifBody) {
                walkRecursive(child)
            }
        } else {
            if (ifNode.hasElse) {
                for (child in ifNode.elseBody) {
                    walkRecursive(child)
                }
            }
        }
    }

    private fun handleAssign(child: AstNode) {
        val assignNode = child.asAssign()

        val valueToAssign = getValueOf(assignNode.value)



        if (assignNode.target.type == NodeTypes.Identifier) {
            val assignName = assignNode.target.asIdentifier()
            val isNewAssign = assignName !in frame.variables
            if (isNewAssign) {
                frame.variables[assignName] = valueToAssign
                return
            }

        }

        val variableToAssignTo = getValueOf(assignNode.target)

        if (valueToAssign.datatype != variableToAssignTo.datatype) {
            throw WalkerException("Type mismatch")
        }

        variableToAssignTo.copyFrom(valueToAssign)
    }

    fun handleCall(node: AstNode): Variable {
        assert(node.type == NodeTypes.Call)
        val callNode = node.asCall()

        val arguments = callNode.parameters
            .map { getValueOf(it) }

        for (function in builtInList) {
            if (function.name != callNode.targetName) {
                continue
            }

            if (!matchesTheseArgumentsSignature(arguments, function.parameterTypes)) {
                continue
            }

            return function.execute(arguments, output)
        }

        throw WalkerException("No function found matching ${callNode.targetName}")
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


    fun getValueOf(node: AstNode): Variable {

        return when (node.type) {
            NodeTypes.Constant -> Variable(Datatype.Integer, node.asConstant())
            NodeTypes.Call -> handleCall(node)
            NodeTypes.Identifier -> {
                val variableName = node.asIdentifier()
                if (variableName !in frame.variables) {
                    throw WalkerException("No variable named $variableName")
                }

                frame.variables.getValue(variableName)
            }

            NodeTypes.MemberAccess -> {
                val toAccess = getValueOf(node.childNodes[0])
                return toAccess.getField(node.data as String)
            }

            else -> {
                throw WalkerException()
            }
        }
    }


}

