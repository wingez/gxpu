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
    fun isIndent(): Boolean = true
    fun generateAssembly(): List<String>

    fun generateWithIndent(): List<String> {
        if (isIndent()) {
            return generateAssembly().map { "\t$it" }
        }
        return generateAssembly()
    }
}


interface DataItem {
    fun generateAssembly(): String
}

class LabelInstruction(val label: String) : Instruction {
    override fun isIndent(): Boolean = false
    override fun generateAssembly(): List<String> {
        return listOf("$label:")
    }
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

class CompareInstruction(val left: DataItem, val right: DataItem) : Instruction {
    override fun generateAssembly(): List<String> {
        return listOf("cmp\t${left.generateAssembly()}, ${right.generateAssembly()}")
    }
}

class JumpInstruction(val label: String) : Instruction {
    override fun generateAssembly(): List<String> {
        return listOf("jmp $label")
    }
}

private class JumpIf(val condition: BinaryOpType, val label: String) : Instruction {
    override fun generateAssembly(): List<String> {
        val inst = when (condition) {
            BinaryOpType.NotEquals -> "jne"
            BinaryOpType.Equals -> "je"
            BinaryOpType.Greater -> "jg"
            BinaryOpType.GreaterOrEqual -> "jge"
            BinaryOpType.Less -> "jl"
            BinaryOpType.LessOrEqual -> "jle"
            else -> requireNotReached(condition.toString())
        }

        return listOf("$inst $label")

    }
}

private class BinaryOpInstruction(val type: BinaryOpType, val dest: DataItem, val src: DataItem) : Instruction {
    private fun instr(): String {
        return when (type) {
            BinaryOpType.Add -> "add"
            BinaryOpType.Sub -> "sub"


            BinaryOpType.Equals, BinaryOpType.NotEquals, BinaryOpType.Greater, BinaryOpType.GreaterOrEqual, BinaryOpType.Less, BinaryOpType.LessOrEqual -> requireNotReached()
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

    Equals,
    NotEquals,
    Greater,
    Less,
    GreaterOrEqual,
    LessOrEqual,
}

private class BinaryOp(val type: BinaryOpType, val left: ValueState, val right: ValueState) : ValueState {
    override fun debugMsg(): String {

        val op = when (type) {
            BinaryOpType.Add -> "+"
            BinaryOpType.Sub -> "-"
            BinaryOpType.Equals -> "-"
            BinaryOpType.NotEquals -> "-"
            BinaryOpType.Greater -> ">"
            BinaryOpType.GreaterOrEqual -> ">="
            BinaryOpType.Less -> "<"
            BinaryOpType.LessOrEqual -> "<="
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
        val simplifiedState = simplify(state)
        tempValueStates[value] = simplifiedState

        println("Set value '$value' to ${simplifiedState.debugMsg()}")
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

            is JumpOnFalse -> jumpHelper(instruction.condition, instruction.label, false)
            is JumpOnTrue -> jumpHelper(instruction.condition, instruction.label, true)


            is Jump -> {
                emit(JumpInstruction(getLabel(instruction.label)))
            }

            else -> TODO(instruction.toString())
        }
    }

    private fun jumpHelper(condition: ValueExpr, label: Label, jumpOn: Boolean) {

        val conditionState = getValueState(condition)

        //If constant we can just skip it altogether
        if (conditionState is Constant) {
            if (conditionState.value.toBool() == jumpOn) {
                emit(JumpInstruction(getLabel(label)))
            }
            return
        }

        //Check if value is a binaryOp we can generate
        if (conditionState is BinaryOp && conditionState.type in listOf(
                BinaryOpType.Equals,
                BinaryOpType.NotEquals,
                BinaryOpType.Greater,
                BinaryOpType.GreaterOrEqual,
                BinaryOpType.Less,
                BinaryOpType.LessOrEqual
            )
        ) {
            //
            var left = conditionState.left
            var right = conditionState.right
            var type = conditionState.type

            if (right is Constant) {
                val temp = left
                left = right
                right = temp
                type = type.invertComparison()
            }

            generateMoveData(right, InRegister(Register.RAX))

            require(left is DataItem)

            emit(CompareInstruction(left, InRegister(Register.RAX)))
            emit(JumpIf(type, getLabel(label)))
            return
        }

        generateMoveData(conditionState, InRegister(Register.RAX))
        emit(CompareInstruction(Constant(0), InRegister(Register.RAX)))
        emit(JumpIf(BinaryOpType.Equals, getLabel(label)))
    }

    private fun getLabel(label: Label): String {
        return ".${function.definition.functionName}_${label.identifier}"
    }

    private fun moveToDataItem(value: ValueState): DataItem {
        return when (value) {
            is Constant -> value
            is StackVariable -> value
            else -> TODO(value.toString())
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
                        BinaryOpType.Equals -> (left.value == right.value).toInt()
                        BinaryOpType.NotEquals -> (left.value != right.value).toInt()
                        BinaryOpType.Greater -> (left.value > right.value).toInt()
                        BinaryOpType.GreaterOrEqual -> (left.value >= right.value).toInt()
                        BinaryOpType.Less -> (left.value < right.value).toInt()
                        BinaryOpType.LessOrEqual -> (left.value <= right.value).toInt()
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

                if (src is StackVariable && target is StackVariable) {
                    // Only one memory address allowed
                    // TODO We can make this look more nice by first loading LEFT to RAX and performing the operation there and then moving back

                    generateMoveData(src, InRegister(Register.RAX))
                    emit(BinaryOpInstruction(op.type, target, InRegister(Register.RAX)))



                } else {
                    emit(BinaryOpInstruction(op.type, target, src))

                }
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

        if (from is StackVariable && to is StackVariable) {
            // Cannot move between memory adresses
            emitMove(from, InRegister(Register.RAX))
            emitMove(InRegister(Register.RAX), to)
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


        for ((instr, labels) in function.instructions) {
            for (label in labels) {
                emit(LabelInstruction(getLabel(label)))
            }
            handleInstruction(instr)

        }

        return generateStringOutput()

    }


    private fun generateStringOutput(): List<String> {

        val result = mutableListOf<String>()

        result.addAll(generateHeader(function.definition.functionName, staticStackSize))


        result.addAll(generatedInstructions.flatMap { it.generateWithIndent() })


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


    // Check binary
    val binary = when (functionDefinition) {
        BuiltInSignatures.add -> BinaryOpType.Add
        BuiltInSignatures.sub -> BinaryOpType.Sub
        BuiltInSignatures.equals -> BinaryOpType.Equals
        BuiltInSignatures.notEquals -> BinaryOpType.NotEquals
        BuiltInSignatures.greaterThan -> BinaryOpType.Greater
        BuiltInSignatures.lessThan -> BinaryOpType.Less
        else -> null
    }

    if (binary != null) {
        require(values.size == 2)
        return BinaryOp(binary, values[0], values[1])
    }

    // Check bool
    if (functionDefinition == BuiltInSignatures.bool) {
        require(values.size == 1)
        return values[0]
    }


    requireNotReached(functionDefinition.toString())
}

private fun Boolean.toInt(): Int {
    return if (this) 1 else 0
}

private fun Int.toBool(): Boolean {
    return this != 0
}

private fun BinaryOpType.invertComparison(): BinaryOpType {
    return when (this) {
        BinaryOpType.Equals -> BinaryOpType.NotEquals
        BinaryOpType.NotEquals -> BinaryOpType.Equals
        BinaryOpType.Greater -> BinaryOpType.LessOrEqual
        BinaryOpType.GreaterOrEqual -> BinaryOpType.Less
        BinaryOpType.Less -> BinaryOpType.GreaterOrEqual
        BinaryOpType.LessOrEqual -> BinaryOpType.Greater
        else -> requireNotReached(this.toString())
    }
}

