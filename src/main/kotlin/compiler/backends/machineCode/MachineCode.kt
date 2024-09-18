package compiler.backends.machineCode

import compiler.frontend.*
import requireNotReached


enum class Register {
    RDI, RSI,
    RDX, RCX,

    R8, R9, R10, R11,
    RAX, RBX,
}


class RegisterData(private val register: Register) : DataItem {
    override fun generateAssembly(): String {
        val label = when (register) {
            Register.RDI -> "edi"
            Register.RSI -> "esi"
            Register.RAX -> "eax"
            Register.RBX -> "rbx"
            Register.R8 -> "r8d"
            Register.R9 -> "r9d"
            Register.R10 -> "r10d"
            Register.R11 -> "r11d"
            else -> TODO(register.toString())
        }
        return "%$label"
    }
}

val callRegisterOrder = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX)

val temporaryRegisters = listOf(Register.R8, Register.R9, Register.R10, Register.R11)


class Constant(val value: Int) : DataItem {
    override fun generateAssembly(): String {
        return "$$value"
    }
}

interface Instruction {
    fun generateAssembly(): List<String>
}


interface DataItem {
    fun generateAssembly(): String
}


class MoveInstruction(private val from: DataItem, val to: DataItem) : Instruction {


    override fun generateAssembly(): List<String> {
        return listOf("movl\t${from.generateAssembly()}, ${to.generateAssembly()}")
    }
}

class CallInstruction(val toCall: FunctionDefinition) : Instruction {
    override fun generateAssembly(): List<String> {
        return listOf("call ${toCall.functionName}")
    }
}

class ReturnInstruction() : Instruction {
    override fun generateAssembly(): List<String> {
        return listOf(
            "movq\t%rbp, %rsp",
            "popq\t%rbp",
            "ret",
        )
    }
}

fun generateHeader(functionName: String, staticStackSize: Int): List<String> {
    return listOf(
        "\t.text",
        "\t.globl $functionName",
        "$functionName:",
        "\tpushq\t%rbp",
        "\tmovq\t%rsp, %rbp",
        "\tsubq\t$$staticStackSize, %rsp"
    )
}

fun sizeOf(datatype: Datatype): Int {
    return when (datatype) {
        Primitives.Integer -> 4
        else -> TODO()
    }
}

interface ValueState {
    fun debugMsg(): String
}

private class StackVariable(val offset: Int) : ValueState, DataItem {
    override fun debugMsg(): String {
        return "Stack offset: $offset"
    }

    override fun generateAssembly(): String {
        return "-$offset(%rbp)"
    }
}

private class InRegister(val register: Register) : ValueState, DataItem {
    override fun debugMsg(): String {
        return "In register ${register.name}"
    }

    override fun generateAssembly(): String {
        return RegisterData(register).generateAssembly()
    }
}

class Emitter(val function: FunctionContent) {

    private val generatedInstructions = mutableListOf<Instruction>()

    private val tempValueStates = mutableMapOf<String, ValueState>()

    private var staticStackSize = 0

    private var numUsedLocalRegisters = 0


    fun emit(instruction: Instruction) {
        generatedInstructions.add(instruction)
        instruction.generateAssembly()
            .forEach { println(it) }
    }

    private fun nextTemporaryRegister(): Register {
        if (numUsedLocalRegisters >= temporaryRegisters.size) {
            requireNotReached()
        }
        return temporaryRegisters[numUsedLocalRegisters++]
    }

    private fun setValueState(value: String, state: ValueState) {
        require(value !in tempValueStates)
        tempValueStates[value] = state

        println("Set value '$value' to ${state.debugMsg()}")
    }

    private fun handleInstruction(instruction: IRinstruction) {

        when (instruction) {
            is TempValue -> {

                val tempValue = instruction.name


                when (instruction.value) {
                    is AllocStack -> {
                        val allocType = instruction.value.allocType
                        val size = sizeOf(allocType)

                        staticStackSize += size
                        setValueState(tempValue, StackVariable(staticStackSize))
                    }

                    is Load -> {
                        val toLoad = instruction.value.value
                        require(toLoad is LocalValueRef)

                        when (val state = tempValueStates.getValue(toLoad.name)) {

                            is StackVariable -> {
                                setValueState(tempValue, state)
                            }

                            else -> TODO()
                        }
                    }

                    is Call -> {
                        //TODO: shelve parameters
                        // Load parameters
                        val call = instruction.value
                        for ((index, parameter) in call.params.withIndex()) {

                            val destination = callRegisterOrder[index]

                            moveToRegister(parameter, destination)
                        }

                        emit(CallInstruction(call.func))


                        //TODO do only if needed
                        val whereToStore = nextTemporaryRegister()
                        emit(MoveInstruction(RegisterData(Register.RAX), RegisterData(whereToStore)))
                        setValueState(tempValue, InRegister(whereToStore))
                    }

                    else -> TODO()
                }
            }

            is Store -> {
//                "movl $8 -4(%rbp)"

                val toStore = when (instruction.value) {
                    is IntConstant -> {
                        Constant(instruction.value.value)
                    }

                    else -> TODO()
                }

                require(instruction.destination is LocalValueRef)
                val destinationName = instruction.destination.name

                val destination = when (val state = tempValueStates.getValue(destinationName)) {
                    is StackVariable -> {
                        state
                    }

                    else -> TODO()
                }

                emit(MoveInstruction(toStore, destination))

            }

            is ReturnNothing -> {
                emit(ReturnInstruction())
            }

            is Return -> {
                moveToRegister(instruction.value, Register.RAX)
                emit(ReturnInstruction())
            }

            else -> TODO(instruction.toString())
        }
    }

    private fun moveToRegister(valueExpr: ValueExpr, register: Register) {

        val destination = RegisterData(register)

        when (valueExpr) {
            is LocalValueRef -> {
                when (val state = tempValueStates.getValue(valueExpr.name)) {
                    is StackVariable -> {
                        emit(MoveInstruction(state, destination))
                    }

                    is InRegister -> {
                        emit(MoveInstruction(state, destination))
                    }

                    else -> TODO(state.toString())
                }
            }

            is IntConstant -> {
                emit(MoveInstruction(Constant(valueExpr.value), destination))
            }

            else -> TODO()
        }
    }

    fun build(): List<String> {


        for ((index, param) in function.definition.parameters.withIndex()) {

            val destination = nextTemporaryRegister()
            emit(MoveInstruction(RegisterData(callRegisterOrder[index]), RegisterData(destination)))
            setValueState(param.first, InRegister(destination))
        }


        for ((instr, label) in function.instructions) {
            handleInstruction(instr)

        }

        return generateStringOutput()

    }


    private fun generateStringOutput(): List<String> {

        val result = mutableListOf<String>()

        result.addAll(generateHeader(function.definition.functionName, staticStackSize))


        result.addAll(generatedInstructions.flatMap { it.generateAssembly() }.map { "\t" + it })


        return result
    }


}


fun buildToAssembly(intermediateProgram: CompiledIntermediateProgram): List<String> {

    val lines = mutableListOf<String>()


    for (func in intermediateProgram.functions) {
        lines.addAll(Emitter(func).build())
    }

    return lines
}


