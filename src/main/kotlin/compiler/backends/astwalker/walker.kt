package compiler.backends.astwalker

import compiler.BackendCompiler
import compiler.frontend.*
import requireNotReached

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


private fun sizeOf(datatype: Datatype): Int {
    return when (datatype) {
        is PrimitiveDataType -> 1
        is PointerDatatype -> 1
        is CompositeDatatype -> datatype.compositeFields.sumOf { sizeOf(it.type) }
        else -> TODO(datatype.toString())
    }
}

private fun fieldOffset(datatype: CompositeDatatype, name: String): Int {
    var offset = 0
    for (field in datatype.compositeFields) {
        if (field.name == name) {
            return offset
        }
        offset += sizeOf(field.type)
    }
    requireNotReached()
}

private fun createTemplate(variables: List<Pair<String, Datatype>>): Template {


    var totalSize = 0
    val variableMap = mutableMapOf<String, Pair<Datatype, Int>>()

    for ((paramName, paramType) in variables) {
        variableMap[paramName] = paramType to totalSize
        totalSize += sizeOf(paramType)
    }

    return Template(totalSize, variableMap)
}

private fun createTemplate(functionContent: FunctionContent): Template {

    val list = mutableListOf<Pair<String, Datatype>>()
    list.addAll(functionContent.definition.parameters)

    for (instr in functionContent.instructions) {
        if (instr !is TempValue) {
            continue
        }
        list.add(instr.name to instr.type)
    }
    return createTemplate(list)
}

class Template(val totalSize: Int, val variableMap: Map<String, Pair<Datatype, Int>>)

class Value(
    val type: Datatype,
    val primitive: Int = 0,
    val pointer: WalkFrame.Pointer? = null,
    val primitiveArray: IntArray? = null,
    val pointerArray: Array<WalkFrame.Pointer?>? = null,
) {
    companion object {
        val nothing = Value(Primitives.Nothing)
    }
}

class WalkFrame(private val template: Template) {

    private val dynamicStackSize = 100

    private val totalStackSize = template.totalSize + dynamicStackSize

    private val data = IntArray(totalStackSize)
    private val pointers = Array<Pointer?>(totalStackSize) { null }

    private var stackposition = template.totalSize

    fun pointer(name: String): Value {
        val pointer = pointerToVariable(name)
        return Value(pointer.type, pointer = pointer)
    }

    private fun pointerToVariable(name: String): Pointer {
        val (type, offset) = template.variableMap.getValue(name)

        return Pointer(type.pointerOf(), this, offset)
    }

    fun setVariable(name: String, value: Value) {
        val pointer = pointerToVariable(name)
        pointer.setValue(value)

    }

    fun getVariable(name: String): Value {
        val pointer = pointerToVariable(name)
        return pointer.getDeref()
    }

    fun stackDynamicAlloc(type: Datatype): Value {
        val size = sizeOf(type)

        if (stackposition + size >= totalStackSize) {
            throw WalkerException("Out of stack size")
        }
        val pos = stackposition
        stackposition += size

        val pointer = Pointer(type.pointerOf(), this, pos)
        return Value(pointer.type, pointer = pointer)
    }

    fun stackDynamicArrayAlloc(arrayType: Datatype, arraySize: Int): Value {
        val size = sizeOf(Primitives.Integer) + sizeOf(arrayType) * arraySize


        if (stackposition + size >= totalStackSize) {
            throw WalkerException("Out of stack size")
        }
        val pos = stackposition
        stackposition += size

        val pointer = Pointer(arrayType.arrayPointerOf(), this, pos)
        return Value(pointer.type, pointer = pointer)
    }


    class Pointer(val type: PointerDatatype, private val frame: WalkFrame, private val offset: Int) {
        fun getDeref(): Value {

            when (type.pointerType) {
                is PrimitiveDataType -> {
                    return Value(type.pointerType, primitive = frame.data[offset])
                }

                is PointerDatatype -> {
                    return Value(type.pointerType, pointer = frame.pointers[offset])
                }

                else -> {
                    val size = sizeOf(type.pointerType)
                    return Value(
                        type.pointerType,
                        primitiveArray = frame.data.copyOfRange(offset, offset + size),
                        pointerArray = frame.pointers.copyOfRange(offset, offset + size),
                    )
                }
            }
        }

