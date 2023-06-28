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

class CompositeValueHolder(
    val type: Datatype,
    val valueHolders: Map<VariableHandle, PrimitiveValueHolder>,
    val singleValueHolder: PrimitiveValueHolder?,
) {

    fun getCompositeValueHolder(variableName: String): CompositeValueHolder {
        require(type.isComposite)
        require(type.containsField(variableName))
        val fieldType = type.fieldType(variableName)
        if (!fieldType.isComposite) {
            return CompositeValueHolder(
                fieldType, emptyMap(), valueHolders.getValue(
                    VariableHandle(
                        listOf(variableName), fieldType
                    )
                )
            )
        } else {
            return CompositeValueHolder(
                fieldType,
                valueHolders.keys.filter { it.accessors.first() == variableName }.associate {
                    VariableHandle(it.accessors.subList(1, it.accessors.size), it.type) to valueHolders.getValue(it)
                },
                null,
            )
        }
    }

    fun setValue(value: Value) {
        if (value.datatype.isComposite) TODO()

        require(singleValueHolder!!.type == value.datatype)
        singleValueHolder.value = value
    }

    fun asValue(): Value {
        if (singleValueHolder != null) {
            return singleValueHolder.value
        }
        return Value.composite(type, valueHolders.keys.associateWith { valueHolders.getValue(it).value })
    }


}

class WalkFrame(
    val holder: CompositeValueHolder,
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

        if (!value.datatype.isComposite) {
            getVariableHolder(variableName).singleValueHolder!!.value = value
        } else {
            TODO()
        }
    }


    fun getVariableHolder(variableName: String): CompositeValueHolder {
        return currentFrame.holder.getCompositeValueHolder(variableName)
    }

    fun getVariable(variableName: String): Value {
        return getVariableHolder(variableName).asValue()
    }

    fun walkUserFunction(userFunction: UserFunction, parameters: List<Value>): Value {


        val fields = userFunction.functionContent.fields

        // Push new frame
        frameStack.add(
            WalkFrame(
                CompositeValueHolder(
                    fields,
                    getVariableHandlesForDatatype(fields).associateWith { PrimitiveValueHolder(it.type) },
                    null,
                )
            )
        )

        // Add arguments as local variables
        fields.compositeFields.filter { it.annotation == FieldAnnotation.Parameter }.zip(parameters)
            .forEach { (variable, value) ->
                assert(variable.type == value.datatype)
                setVariable(variable.name, value)
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
            Value.void()
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
        val value = getValueOf(condition)
        assert(value.datatype == Datatype.Boolean)

        val compareValue = if (jumpOn) 1 else 0
        if (value.getPrimitiveValue() == compareValue) {
            return ControlFlow.Jump to label
        } else {
            return ControlFlow.Normal to null
        }
    }

    private fun handleAssign(instr: Assign) {

        val valueToAssign = getValueOf(instr.value)
        val holderToAssignTo = getValueHolderOf(instr.target)

        if (valueToAssign.datatype != holderToAssignTo.type) {
            throw WalkerException("Type mismatch. Expected ${holderToAssignTo.type}, got ${valueToAssign.datatype}")
        }

        holderToAssignTo.setValue(valueToAssign)
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


    fun getValueHolderOf(addressExpression: AddressExpression): CompositeValueHolder {

        return when (addressExpression) {
            is VariableExpression -> {
                return getVariableHolder(addressExpression.variable.name)
            }

            is DerefToAddress -> {
                val pointer = getValueOf(addressExpression.value)
                pointer.derefPointer()
            }

            is AddressMemberAccess -> {
                val existing = getValueHolderOf(addressExpression.of)
                return existing.getCompositeValueHolder(addressExpression.memberName)
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
                val compositeHolder = getValueHolderOf(valueExpression.value)
                Value.pointer(compositeHolder.type, compositeHolder)
            }

            is DerefToValue -> {
                getValueHolderOf(valueExpression.value).singleValueHolder!!.value.derefPointer().asValue()
            }

            is ValueMemberAccess -> {
                val existing = getValueOf(valueExpression.of)
                existing.getField(valueExpression.memberName)
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

private fun getVariableHandlesForDatatype(type: Datatype): List<VariableHandle> {
    val result = mutableListOf<VariableHandle>()
    getVariableHandlesForDatatypeImpl(type, emptyList(), result)
    return result
}


private fun getVariableHandlesForDatatypeImpl(
    type: Datatype,
    accesorsSoFar: List<String>,
    result: MutableList<VariableHandle>
) {
    if (!type.isComposite) {
        result.add(VariableHandle(accesorsSoFar, type))
    } else {
        for (field in type.compositeFields) {
            getVariableHandlesForDatatypeImpl(field.type, accesorsSoFar + listOf(field.name), result)
        }
    }
}
