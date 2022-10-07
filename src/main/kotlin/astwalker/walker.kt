package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

class WalkerException(msg: String = "") : Exception(msg)

class WalkerOutput() {

    val result = mutableListOf<String>()
}

data class FunctionDefinition(
    val name: String,
    val parameterTypes: List<Datatype>,
    val returnType: Datatype,
) {
    fun matches(name: String, parameterTypes: List<Datatype>): Boolean {
        return name == this.name && parameterTypes == this.parameterTypes
    }
}

interface IFunction {
    val definition: FunctionDefinition
    fun execute(variables: List<Variable>, state: WalkerState): Variable
}

class NodeFunction(
    val node: AstNode,
    override val definition: FunctionDefinition
) : IFunction {
    override fun execute(variables: List<Variable>, state: WalkerState): Variable {
        return state.walkFunction(node, variables)
    }
}

class WalkFrame {
    val variables = mutableMapOf<String, Variable>()
}

fun walk(node: AstNode): WalkerOutput {
    return walk(listOf(node))
}

fun walk(nodes: List<AstNode>): WalkerOutput {


    val walker = WalkerState(nodes)
    walker.walk()
    return walker.output

}

interface TypeProvider {
    fun getType(name: String): Datatype
}


private fun definitionFromFuncNode(node: AstNode, typeProvider: TypeProvider): NodeFunction {
    assert(node.type == NodeTypes.Function)
    val funcNode = node.asFunction()

    val name = funcNode.name
    val parameters = funcNode.arguments.map { argNode ->
        assert(argNode.type == NodeTypes.MemberDeclaration)
        val member = argNode.asMemberDeclaration()
        typeProvider.getType(member.type)
    }
    val returnType = typeProvider.getType(funcNode.returnType)
    val definition = FunctionDefinition(name, parameters, returnType)
    return NodeFunction(node, definition)
}


class WalkerState(
    val nodes: List<AstNode>
) : TypeProvider {

    val output = WalkerOutput()
    val frameStack = mutableListOf<WalkFrame>()

    val currentFrame
        get() = frameStack.last()


    private val types = mutableMapOf(
        "int" to Datatype.Integer,
        "void" to Datatype.Void,
    )

    val availableFunctions = mutableListOf<IFunction>()

    override fun getType(name: String): Datatype {
        if (name !in types) {
            throw WalkerException("No such type $name")
        }
        return types.getValue(name)
    }

    private fun addType(type: Datatype) {
        if (type.name in types) {
            throw WalkerException()
        }
        types[type.name] = type
    }

    private fun hasFunctionMatching(name: String, parameterTypes: List<Datatype>): Boolean {
        return availableFunctions.any { it.definition.matches(name, parameterTypes) }
    }

    private fun getFunctionMatching(name: String, parameterTypes: List<Datatype>): IFunction {
        return availableFunctions.find { it.definition.matches(name, parameterTypes) }
            ?: throw WalkerException("No functions matches $name($parameterTypes)")
    }

    private fun addFunction(function: IFunction) {
        if (hasFunctionMatching(function.definition.name, function.definition.parameterTypes)) {
            throw WalkerException()
        }
        availableFunctions.add(function)
    }

    fun walk(): WalkerOutput {

        nodes.filter { it.type == NodeTypes.Struct }
            .map { createTypeFromNode(it, this) }
            .forEach { addType(it) }

        builtInList.forEach {
            addFunction(it)
        }

        for (node in nodes.filter { it.type == NodeTypes.Function }) {
            addFunction(definitionFromFuncNode(node, this))
        }

        call("main", emptyList())

        return output
    }

    fun call(funcName: String, parameters: List<Variable>): Variable {

        val parameterTypes = parameters.map { it.datatype }
        val funcToCall = getFunctionMatching(funcName, parameterTypes)

        return funcToCall.execute(parameters, this)
    }

    fun walkFunction(node: AstNode, parameters: List<Variable>): Variable {
        assert(node.type == NodeTypes.Function)
        val funcNode = node.asFunction()


        // Push new frame
        frameStack.add(WalkFrame())

        // Add result variable
        val returnType = getType(funcNode.returnType)
        currentFrame.variables["result"] = createDefaultVariable(returnType)

        // Add arguments as local variables
        funcNode.arguments.map { it.asMemberDeclaration() }.zip(parameters).forEach { (memberInfo, value) ->
            assert(getType(memberInfo.type) == value.datatype)

            currentFrame.variables[memberInfo.name] = value
        }

        // Walk the function
        node.childNodes.forEach { walkRecursive(it) }


        val result = currentFrame.variables.getValue("result")

        // Pop frame
        frameStack.removeLast()

        return result
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
        assert(name !in currentFrame.variables)

        var newVariableType = types.getValue(memberDef.type)
        if (memberDef.isArray) {
            newVariableType = Datatype.Array(newVariableType)
        }

        currentFrame.variables[name] = createDefaultVariable(newVariableType)
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
            val isNewAssign = assignName !in currentFrame.variables
            if (isNewAssign) {
                currentFrame.variables[assignName] = valueToAssign
                return
            }

        }

        val variableToAssignTo = getValueOf(assignNode.target)

        if (valueToAssign.datatype != variableToAssignTo.datatype) {
            throw WalkerException("Type mismatch. Expected ${variableToAssignTo.datatype}, got ${valueToAssign.datatype}")
        }

        variableToAssignTo.copyFrom(valueToAssign)
    }

    fun handleCall(node: AstNode): Variable {
        assert(node.type == NodeTypes.Call)
        val callNode = node.asCall()

        val arguments = callNode.parameters
            .map { getValueOf(it) }

        return call(callNode.targetName, arguments)
    }

    fun getValueOf(node: AstNode): Variable {

        return when (node.type) {
            NodeTypes.Constant -> Variable(Datatype.Integer, node.asConstant())
            NodeTypes.Call -> handleCall(node)
            NodeTypes.Identifier -> {
                val variableName = node.asIdentifier()
                if (variableName !in currentFrame.variables) {
                    throw WalkerException("No variable named $variableName")
                }

                currentFrame.variables.getValue(variableName)
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