        fun setValue(value: Value) {

            assert(type.pointerType == value.type)

            when (type.pointerType) {
                is PrimitiveDataType -> {
                    frame.data[offset] = value.primitive
                }

                is PointerDatatype -> {
                    frame.pointers[offset] = value.pointer!!
                }

                else -> {
                    value.primitiveArray!!.copyInto(frame.data, offset)
                    value.pointerArray!!.copyInto(frame.pointers, offset)
                }
            }
        }

        fun readField(memberName: String): Value {
            val compositeDatatype = type.pointerType

            require(compositeDatatype is CompositeDatatype)

            val memberOffset = fieldOffset(compositeDatatype, memberName)

            val newPointer = Pointer(compositeDatatype.fieldType(memberName).pointerOf(), frame, offset + memberOffset)
            return Value(newPointer.type, pointer = newPointer)
        }

        fun arrayIndex(index: Int): Value {

            val rawArrayType = type.pointerType
            require(rawArrayType is RawArrayDatatype)

            val arrayElementType = rawArrayType.arrayType
            val elementOffset = sizeOf(arrayElementType) * index

            val newPointer = Pointer(arrayElementType.pointerOf(), frame, offset + elementOffset)
            return Value(newPointer.type, pointer = newPointer)
        }
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

    lateinit var globalVariables: WalkFrame

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
        val globals = intermediateProgram.symbolTable.getAllGlobalVariables()
        val allGlobalsFields = globals.map { it.name to it.datatype }

        globalVariables = WalkFrame(createTemplate(allGlobalsFields))

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

    fun walkUserFunction(userFunction: UserFunction, parameters: List<Value>): Value {

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
            val resultPointer = currentFrame.getVariable(RETURN_VALUE_NAME).pointer!!
            require(resultPointer.type.pointerType == userFunction.definition.returnType)

            resultPointer.getDeref()
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

            is Store -> {
                val value = getValueOf(instruction.value)

                val destination = getValueOf(instruction.destination)
                assert(destination.type is PointerDatatype)

                destination.pointer!!.setValue(value)

            }

            is ReturnNothing -> {
                return ControlFlow.Return to null
            }

            else -> throw NotImplementedError(instruction.toString())
        }

        return ControlFlow.Normal to null
    }

    private fun jumpHelper(
        condition: ValueExpr,
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

    private fun getValueOf(value: ValueExpr): Value {

        return when (value) {
            is IntConstant -> Value(Primitives.Integer, primitive = value.value)
            is LocalValueRef -> currentFrame.getVariable(value.name)
            is GlobalValueRef -> globalVariables.pointer(value.name)
            is Call -> {
                val arguments = value.params.map { getValueOf(it) }

                val function = getFunctionFromSignature(value.func)

                return call(function, arguments)
            }

            is Load -> {
                val pointer = getValueOf(value.value)
                return pointer.pointer!!.getDeref()
            }

            is AllocStack -> {
                val pointer = currentFrame.stackDynamicAlloc(value.allocType)
                return pointer
            }

            is AllocStackArray -> {
                val arraySize = getValueOf(value.size)
                require(arraySize.type == Primitives.Integer)

                return currentFrame.stackDynamicArrayAlloc(value.arrayType, arraySize.primitive)
            }

            is GetMemberPtr -> {
                val pointer = getValueOf(value.value)

                val newPointer = pointer.pointer!!.readField(value.memberName)

                return newPointer
            }

            is GetElementPtr -> {
                val pointer = getValueOf(value.value).pointer!!

                val index = getValueOf(value.index)
                require(index.type == Primitives.Integer)

                return pointer.arrayIndex(index.primitive)
            }

            else -> TODO(value.toString())
        }
    }
}
