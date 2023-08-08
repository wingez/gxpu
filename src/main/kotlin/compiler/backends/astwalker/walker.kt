package compiler.backends.astwalker

import ast.FunctionType
import compiler.BackendCompiler
import compiler.frontend.*
import compiler.mainDefinition

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

    val result = mutableListOf<Int>()
}

class WalkFrame(
    val localVariableHolder: ValueHolder,
)

enum class ControlFlow {
    Normal,
    Jump,
    Return,
}


class WalkerRunner(
    private val config: WalkConfig,
) : BackendCompiler {
    override fun buildAndRun(
        allTypes: List<Datatype>,
        functions: List<FunctionContent>,
        globals: GlobalsResult
    ): List<Int> {
        return WalkerState(allTypes, functions, config, globals).walk().result
    }
}


class WalkerState(
    types: List<Datatype>,
    private val intermediateFunctions: List<FunctionContent>,

    private val config: WalkConfig,
    private val globalsDefinition: GlobalsResult,
) : TypeProvider, FunctionSignatureResolver {


    private val types = types.associateBy { it.name }

    val output = WalkerOutput()
    val frameStack = mutableListOf<WalkFrame>()

    val currentFrame
        get() = frameStack.last()

    val availableFunctions = mutableListOf<IWalkerFunction>()

    lateinit var globalVariables: ValueHolder

    override fun getType(name: String): Datatype {
        if (name !in types) {
            throw WalkerException("No such type $name")
        }
        return types.getValue(name)
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

        builtInList.forEach {
            addFunction(it)
        }

        for (f in intermediateFunctions) {
            addFunction(UserFunction(f))
        }

        // setup global variables
        globalVariables = ValueHolder(globalsDefinition.fields)
        walkUserFunction(UserFunction(globalsDefinition.initialization), emptyList())

        //Call main
        val mainFunction = getFunctionFromSignature(
            mainDefinition
        )

        call(mainFunction, emptyList())

        return output
    }

    fun call(function: IWalkerFunction, parameters: List<Value>): Value {
        return function.execute(parameters, this)
    }

    fun setVariable(variable: Variable, value: Value) {
        getVariableView(variable).applyValue(value)
    }


    fun getVariableView(variable: Variable): ValueHolder.View {
        return when (variable.type) {
            VariableType.Local -> {
                currentFrame.localVariableHolder.viewEntire().viewField(variable.name)

            }
            VariableType.Global -> {
                globalVariables.viewEntire().viewField(variable.name)
            }
            else -> TODO()
        }
    }

    fun getVariable(variable: Variable): Value {
        return getVariableView(variable).getValue()
    }

    fun walkUserFunction(userFunction: UserFunction, parameters: List<Value>): Value {


        val fields = userFunction.functionContent.fields

        // Push new frame
        frameStack.add(WalkFrame(ValueHolder(fields)))

        // Add arguments as local variables
        userFunction.functionContent.definition.parameterNames.zip(parameters)
            .forEach { (paramName, value) ->
                assert(fields.fieldType(paramName) == value.datatype)
                setVariable(Variable(fields.getField(paramName), VariableType.Local), value)
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
            getVariable(Variable(fields.getField(RETURN_VALUE_NAME), VariableType.Local))
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
        require(condition.type == Primitives.Boolean)
        val value = getValueOf(condition)
        assert(value.datatype == Primitives.Boolean)

        val compareValue = if (jumpOn) 1 else 0
        if (value.asPrimitive == PrimitiveValue.integer(compareValue)) {
            return ControlFlow.Jump to label
        } else {
            return ControlFlow.Normal to null
        }
    }

    private fun handleAssign(instr: Assign) {

        val valueToAssign = getValueOf(instr.value)
        val holderToAssignTo = getValueView(instr.target)

        if (valueToAssign.datatype != holderToAssignTo.datatype) {
            throw WalkerException("Type mismatch. Expected ${holderToAssignTo.datatype}, got ${valueToAssign.datatype}")
        }

        holderToAssignTo.applyValue(valueToAssign)
    }


    fun handleCall(callExpression: CallExpression): Value {

        val arguments = callExpression.parameters
            .map { getValueOf(it) }

        val function = getFunctionFromSignature(callExpression.function)

        return call(function, arguments)
    }

    fun getValueView(addressExpression: AddressExpression): ValueHolder.View {

        return when (addressExpression) {
            is VariableExpression -> {
                return getVariableView(addressExpression.variable)
            }

            is DerefToAddress -> {
                getValueOf(addressExpression.value).asPrimitive.pointer
            }

            is AddressMemberAccess -> {
                val existing = getValueView(addressExpression.of)
                return existing.viewField(addressExpression.memberName)
            }

            else -> TODO(addressExpression.toString())
        }
    }

    fun getValueOf(valueExpression: ValueExpression): Value {

        return when (valueExpression) {
            is ConstantExpression -> Value.primitive(Primitives.Integer, valueExpression.value)
            is CallExpression -> handleCall(valueExpression)
            is VariableExpression -> getVariable(valueExpression.variable)
            is StringExpression -> createFromString(valueExpression.string)

            is AddressOf -> {
                val compositeHolder = getValueView(valueExpression.value)
                Value.pointer(compositeHolder)
            }

            is DerefToValue -> {
                getValueView(valueExpression.value).getPrimitiveValue().pointer.getValue()
            }

            is ValueMemberAccess -> {
                val existing = getValueOf(valueExpression.of)
                existing.getField(valueExpression.memberName)
            }

            is FunctionReference -> {
                val index =
                    availableFunctions.withIndex().find { it.value.definition == valueExpression.function }?.index
                require(index != null)
                Value.primitive(valueExpression.type, index)
            }

            else -> TODO(valueExpression.toString())
        }
    }
}

fun createArray(type: Datatype, size: Int): Value = createArray(type, size) { 0 }
fun createArray(type: Datatype, size: Int, init: (Int) -> Int): Value {

    val arrayType = type.arrayOf()

    val holder = ValueHolder(arrayType, size)

    for (i in 0 until size) {
        holder.primitives[i] = PrimitiveValue.integer(init.invoke(i))
    }

    return Value.pointer(holder.viewEntire())
}

fun createFromString(string: String): Value {
    return createArray(Primitives.Integer, string.length) { i -> string[i].code }
}

