package se.wingez.instructions

import se.wingez.Utils
import se.wingez.emulator.Emulator
import java.lang.Exception

class RegisterInstructionError(message: String) : Exception(message)
class InstructionBuilderError(message: String) : Exception(message)


data class Instruction(
        val mnemonic: String,

        val emulate: (Emulator) -> Boolean,
        var id: UByte = AUTO_INDEX_ASSIGMENT,
        var variableOrder: List<String> = emptyList()
) {
    companion object {
        const val AUTO_INDEX_ASSIGMENT: UByte = 255u
        const val MAX_SIZE: UByte = 254u
        val MNEMONIC_DELIMITERS = listOf(" ", ",")
    }

    val name: String

    init {
        val words = Utils.splitMany(mnemonic, MNEMONIC_DELIMITERS)
        name = words[0]

        val variables = words.filter { '#' in it }.map { it.trimStart('#', '-') }.toList()
        if (variableOrder.isNotEmpty()) {
            // Check so all necessary variables is provided
            if (variableOrder.toSet() != variables.toSet()) {
                throw RegisterInstructionError("VariablesOrder should contain ${variables.toSet()}")
            }
        } else {
            variableOrder = variables
        }
    }

    val size = { 1 + variableOrder.size }

    fun build(args: Map<String, UByte> = emptyMap()): List<UByte> {
        val mutableArgs = args.toMutableMap()
        val result = mutableListOf(id)
        for (variableName in variableOrder) {
            if (variableName !in args) {
                throw InstructionBuilderError("A variable with name $variableName must be provided")
            }
            result.add(mutableArgs.getValue(variableName))
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

class InstructionSet(val maxSize: UByte = Instruction.MAX_SIZE) {

    val instructionByIndex = mutableMapOf<UByte, Instruction>()

    fun nextVacantIndex(): UByte {
        for (i in 0 until maxSize.toInt()) {
            val byte = i.toUByte()
            if (byte !in instructionByIndex)
                return byte
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

    fun createInstruction(mnemonic: String, index: UByte = Instruction.AUTO_INDEX_ASSIGMENT, emulate: (Emulator) -> Boolean) {
        addInstruction(Instruction(
                mnemonic,
                emulate,
                index
        ))
    }

    fun getInstructions(): Collection<Instruction> {
        return instructionByIndex.values
    }

}