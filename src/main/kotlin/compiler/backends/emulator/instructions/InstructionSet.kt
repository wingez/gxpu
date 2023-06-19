package compiler.backends.emulator.instructions

import compiler.backends.emulator.emulator.Emulator
import compiler.backends.emulator.EmulatorInstruction
import splitMany
import java.io.StringReader

class RegisterInstructionError(message: String) : Exception(message)
class InstructionBuilderError(message: String) : Exception(message)

data class Instruction(
    val mnemonic: String,

    val emulate: (Emulator, Map<String, Int>) -> Unit,
    var id: Int = AUTO_INDEX_ASSIGMENT,
    val group: String = "",
    var variableOrder: List<String> = emptyList(),
    var variableSizes: Map<String, Int> = emptyMap()
) {
    companion object {
        const val AUTO_INDEX_ASSIGMENT: Int = -1
        const val MAX_SIZE: Int = 254
        val MNEMONIC_DELIMITERS = listOf(" ", ",")
    }

    val name: String

    init {
        val words = splitMany(mnemonic, listOf(" ", ",", "[", "]"))
        name = words[0]

        val variables = words.filter { '#' in it }.map {
            Pair(it.trimStart('#', '-'), it.count { it == '#' })
        }.toList()

        val variableNames = variables.map { it.first }

        if (variableOrder.isNotEmpty()) {
            // Check so all necessary variables is provided
            if (variableOrder.toSet() != variables.map { it.first }.toSet()) {
                throw RegisterInstructionError("VariablesOrder should contain ${variables.toSet()}")
            }
        } else {
            variableOrder = variableNames
        }

        variableSizes = variables.toMap()

    }

    val size
        get() = 1 + variableOrder.sumOf { variableSizes.getValue(it) }


    fun build(args: Map<String, Int> = emptyMap()): List<Int> {
        val mutableArgs = args.toMutableMap()
        val result = mutableListOf(id)
        for (variableName in variableOrder) {
            if (variableName !in args) {
                throw InstructionBuilderError("A variable with name $variableName must be provided")
            }

            var value = mutableArgs.getValue(variableName)
            val size = variableSizes.getValue(variableName)
            val maxVal = (1 shl (size * 8)) - 1

            if (value < -127 || value > maxVal) {
                throw InstructionBuilderError("Variable $variableName must be within (-127, $maxVal), not $value")
            }

            for (i in 0 until size) {
                result.add((value and 0xff))
                value = value shr 8

            }

            mutableArgs.remove(variableName)
        }
        if (mutableArgs.isNotEmpty()) {
            throw InstructionBuilderError("Instruction $mnemonic does not take variables $mutableArgs")
        }
        return result
    }

    fun getPositionOfVariable(variable: String): Int {
        if (variable !in variableOrder)
            throw InstructionBuilderError("Variable $variable is not part of this instruction")
        return variableOrder.indexOf(variable)
    }
}

class InstructionSet(val maxSize: Int = Instruction.MAX_SIZE) {

    val instructionByIndex = mutableMapOf<Int, Instruction>()

    fun nextVacantIndex(): Int {
        for (i in 0 until maxSize) {
            if (i !in instructionByIndex)
                return i
        }
        throw RegisterInstructionError("Maximum number of instructions reached")
    }

    fun addInstruction(instruction: Instruction) {
        if (instruction.id == Instruction.AUTO_INDEX_ASSIGMENT) {
            instruction.id = nextVacantIndex()
        }

        if (instruction.id in instructionByIndex)
            throw RegisterInstructionError("An instruction with id ${instruction.id} already exists")
        if (instruction.id > maxSize)
            throw RegisterInstructionError("Instructions already at max capacity")

        instructionByIndex[instruction.id] = instruction
    }

    fun createInstruction(
        mnemonic: String,
        index: Int = Instruction.AUTO_INDEX_ASSIGMENT,
        group: String = "",
        emulate: (Emulator, Map<String, Int>) -> Unit
    ): Instruction {
        val instr = Instruction(
            mnemonic,
            emulate,
            index,
            group,
        )
        addInstruction(instr)
        return instr
    }

    fun getInstructions(): Collection<Instruction> {
        return instructionByIndex.values
    }

    fun instructionFromID(id: Int): Instruction {
        if (id !in instructionByIndex)
            throw InstructionBuilderError(id.toString())
        return instructionByIndex.getValue(id)
    }


    fun assembleMnemonic(mnemonic: String): List<EmulatorInstruction> {

        val assembler = Assembler(listOf(mnemonic), this)
        return assembler.getResultingInstructions()
    }

    fun assembleMnemonicFile(file: String): List<EmulatorInstruction> {
        return assembleMnemonicFile(StringReader(file))
    }

    fun assembleMnemonicFile(file: StringReader): List<EmulatorInstruction> {

        val lines = file.readLines().map { it.trim('\n') }.toList()

        val assembler = Assembler(lines, this)

        return assembler.getResultingInstructions()
    }

    fun disassemble(code: List<Int>): List<String> {
        return disassembleWithIndex(code).entries.sortedBy { it.key }.map { it.value }
    }

    fun disassembleWithIndex(code: List<Int>): Map<Int, String> {
        var index = 0
        val result = mutableMapOf<Int, String>()

        while (index < code.size) {
            val instructionId = code[index]
            val instr = instructionFromID(instructionId)

            var out = instr.mnemonic

            instr.variableOrder.forEach {
                val value = code[index + 1 + instr.getPositionOfVariable(it)]
                out = out.replace("#$it", "#$value")
            }

            result[index] = out
            index += instr.size

        }
        return result
    }


    fun describeInstructions(): Iterable<String> {
        val groupKeySelector = { instruction: Instruction -> instruction.group }

        val instructions = getInstructions().sortedBy(groupKeySelector)

        val result = mutableListOf<String>()

        instructions.groupBy(groupKeySelector).forEach { (group, instr) ->
            result.add("Group: ${group.ifEmpty { "not set" }}")

            instr.sortedBy { it.id }.forEach {
                result.add("${it.id.toString().padStart(3)}: ${it.mnemonic}")
            }
        }
        return result
    }
}
