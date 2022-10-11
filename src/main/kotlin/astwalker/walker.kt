package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.MemberDeclarationData
import se.wingez.ast.NodeTypes
import se.wingez.ast.TypeDefinition

class WalkerException(msg: String = "") : Exception(msg)

data class WalkConfig(
    val maxLoopIterations: Int,
) {
    companion object {
        val default = WalkConfig(
            maxLoopIterations = 1000
        )
    }
}

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

fun walk(node: AstNode, config: WalkConfig = WalkConfig.default): WalkerOutput {
    return walk(listOf(node))
}

fun walk(nodes: List<AstNode>, config: WalkConfig = WalkConfig.default): WalkerOutput {

    val walker = WalkerState(nodes, config)
    walker.walk()
    return walker.output

}

interface TypeProvider {
    fun getType(name: String): Datatype
    fun getType(typeDefinition: TypeDefinition): Datatype {
        val typeName = typeDefinition.typeName
        var type = getType(typeName)
        if (typeDefinition.isArray) {
            type = Datatype.Array(type)
        }
        return type
    }
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

enum class ControlFlow {
    Normal,
    Break,
    Return,
}

class WalkerState(
    val nodes: List<AstNode>,
    val config: WalkConfig,
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
            throw WalkerException("Function already exists: ${function.definition.name}(${function.definition.parameterTypes})")
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
        for (child in node.childNodes) {
            // This is the topmost. We need to handle every state. When statement should be exhaustive
            val shouldContinue = when (walkRecursive(child)) {
                ControlFlow.Normal -> true
                ControlFlow.Return -> false
                ControlFlow.Break -> {
                    throw WalkerException("No loop to break from")
                }
            }
            if (!shouldContinue) {
                break
            }
        }

        val result = currentFrame.variables.getValue("result")

        // Pop frame
        frameStack.removeLast()

        return result
    }

    private fun walkRecursive(node: AstNode): ControlFlow {


        return when (node.type) {
            NodeTypes.Call -> {
                handleCallIgnoreResult(node)
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

            NodeTypes.Break -> {
                ControlFlow.Break
            }

            NodeTypes.Return -> {
                handleReturn(node)
            }

            else -> throw WalkerException("Cannot execute node of type ${node.type} (yet)")
        }
    }

    private fun handleReturn(node: AstNode): ControlFlow {
        assert(node.type == NodeTypes.Return)
        val returnNode = node.asReturn()
        assert(!returnNode.hasValue())
        return ControlFlow.Return
    }

    fun handleMemberDeclaration(node: AstNode): ControlFlow {
        val memberDef = node.asMemberDeclaration()

        val name = memberDef.name
        assert(name !in currentFrame.variables)

        val newVariableType = getType(memberDef.type)

        currentFrame.variables[name] = createDefaultVariable(newVariableType)

        return ControlFlow.Normal
    }

    fun handleWhile(node: AstNode): ControlFlow {
        assert(node.type == NodeTypes.While)
        val whileNode = node.asWhile()


        for (iterationCounter in 0..config.maxLoopIterations) {

            // Check max iterations
            if (iterationCounter == config.maxLoopIterations) {
                throw WalkerException("Max allowed iteration in a while loop exceeded")
            }

            val conditionResult = getValueOf(whileNode.condition)
            if (conditionResult.datatype != Datatype.Boolean) {
                throw WalkerException("Expect conditional to be of boolean type")
            }

            if (conditionResult.getPrimitiveValue() != 1) {
                break
            }

            for (child in whileNode.body) {
                val controlFlowResult = walkRecursive(child)
                when (controlFlowResult) {
                    ControlFlow.Normal -> {

                    }

                    ControlFlow.Break -> return ControlFlow.Normal
                    else -> return controlFlowResult
                }
            }
        }

        return ControlFlow.Normal
    }


    private fun handleIf(node: AstNode): ControlFlow {
        assert(node.type == NodeTypes.If)
        val ifNode = node.asIf()

        val conditionResult = getValueOf(ifNode.condition)

        if (conditionResult.datatype != Datatype.Boolean) {
            throw WalkerException("Expect conditional to be of bool type")
        }

        if (conditionResult.getPrimitiveValue() != 0) {
            for (child in ifNode.ifBody) {
                val controlFlowResult = walkRecursive(child)
                if (controlFlowResult != ControlFlow.Normal) {
                    return controlFlowResult
                }
            }
        } else {
            if (ifNode.hasElse) {
                for (child in ifNode.elseBody) {
                    val controlFlowResult = walkRecursive(child)
                    if (controlFlowResult != ControlFlow.Normal) {
                        return controlFlowResult
                    }
                }
            }
        }
        return ControlFlow.Normal
    }

    private fun handleAssign(child: AstNode): ControlFlow {
        val assignNode = child.asAssign()

        val valueToAssign = getValueOf(assignNode.value).read()



        if (assignNode.target.type == NodeTypes.Identifier) {
            val assignName = assignNode.target.asIdentifier()
            val isNewAssign = assignName !in currentFrame.variables
            if (isNewAssign) {
                currentFrame.variables[assignName] = valueToAssign
                return ControlFlow.Normal
            }

        }

        val variableToAssignTo = getValueOf(assignNode.target)

        if (valueToAssign.datatype != variableToAssignTo.datatype) {
            throw WalkerException("Type mismatch. Expected ${variableToAssignTo.datatype}, got ${valueToAssign.datatype}")
        }

        variableToAssignTo.copyFrom(valueToAssign)
        return ControlFlow.Normal
    }

    fun handleCallIgnoreResult(node: AstNode): ControlFlow {
        handleCall(node)
        return ControlFlow.Normal
    }

    fun handleCall(node: AstNode): Variable {
        assert(node.type == NodeTypes.Call)
        val callNode = node.asCall()

        val arguments = callNode.parameters
            .map { getValueOf(it).read() }

        return call(callNode.targetName, arguments)
    }

    fun handleArrayAccess(node: AstNode): Variable {
        assert(node.type == NodeTypes.ArrayAccess)
        val arrayAccess = node.asArrayAccess()
        val array = getValueOf(arrayAccess.parent)
        val index = getValueOf(arrayAccess.index)

        if (index.datatype != Datatype.Integer) {
            throw WalkerException("Array index must be integer. Not ${index.datatype}")
        }
        if (!array.isArray()) {
            throw WalkerException("Cannot do array access on type ${array.datatype}")
        }
        return array.arrayAccess(index.getPrimitiveValue())
    }

    fun getValueOf(node: AstNode): Variable {

        return when (node.type) {
            NodeTypes.Constant -> Variable.primitive(Datatype.Integer, node.asConstant())
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

            NodeTypes.ArrayAccess -> handleArrayAccess(node)

            NodeTypes.String -> createFromString(node.asString())

            else -> {
                throw WalkerException("Cannot get value of node of type ${node.type}")
            }
        }
    }
}

