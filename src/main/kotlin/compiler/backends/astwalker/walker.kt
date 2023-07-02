package compiler.backends.astwalker

import ast.FunctionType
import compiler.BackendCompiler
import compiler.frontend.*
import compiler.mainSignature

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

class WalkFrame(
    val holder: FieldsHolder,
)

enum class ControlFlow {
    Normal,
    Jump,
    Return,
}


class WalkerRunner(
    private val config: WalkConfig,
) : BackendCompiler {
    override fun buildAndRun(allTypes: List<Datatype>, functions: List<FunctionContent>): List<String> {
        return WalkerState(allTypes, functions, config).walk().result

    }

}


class WalkerState(
    types: List<Datatype>,
    val intermediateFunctions: List<FunctionContent>,

    val config: WalkConfig,
) : TypeProvider, FunctionSignatureResolver, VariableProvider {


    private val types = types.associateBy { it.name }

    val output = WalkerOutput()
    val frameStack = mutableListOf<WalkFrame>()

    val currentFrame
        get() = frameStack.last()

    val availableFunctions = mutableListOf<IWalkerFunction>()

    override fun getType(name: String): Datatype {
        if (name !in types) {
            throw WalkerException("No such type $name")
        }
        return types.getValue(name)
    }

    private fun hasFunctionMatching(name: String, functionType: FunctionType, parameterTypes: List<Datatype>): Boolean {
        return availableFunctions.any { it.signature.matches(name, functionType, parameterTypes) }
    }

    fun getFunctionFromSignature(functionSignature: FunctionSignature): IWalkerFunction {
        return availableFunctions.find { it.signature == functionSignature }
            ?: throw WalkerException("No functions matches $functionSignature")
    }

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionSignature {
        val func = availableFunctions.find { it.signature.matches(name, functionType, parameterTypes) }
            ?: throw WalkerException("No functions matches $name($parameterTypes)")
        return func.signature
    }

    private fun addFunction(function: IWalkerFunction) {
        if (hasFunctionMatching(
                function.signature.name,
                function.signature.functionType,
                function.signature.parameterTypes
            )
        ) {
            throw WalkerException("Function already exists: ${function.signature.name}(${function.signature.parameterTypes})")
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

        val mainFunction = getFunctionFromSignature(
            mainSignature
        )

        call(mainFunction, emptyList())

        return output
    }

    fun call(function: IWalkerFunction, parameters: List<Value>): Value {
        return function.execute(parameters, this)
    }

    fun setVariable(variableName: String, value: Value) {
        getVariableView(variableName).applyValue(value)
    }


    fun getVariableView(variableName: String): FieldsHolder.FieldsView {
        return currentFrame.holder.viewEntire().viewField(variableName)
    }

    fun getVariable(variableName: String): Value {
        return getVariableView(variableName).getValue()
    }

    fun walkUserFunction(userFunction: UserFunction, parameters: List<Value>): Value {


        val fields = userFunction.functionContent.fields

        // Push new frame
        frameStack.add(WalkFrame(FieldsHolder(userFunction.functionContent.fields)))

        // Add arguments as local variables
        userFunction.functionContent.definition.parameterNames.zip(parameters)
            .forEach { (paramName, value) ->
                assert(fields.fieldType(paramName) == value.datatype)
                setVariable(paramName, value)
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

        val result = if (userFunction.signature.returnType == Datatype.Void) {
            Value.void
        } else {
            getVariable("result")
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
        require(condition.type == Datatype.Boolean)
        val value = getValueOf(condition)
        assert(value.datatype == Datatype.Boolean)

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


    fun getValueView(addressExpression: AddressExpression): FieldsHolder.FieldsView {

        return when (addressExpression) {
            is VariableExpression -> {
                return getVariableView(addressExpression.variable.name)
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
            is ConstantExpression -> Value.primitive(Datatype.Integer, valueExpression.value)
            is CallExpression -> handleCall(valueExpression)
            is VariableExpression -> getVariable(valueExpression.variable.name)
            is StringExpression -> createFromString(valueExpression.string)

            is AddressOf -> {
                val compositeHolder = getValueView(valueExpression.value)
                Value.pointer( compositeHolder)
            }

            is DerefToValue -> {
                getValueView(valueExpression.value).getPrimitiveValue().pointer.getValue()
            }

            is ValueMemberAccess -> {
                val existing = getValueOf(valueExpression.of)
                existing.getField(valueExpression.memberName)
            }

            else -> TODO(valueExpression.toString())
        }
    }
}
