package se.wingez.instructions

import se.wingez.emulator.Emulator
import se.wingez.instructions.Instruction.Companion.MNEMONIC_DELIMITERS
import se.wingez.splitMany
import java.io.StringReader

class RegisterInstructionError(message: String) : Exception(message)
class InstructionBuilderError(message: String) : Exception(message)


data class Instruction(
        val mnemonic: String,

        val emulate: (Emulator) -> Boolean,
        var id: UByte = AUTO_INDEX_ASSIGMENT,
        val group: String = "",
        var variableOrder: List<String> = emptyList(),
) {
    companion object {
        const val AUTO_INDEX_ASSIGMENT: UByte = 255u
        const val MAX_SIZE: UByte = 254u
        val MNEMONIC_DELIMITERS = listOf(" ", ",")
    }

    val name: String

    init {
        val words = splitMany(mnemonic, MNEMONIC_DELIMITERS)
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

    val size
        get() = 1 + variableOrder.size


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

    fun createInstruction(mnemonic: String, index: UByte = Instruction.AUTO_INDEX_ASSIGMENT, group: String = "", emulate: (Emulator) -> Boolean): Instruction {
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

    fun instructionFromID(id: UByte): Instruction {
        if (id !in instructionByIndex)
            throw InstructionBuilderError(id.toString())
        return instructionByIndex.getValue(id)
    }

    fun assembleMnemonic(mnemonic: String): List<UByte> {
        val trimmedMnemonic = mnemonic.trim(' ')

        // Filter empty lines and comments
        if (trimmedMnemonic.isEmpty() || trimmedMnemonic.startsWith('#'))
            return emptyList()

        for (instr in getInstructions()) {

            val variables = mutableMapOf<String, UByte>()

            val templateSplitted = splitMany(instr.mnemonic, MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }
            val mnemSplitted = splitMany(trimmedMnemonic, MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }

            if (templateSplitted.size != mnemSplitted.size)
                continue

            var allMatch = true
            for ((templateWord, mnemWord) in templateSplitted.zip(mnemSplitted)) {
                if ('#' in templateWord && '#' in mnemWord) {
                    //Variable
                    val index = templateWord.indexOf("#")
                    if (index != mnemWord.indexOf("#")) {
                        allMatch = false
                        break
                    }
                    //check variable name matches
                    if (templateWord.lowercase().substring(0, index) !=
                            mnemWord.lowercase().substring(0, index)) {
                        allMatch = false
                        break
                    }

                    variables[templateWord.substring(index + 1)] = mnemWord.substring(index + 1).toUByte()

                } else if (templateWord.lowercase() != mnemWord.lowercase()) {
                    allMatch = false
                    break
                }
            }
            if (allMatch)
                return instr.build(variables)
        }
        throw InstructionBuilderError("No instruction matches $mnemonic")
    }

    fun assembleMnemonicFile(file: StringReader): List<UByte> {
        val result = mutableListOf<UByte>()

        file.forEachLine {
            result.addAll(assembleMnemonic(it.trim('\n')))
        }
        return result
    }

    fun disassemble(code: List<UByte>): List<String> {
        var index = 0
        val result = mutableListOf<String>()

        while (index < code.size) {
            val instructionId = code[index]
            val instr = instructionFromID(instructionId)

            var out = instr.mnemonic

            instr.variableOrder.forEach {
                val value = code[index + 1 + instr.getPositionOfVariable(it)]
                out = out.replace("#$it", "#$value")
            }

            index += instr.size
            result.add(out)

        }
        return result
    }

    fun describeInstructions(): Iterable<String> {
        val groupKeySelector = { instruction: Instruction -> instruction.group }

        val instructions = getInstructions().sortedBy(groupKeySelector)

        val result = mutableListOf<String>()

        instructions.groupBy(groupKeySelector).forEach { group, instr ->
            result.add("Group: ${group.ifEmpty { "not set" }}")

            instr.sortedBy { it.id }.forEach {
                result.add("${it.id.toString().padStart(3)}: ${it.mnemonic}")
            }
        }
        return result
    }
}