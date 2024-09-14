package compiler.backends.astwalker

import compiler.BackendCompiler
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

class Value(val type: Datatype, val primitive: Int = 0, val array: IntArray? = null) {
    companion object {
        val nothing = Value(Primitives.Nothing)
    }
}


private fun sizeOf(datatype: Datatype): Int {
    return when (datatype) {
        is PrimitiveDataType -> 1
        else -> TODO(datatype.toString())
    }
}

private fun createTemplate(functionContent: FunctionContent): Template {


    var totalSize = 0
    val variableMap = mutableMapOf<String, Pair<Datatype, Int>>()

    for ((paramName, paramType) in functionContent.definition.parameters) {
        variableMap[paramName] = paramType to totalSize
        totalSize += sizeOf(paramType)
    }

    for (instr in functionContent.instructions) {
        if (instr !is TempValue) {
            continue
        }

        variableMap[instr.name] = instr.type to totalSize
        totalSize += sizeOf(instr.type)

    }

    return Template(totalSize, variableMap)
}

private class Template(val totalSize: Int, val variableMap: Map<String, Pair<Datatype, Int>>)

private class WalkFrame(private val template: Template) {

    private val data = IntArray(template.totalSize)

    fun setVariable(name: String, value: Value) {
        val (type, offset) = template.variableMap.getValue(name)

        assert(type == value.type)

        if (type is PrimitiveDataType) {
            data[offset] = value.primitive
        } else {
            value.array!!.copyInto(data, offset)
        }

    }

    fun getVariable(name: String): Value {
        val (type, offset) = template.variableMap.getValue(name)

        if (type is PrimitiveDataType) {
            return Value(type, primitive = data[offset])
        }

        val size = sizeOf(type)
        return Value(type, array = data.copyOfRange(offset, offset + size))
    }
}

enum class ControlFlow {
    Normal,
    Jump,
    Return,
}


class WalkerRunner(
    private val config: WalkConfig,
) : BackendCompiler {
    override fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String> {
        return WalkerState(intermediateProgram, config).walk().result
    }
}


