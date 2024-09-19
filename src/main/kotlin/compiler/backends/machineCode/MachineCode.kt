package compiler.backends.machineCode

import compiler.BuiltInSignatures
import compiler.frontend.*
import requireNotReached


enum class Register {
    RDI, RSI,
    RDX, RCX,

    R8, R9, R10, R11,
    R12, R13, R14, R15,
    RAX, RBX,
}

val callRegisterOrder = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX)

val calleeSavedRegisters = listOf(Register.R12, Register.R13, Register.R14, Register.R15, Register.RBX)

val temporaryRegisters = calleeSavedRegisters


data class Constant(val value: Int) : DataItem, ValueState {
    override fun generateAssembly(): String {
        return "$$value"
    }

    override fun debugMsg(): String {
        return "Constant: $value"
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

private class BinaryOpInstruction(val type: BinaryOpType, val dest: DataItem, val src: DataItem) : Instruction {
    private fun instr(): String {
        return when (type) {
            BinaryOpType.Add -> "add"
            BinaryOpType.Sub -> "sub"

        }
    }

    override fun generateAssembly(): List<String> {
        return listOf("${instr()}\t${src.generateAssembly()}, ${dest.generateAssembly()}")
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

private data class StackVariable(val offset: Int) : ValueState, DataItem {
    override fun debugMsg(): String {
        return "Stack offset: $offset"
    }

    override fun generateAssembly(): String {
        return "-$offset(%rbp)"
    }
}

private class LoadValueAt(val source: ValueState) : ValueState {
    override fun debugMsg(): String {
        return "Value at [${source.debugMsg()}]"
    }
}

private data class InRegister(val register: Register) : ValueState, DataItem {
    override fun debugMsg(): String {
        return "In register ${register.name}"
    }

    override fun generateAssembly(): String {
        val label = when (register) {
            Register.RDI -> "edi"
            Register.RSI -> "esi"
            Register.RDX -> "edx"
            Register.RCX -> "ecx"
            Register.RAX -> "eax"
            Register.RBX -> "rbx"
            Register.R8 -> "r8d"
            Register.R9 -> "r9d"
            Register.R10 -> "r10d"
            Register.R11 -> "r11d"
            Register.R12 -> "r12d"
            Register.R13 -> "r13d"
            Register.R14 -> "r14d"
            Register.R15 -> "r15d"
        }
        return "%$label"
    }
}

private enum class BinaryOpType {
    Add,
    Sub,
}

private class BinaryOp(val type: BinaryOpType, val left: ValueState, val right: ValueState) : ValueState {
    override fun debugMsg(): String {

        val op = when (type) {
            BinaryOpType.Add -> "+"
            BinaryOpType.Sub -> "-"
        }

        return "(${left.debugMsg()} $op ${right.debugMsg()})"
    }
}


class Emitter(val function: FunctionContent) {

    private val generatedInstructions = mutableListOf<Instruction>()

    private val tempValueStates = mutableMapOf<String, ValueState>()

    private var staticStackSize = 0

    private var numUsedLocalRegisters = 0

    lateinit var allocations: Map<String, AllocationResult>


    fun emit(instruction: Instruction) {
        generatedInstructions.add(instruction)
        instruction.generateAssembly()
            .forEach { println(it) }
    }


    private fun setValueState(value: String, state: ValueState) {
        require(value !in tempValueStates)
        tempValueStates[value] = state

        println("Set value '$value' to ${state.debugMsg()}")
    }

    private fun getValueState(valueExpr: ValueExpr): ValueState {

        return when (valueExpr) {
            is LocalValueRef -> tempValueStates.getValue(valueExpr.name)
            is IntConstant -> Constant(valueExpr.value)
            else -> TODO(valueExpr.toString())
        }
    }

    private fun handleInstruction(instruction: IRinstruction) {

        when (instruction) {
            is TempValue -> {

                val tempValue = instruction.name
                val valueState = when (instruction.value) {
                    is AllocStack -> {
                        val allocType = instruction.value.allocType
                        val size = sizeOf(allocType)

                        staticStackSize += size

                        StackVariable(staticStackSize)
                    }

                    is Load -> {
                        val toLoad = instruction.value.value
                        require(toLoad is LocalValueRef)
                        getValueState(toLoad)
                    }

                    is Call -> {
                        val call = instruction.value

                        val isExternal = isExternal(call.func)

                        val params = call.params.map { getValueState(it) }

                        if (!isExternal) {
                            mapBuiltIn(call.func, params)
                        } else {
                            doExternalCall(call.func, params)
                        }
                    }

                    else -> TODO()
                }

                val resultState: ValueState

                if (tempValue in allocations) {

                    val allocation = allocations.getValue(tempValue)

                    resultState = when (allocation.type) {
                        AllocationResultType.Unused -> {
                            valueState
                        }

                        AllocationResultType.InRegister -> {
                            val register = InRegister(allocation.register!!)
                            generateMoveData(valueState, register)
                            register
                        }

                        else -> TODO(allocation.type.toString())
                    }
                } else {
                    resultState = valueState
                }

                println("SetvalueState: $tempValue")
                setValueState(tempValue, resultState)
            }

            is Store -> {
                val destination = when (val state = getValueState(instruction.destination)) {
                    is StackVariable -> {
                        state
                    }

                    else -> TODO()
                }

                generateMoveData(getValueState(instruction.value), destination)
            }

            is ReturnNothing -> {
                emit(ReturnInstruction())
            }

            is Return -> {
                generateMoveData(getValueState(instruction.value), InRegister(Register.RAX))
                emit(ReturnInstruction())
            }

            else -> TODO(instruction.toString())
        }
    }


    private fun doExternalCall(func: FunctionDefinition, parameters: List<ValueState>): ValueState {
        for ((index, parameter) in parameters.withIndex()) {

            val destination = callRegisterOrder[index]

            generateMoveData(parameter, InRegister(destination))
        }

        // TODO store/restore temp variables

        emit(CallInstruction(func))

        return InRegister(Register.RAX)
    }


    private fun simplify(toSimplify: ValueState): ValueState {

        when (toSimplify) {
            is BinaryOp -> {
                val left = simplify(toSimplify.left)
                val right = simplify(toSimplify.right)

                if (left is Constant && right is Constant) {
                    val result = when (toSimplify.type) {
                        BinaryOpType.Add -> left.value + right.value
                        BinaryOpType.Sub -> left.value - right.value
                    }
                    return Constant(result)
                }
                return BinaryOp(toSimplify.type, left, right)
            }
        }

        return toSimplify
    }


    private fun generateMoveData(source: ValueState, target: DataItem) {
        val sourceSimplified = simplify(source)

        when (sourceSimplified) {
            is Constant -> {
                emitMove(sourceSimplified, target)
            }

            is InRegister -> {
                emitMove(sourceSimplified, target)
            }

            is StackVariable -> {
                emitMove(sourceSimplified, target)
            }

            is BinaryOp -> {

                val op = sourceSimplified

                //TODO
                require(target is InRegister || target is StackVariable)

                generateMoveData(op.left, target)


                //TODO

                require(op.right is Constant || op.right is InRegister || op.right is StackVariable)
                val src: DataItem = op.right as DataItem
                emit(BinaryOpInstruction(op.type, target, src))

            }


            else -> TODO(sourceSimplified.toString())
        }
    }


//    private fun moveToRegister(valueExpr: ValueExpr, register: Register) {
//
//        val destination = RegisterData(register)
//
//        when (valueExpr) {
//            is LocalValueRef -> {
//                when (val state = tempValueStates.getValue(valueExpr.name)) {
//                    is StackVariable -> {
//                        emit(MoveInstruction(state, destination))
//                    }
//
//                    is InRegister -> {
//                        emit(MoveInstruction(state, destination))
//                    }
//
//                    else -> TODO(state.toString())
//                }
//            }
//
//            is IntConstant -> {
//                emit(MoveInstruction(Constant(valueExpr.value), destination))
//            }
//
//            else -> TODO()
//        }
//    }

    fun emitMove(from: Register, to: Register) {

        emitMove(InRegister(from), InRegister(to))
    }

    fun emitMove(from: DataItem, to: DataItem) {
        if (from == to) {
            return
        }
        emit(MoveInstruction(from, to))
    }

    fun build(): List<String> {

        allocations = doInitialPass(function.definition, function.instructions.map { it.first })


        val parameterMoves = mutableListOf<Pair<Register, Register>>()

        for ((index, param) in function.definition.parameters.withIndex()) {

            val (paramName, paramType) = param

            val allocation = allocations.getValue(paramName)

            val sourceRegister = callRegisterOrder[index]

            val targetRegister = when (allocation.type) {
                AllocationResultType.Unused -> sourceRegister
                AllocationResultType.InRegister -> allocation.register!!
                else -> TODO()
            }

            setValueState(paramName, InRegister(targetRegister))
            if (sourceRegister != targetRegister) {
                parameterMoves.add(sourceRegister to targetRegister)
            }
        }

        while (parameterMoves.isNotEmpty()) {
            for ((index, move) in parameterMoves.withIndex()) {
                val (from, to) = move
                if (to in parameterMoves.map { it.first }) {
                    continue
                }
                emitMove(from, to)
                parameterMoves.removeAt(index)
                break
            }
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

fun mapBuiltIn(functionDefinition: FunctionDefinition, values: List<ValueState>): ValueState {

    val binary = when (functionDefinition) {
        BuiltInSignatures.add -> BinaryOpType.Add
        BuiltInSignatures.sub -> BinaryOpType.Sub
        else -> null
    }

    if (binary != null) {
        require(values.size == 2)
        return BinaryOp(binary, values[0], values[1])
    }

    requireNotReached()
}




