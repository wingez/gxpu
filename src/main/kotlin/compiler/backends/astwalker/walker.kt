package compiler.backends.astwalker

import ast.AstNode
import compiler.backends.astwalker.*
import compiler.frontend.*
import ast.FunctionType
import ast.NodeTypes
import compiler.frontend.*

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
    Jump,
    Return,
}

class WalkerState(
    val nodes: List<AstNode>,
    val config: WalkConfig,
) : TypeProvider, FunctionDefinitionResolver, VariableProvider {

    val output = WalkerOutput()
    val frameStack = mutableListOf<WalkFrame>()

    val currentFrame
        get() = frameStack.last()


    private val types = mutableMapOf(
        "int" to Datatype.Integer,
        "void" to Datatype.Void,
    )

    val availableFunctions = mutableListOf<IWalkerFunction>()

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

    private fun hasFunctionMatching(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return availableFunctions.any { it.definition.matches(name, functionType, parameterTypes) }
    }

    fun getFunctionFromSignature(functionDefinition: FunctionDefinition): IWalkerFunction {
        return availableFunctions.find { it.definition == functionDefinition }
            ?: throw WalkerException("No functions matches $functionDefinition")
    }

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        val func = availableFunctions.find { it.definition.matches(name, functionType, parameterTypes) }
            ?: throw WalkerException("No functions matches $name($parameterTypes)")
        return func.definition
    }

    private fun addFunction(function: IWalkerFunction) {
        if (hasFunctionMatching(
                function.definition.name,
                function.definition.functionType,
                function.definition.parameterTypes
            )
        ) {
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
            addFunction(definitionFromFuncNode(node, this, this))
        }

        val mainFunction = getFunctionFromSignature(
            FunctionDefinition("main", emptyList(), Datatype.Void, FunctionType.Normal)
        )
        call(mainFunction, emptyList())

        return output
    }

    fun call(function: IWalkerFunction, parameters: List<Value>): Value {
        return function.execute(parameters, this)
    }

    fun walkUserFunction(userFunction: UserFunction, parameters: List<Value>): Value {

        // Push new frame
        frameStack.add(WalkFrame())

        // Create variables for parameters & local variables
        userFunction.functionContent.localVariables.forEach {
            createNewVariable(it.name, it.datatype)
        }

        // Add arguments as local variables
        userFunction.functionContent.localVariables.filter { it.type == VariableType.Parameter }.zip(parameters)
            .forEach { (variable, value) ->
                assert(variable.datatype == value.datatype)
                currentFrame.valueHolders.getValue(variable.name).value = value
            }

        // Walk the function

        val code = userFunction.code

        var currentInstructionIndex = 0

        var totalInstructionsCounter = 0
        while (true) {
            if (totalInstructionsCounter++ > config.maxLoopIterations) {
                throw WalkerException("Maximum instructions exceeded")
            }

            if (currentInstructionIndex >= code.instructions.size) {
                // FIXME: Auto return??
                throw WalkerException("Missing return??")
            }

            val toExecute = code.instructions[currentInstructionIndex]
            val (controlFlow, jumpLabel) = walkInstruction(toExecute)

            when (controlFlow) {
                ControlFlow.Normal -> currentInstructionIndex++
                ControlFlow.Jump -> {
                    currentInstructionIndex = code.labels.getValue(jumpLabel!!)
                }

                ControlFlow.Return -> break
            }
        }

        val result = if (userFunction.definition.returnType == Datatype.Void) {
            Value.void()
        } else {
            currentFrame.valueHolders.getValue("result").value
        }

        // Pop frame
        frameStack.removeLast()

        return result
    }

    /**
    A non-null value represents the next label we should go to
     **/
    private fun walkInstruction(instruction: Instruction): Pair<ControlFlow, Label?> {

        when (instruction) {
            is Jump -> {
                return ControlFlow.Jump to instruction.label
            }

            is JumpOnTrue -> {
                return jumpHelper(instruction.condition, jumpOn = true, instruction.label)
            }

            is JumpOnFalse -> {
                return jumpHelper(instruction.condition, jumpOn = false, instruction.label)
            }

            is Execute -> {
                getValueOf(instruction.expression)
            }

            is Assign -> {
                handleAssign(instruction)
            }

            is Return -> {
                return ControlFlow.Return to null
            }

            else -> throw NotImplementedError(instruction.toString())
        }

        return ControlFlow.Normal to null
    }

    private fun jumpHelper(condition: ValueExpression, jumpOn: Boolean, label: Label): Pair<ControlFlow, Label?> {
        val value = getValueOf(condition)
        assert(value.datatype == Datatype.Boolean)

        val compareValue = if (jumpOn) 1 else 0
        if (value.getPrimitiveValue() == compareValue) {
            return ControlFlow.Jump to label
        } else {
            return ControlFlow.Normal to null
        }


    }

    /*private fun walkRecursive(node: AstNode): ControlFlow {


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
    }*/

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

        return ControlFlow.Normal
    }

    /*fun handleWhile(node: AstNode): ControlFlow {
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
    }*/


    /*private fun handleIf(node: AstNode): ControlFlow {
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
    }*/

    fun modifyVariable(name: String, newValue: Value) {

    }

    private fun handleAssign(instr: Assign) {

        val valueToAssign = getValueOf(instr.value)
        val holderToAssignTo = getValueHolderOf(instr.target)

        if (valueToAssign.datatype != holderToAssignTo.type) {
            throw WalkerException("Type mismatch. Expected ${holderToAssignTo.type}, got ${valueToAssign.datatype}")
        }

        holderToAssignTo.value = valueToAssign
        //return ControlFlow.Normal
    }

    /*fun handleCallIgnoreResult(node: AstNode): ControlFlow {
        handleCall(node)
        return ControlFlow.Normal
    }*/

    fun handleCall(callExpression: CallExpression): Value {

        val arguments = callExpression.parameters
            .map { getValueOf(it) }

        val function = getFunctionFromSignature(callExpression.function)

        return call(function, arguments)
    }

    /*fun getArrayIndexValueHolder(node: AstNode): ValueHolder {
        assert(node.type == NodeTypes.ArrayAccess)
        val arrayAccess = node.asArrayAccess()
        val arrayPointer = getValueOf(arrayAccess.parent)
        val index = getValueOf(arrayAccess.index)

        if (index.datatype != Datatype.Integer) {
            throw WalkerException("Array index must be integer. Not ${index.datatype}")
        }
        if (!arrayPointer.isPointer()) {
            throw WalkerException("Cannot do array access on type ${arrayPointer.datatype}")
        }
        val array = arrayPointer.derefPointer().value
        if (!array.isArray()) {
            throw WalkerException("Cannot do array access on type ${arrayPointer.datatype}")
        }
        return array.arrayAccess(index.getPrimitiveValue())
    }*/

    /*fun getMemberValueHolder(node: AstNode): IValueHolder {
        val toAccessPointer = getValueOf(node.childNodes[0])
        assert(toAccessPointer.isPointer())
        val toAccess = toAccessPointer.derefPointer().value
        return toAccess.getFieldValueHolder(node.data as String)
    }*/

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

    fun getValueHolderOf(addressExpression: AddressExpression): ValueHolder {

        return when (addressExpression) {
            is VariableExpression -> {
                return getVariableHolder(addressExpression.variable.name)
            }

            is DerefToAddress -> {
                val pointer = getValueOf(addressExpression.value)
                pointer.derefPointer()
            }

            else -> TODO(addressExpression.toString())
        }
        /*return when (node.type) {
            NodeTypes.Identifier -> getVariableHolder(node.asIdentifier())
            //NodeTypes.ArrayAccess -> getArrayIndexValueHolder(node)
            //NodeTypes.MemberAccess -> getMemberValueHolder(node)

            else -> throw WalkerException("Not supported yet")
        }*/
    }

    fun getValueOf(valueExpression: ValueExpression): Value {

        return when (valueExpression) {
            is ConstantExpression -> Value.primitive(Datatype.Integer, valueExpression.value)
            is CallExpression -> handleCall(valueExpression)
            is VariableExpression -> getVariable(valueExpression.variable.name)
            is StringExpression -> createFromString(valueExpression.string)

            is AddressOf -> {
                Value.pointer(getValueHolderOf(valueExpression.value))
            }

            is DerefToValue -> {
                getValueHolderOf(valueExpression.value).value.derefPointer().value
            }

            else -> TODO(valueExpression.toString())
        }


        /*return when (node.type) {
            NodeTypes.Identifier -> getValueHolderOf(node).value
            NodeTypes.ArrayAccess -> getValueHolderOf(node).value
            NodeTypes.MemberAccess -> getValueHolderOf(node).value

            NodeTypes.Constant -> Value.primitive(Datatype.Integer, node.asConstant())
            NodeTypes.Call -> handleCall(node)

            NodeTypes.String -> createFromString(node.asString())

            else -> {
                throw WalkerException("Cannot get value of node of type ${node.type}")
            }
        }*/
    }
}