class WalkerState(
    val intermediateProgram: CompiledIntermediateProgram,
    private val config: WalkConfig,
) {
    val output = WalkerOutput()
    private val frameStack = mutableListOf<WalkFrame>()

    private val currentFrame
        get() = frameStack.last()

    val availableFunctions = mutableListOf<IWalkerFunction>()

    //lateinit var globalVariables: ValueHolder

    fun getFunctionFromSignature(functionDefinition: FunctionDefinition): IWalkerFunction {
        return availableFunctions.find { it.definition == functionDefinition }
            ?: throw WalkerException("No functions matches $functionDefinition")
    }


    private fun addFunction(function: IWalkerFunction) {
        if (availableFunctions.any { it.definition == function.definition }) {
            throw WalkerException("Function already exists: ${function.definition.name}(${function.definition.parameterTypes})")
        }
        availableFunctions.add(function)
    }

    fun walk(): WalkerOutput {

        builtInList.forEach {
            addFunction(it)
        }

        for (f in intermediateProgram.functions) {
            addFunction(UserFunction(f))
        }

        // setup global variables
//        val globals = symbolTable.getAllGlobalVariables()

//        val allGlobalsFields = CompositeDatatype("fields", globals.map { CompositeDataTypeField(it.name, it.datatype) })
//
//        globalVariables = ValueHolder(allGlobalsFields)


        //Call main
        val mainFunction = getFunctionFromSignature(
            intermediateProgram.mainFunction.definition
        )

        call(mainFunction, emptyList())

        return output
    }

    fun call(function: IWalkerFunction, parameters: List<Value>): Value {
        return function.execute(parameters, this)
    }

//    fun setVariable(variableType: VariableType, name: String, value: Value) {
//        getVariableView(variableType, name).applyValue(value)
//    }


//    fun getVariableView(variableType: VariableType, name: String): ValueHolder.View {
//        return when (variableType) {
//            VariableType.Local -> {
//                currentFrame.localVariableHolder.viewEntire().viewField(name)
//
//            }
//
//            VariableType.Global -> {
//                globalVariables.viewEntire().viewField(name)
//            }
//
//            else -> TODO()
//        }
//    }

//    fun getVariable(variableType: VariableType, name: String): Value {
//        return getVariableView(variableType, name).getValue()
//    }

    fun walkUserFunction(userFunction: UserFunction, parameters: List<Value>): Value {

//        val localVariables = symbolTable.getVariablesForFunction(userFunction.definition)

//        val fields = CompositeDatatype("fields", localVariables.map { CompositeDataTypeField(it.name, it.datatype) })


        // Push new frame
        frameStack.add(WalkFrame(createTemplate(userFunction.functionContent)))

        // Add arguments as local variables
        userFunction.functionContent.definition.parameterNames.zip(parameters)
            .forEach { (paramName, value) ->
                currentFrame.setVariable(paramName, value)
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

        val result = if (userFunction.definition.returnType == Primitives.Nothing) {
            Value.nothing
        } else {
            currentFrame.getVariable(RETURN_VALUE_NAME)
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

            is TempValue -> {
                val value = getValueOf(instruction.value)
                currentFrame.setVariable(instruction.name, value)
            }

            is JumpOnTrue -> {
                return jumpHelper(instruction.condition, jumpOn = true, instruction.label)
            }

            is JumpOnFalse -> {
                return jumpHelper(instruction.condition, jumpOn = false, instruction.label)
            }
//
//            is Execute -> {
//                getValueOf(instruction.expression)
//            }
//
//            is Assign -> {
//                handleAssign(instruction)
//            }

            is ReturnNothing -> {
                return ControlFlow.Return to null
            }

            else -> throw NotImplementedError(instruction.toString())
        }

        return ControlFlow.Normal to null
    }

    private fun jumpHelper(
        condition: compiler.frontend.ValueExpr,
        jumpOn: Boolean,
        label: Label
    ): Pair<ControlFlow, Label?> {
        require(condition.type == Primitives.Boolean)
        val value = getValueOf(condition)
        assert(value.type == Primitives.Boolean)

        val compareValue = if (jumpOn) 1 else 0
        if (value.primitive == compareValue) {
            return ControlFlow.Jump to label
        } else {
            return ControlFlow.Normal to null
        }
    }
//
//    private fun handleAssign(instr: Assign) {
//
//        val valueToAssign = getValueOf(instr.value)
//        val holderToAssignTo = getValueView(instr.target)
//
//        if (valueToAssign.datatype != holderToAssignTo.datatype) {
//            throw WalkerException("Type mismatch. Expected ${holderToAssignTo.datatype}, got ${valueToAssign.datatype}")
//        }
//
//        holderToAssignTo.applyValue(valueToAssign)
//    }


    //    fun handleCall(callExpression: CallExpression): Value {
//
//        val arguments = callExpression.parameters
//            .map { getValueOf(it) }
//
//        val function = getFunctionFromSignature(callExpression.function)
//
//        return call(function, arguments)
//    }
//
//    fun getValueView(addressExpression: AddressExpression): ValueHolder.View {
//
//        return when (addressExpression) {
//            is VariableExpression -> {
//                return getVariableView(addressExpression.variable.variableType, addressExpression.variable.name)
//            }
//
//            is DerefToAddress -> {
//                getValueOf(addressExpression.value).asPrimitive.pointer
//            }
//
//            is AddressMemberAccess -> {
//                val existing = getValueView(addressExpression.of)
//                return existing.viewField(addressExpression.memberName)
//            }
//
//            else -> TODO(addressExpression.toString())
//        }
//    }
//

    private fun getValueOf(value: ValueExpr): Value {

        return when (value) {
            is IntConstant -> Value(Primitives.Integer, primitive = value.value)
            is LocalValueRef -> currentFrame.getVariable(value.name)
            is Call -> {
                val arguments = value.params.map { getValueOf(it) }

                val function = getFunctionFromSignature(value.func)

                return call(function, arguments)
            }
//            is CallExpression -> handleCall(valueExpression)
//            is VariableExpression -> getVariable(valueExpression.variable.variableType, valueExpression.variable.name)
//            is StringExpression -> createFromString(valueExpression.string)
//
//            is AddressOf -> {
//                val compositeHolder = getValueView(valueExpression.value)
//                Value.pointer(compositeHolder)
//            }
//
//            is DerefToValue -> {
//                getValueView(valueExpression.value).getPrimitiveValue().pointer.getValue()
//            }
//
//            is ValueMemberAccess -> {
//                val existing = getValueOf(valueExpression.of)
//                existing.getField(valueExpression.memberName)
//            }
//
//            is FunctionReference -> {
//                val index =
//                    availableFunctions.withIndex().find { it.value.definition == valueExpression.function }?.index
//                require(index != null)
//                Value.primitive(valueExpression.type, index)
//            }

            else -> TODO(value.toString())
        }
    }
}

//fun createArray(type: Datatype, size: Int): ValueExpr = createArray(type, size) { 0 }
//fun createArray(type: Datatype, size: Int, init: (Int) -> Int): ValueExpr {
//
//    val arrayType = type.arrayOf()
//
//    val holder = ValueHolder(arrayType, size)
//
//    for (i in 0 until size) {
//        holder.primitives[i] = PrimitiveValue.integer(init.invoke(i))
//    }
//
//    return ValueExpr.pointer(holder.viewEntire())
//}

//fun createFromString(string: String): ValueExpr {
//    return createArray(Primitives.Integer, string.length) { i -> string[i].code }
//}

