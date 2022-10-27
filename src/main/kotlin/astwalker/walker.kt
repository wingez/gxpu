package se.wingez.astwalker

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes

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

class WalkerOutput {

    val result = mutableListOf<String>()
}

class WalkFrame {
    val valueHolders = mutableMapOf<String, ValueHolder>()
}

fun walk(node: AstNode, config: WalkConfig = WalkConfig.default): WalkerOutput {
    return walk(listOf(node), config)
}

fun walk(nodes: List<AstNode>, config: WalkConfig = WalkConfig.default): WalkerOutput {

    val walker = WalkerState(nodes, config)
    walker.walk()
    return walker.output

}

enum class ControlFlow {
    Normal,
    Break,
    Return,
}

class WalkerState(
    val nodes: List<AstNode>,
    val config: WalkConfig,
) : TypeProvider, FunctionProvider, VariableProvider {

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

    override fun getFunctionMatching(name: String, parameterTypes: List<Datatype>): IFunction {
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
            .map { createTypeFromNode(it, this, this, this) }
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

    fun call(funcName: String, parameters: List<Value>): Value {

        val parameterTypes = parameters.map { it.datatype }
        val funcToCall = getFunctionMatching(funcName, parameterTypes)

        return funcToCall.execute(parameters, this)
    }

    fun walkFunction(node: AstNode, parameters: List<Value>): Value {
        assert(node.type == NodeTypes.Function)
        val funcNode = node.asFunction()


        // Push new frame
        frameStack.add(WalkFrame())

        // Add result variable
        val returnType = getType(funcNode.returnType)
        currentFrame.valueHolders["result"] = ValueHolder(returnType)

        // Add arguments as local variables
        funcNode.arguments.map { it.asNewVariable() }.zip(parameters).forEach { (memberInfo, value) ->
            assert(getType(memberInfo.optionalTypeDefinition!!) == value.datatype)
            val name = memberInfo.name
            createNewVariable(name, value.datatype)
            currentFrame.valueHolders.getValue(memberInfo.name).value = value
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

        val result = currentFrame.valueHolders.getValue("result").value

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

            NodeTypes.NewVariable -> {
                handleNewVariable(node)
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

    fun createNewVariable(name: String, type: Datatype) {
        assert(name !in currentFrame.valueHolders)
        currentFrame.valueHolders[name] = ValueHolder(type)
    }

    fun handleNewVariable(node: AstNode): ControlFlow {
        val newValDef = node.asNewVariable()

        val name = newValDef.name

        val newVariableType: Datatype
        if (newValDef.optionalTypeDefinition != null) {
            newVariableType = getType(newValDef.optionalTypeDefinition)
        } else {
            newVariableType = findType(newValDef.assignmentType, this, this)
        }
        createNewVariable(name, newVariableType)
        currentFrame.valueHolders[name] = ValueHolder(newVariableType)

        return ControlFlow.Normal
    }

    fun handleWhile(node: AstNode): ControlFlow {
        assert(node.type == NodeTypes.While)
        val whileNode = node.asWhile()

        val variablesBeforeLoop = currentFrame.valueHolders.keys.toList()

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

            // Clear variables
            val toRemove = currentFrame.valueHolders.keys.filter { it !in variablesBeforeLoop }
            toRemove.forEach { currentFrame.valueHolders.remove(it) }

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

    fun modifyVariable(name: String, newValue: Value) {

    }

    private fun handleAssign(child: AstNode): ControlFlow {
        val assignNode = child.asAssign()

        val valueToAssign = getValueOf(assignNode.value).read()
        val holderToAssignTo = getValueHolderOf(assignNode.target)

        if (valueToAssign.datatype != holderToAssignTo.type) {
            throw WalkerException("Type mismatch. Expected ${holderToAssignTo.type}, got ${valueToAssign.datatype}")
        }

        holderToAssignTo.value = valueToAssign
        return ControlFlow.Normal
    }

    fun handleCallIgnoreResult(node: AstNode): ControlFlow {
        handleCall(node)
        return ControlFlow.Normal
    }

    fun handleCall(node: AstNode): Value {
        assert(node.type == NodeTypes.Call)
        val callNode = node.asCall()

        val arguments = callNode.parameters
            .map { getValueOf(it).read() }

        return call(callNode.targetName, arguments)
    }

    fun getArrayIndexValueHolder(node: AstNode): ValueHolder {
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

    override fun getTypeOfVariable(variableName: String): Datatype {
        return getVariable(variableName).datatype
    }

    fun getVariableHolder(variableName: String): ValueHolder {
        if (variableName !in currentFrame.valueHolders) {
            throw WalkerException("No variable named $variableName")
        }
        return currentFrame.valueHolders.getValue(variableName)
    }

    fun getVariable(variableName: String): Value {
        return getVariableHolder(variableName).value
    }

    fun getValueHolderOf(node: AstNode): IValueHolder {
        return when (node.type) {
            NodeTypes.Identifier -> getVariableHolder(node.asIdentifier())
            NodeTypes.ArrayAccess -> getArrayIndexValueHolder(node)
            NodeTypes.MemberAccess -> {
                val toAccess = getValueOf(node.childNodes[0])
                return toAccess.getFieldValueHolder(node.data as String)
            }
            else -> throw WalkerException("Not supported yet")
        }
    }

    fun getValueOf(node: AstNode): Value {

        return when (node.type) {
            NodeTypes.Identifier -> getValueHolderOf(node).value
            NodeTypes.ArrayAccess -> getValueHolderOf(node).value
            NodeTypes.MemberAccess -> getValueHolderOf(node).value

            NodeTypes.Constant -> Value.primitive(Datatype.Integer, node.asConstant())
            NodeTypes.Call -> handleCall(node)

            NodeTypes.String -> createFromString(node.asString())

            else -> {
                throw WalkerException("Cannot get value of node of type ${node.type}")
            }
        }
    }
}

