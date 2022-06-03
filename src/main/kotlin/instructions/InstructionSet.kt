package se.wingez.instructions

import se.wingez.emulator.Emulator
import se.wingez.instructions.Instruction.Companion.MNEMONIC_DELIMITERS
import se.wingez.splitMany
import java.io.StringReader

class RegisterInstructionError(message: String) : Exception(message)
class InstructionBuilderError(message: String) : Exception(message)
class AssembleError(message: String) : Exception(message)

data class Instruction(
    val mnemonic: String,

    val emulate: (Emulator) -> Unit,
    var id: UByte = AUTO_INDEX_ASSIGMENT,
    val group: String = "",
    var variableOrder: List<String> = emptyList(),
    var variableSizes: Map<String, Int> = emptyMap()
) {
    companion object {
        const val AUTO_INDEX_ASSIGMENT: UByte = 255u
        const val MAX_SIZE: UByte = 254u
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


    fun build(args: Map<String, Int> = emptyMap()): List<UByte> {
        val mutableArgs = args.toMutableMap()
        val result = mutableListOf(id)
        for (variableName in variableOrder) {
            if (variableName !in args) {
                throw InstructionBuilderError("A variable with name $variableName must be provided")
            }

            var value = mutableArgs.getValue(variableName)
            val size = variableSizes.getValue(variableName)
            val maxVal = (1 shl (size * 8)) - 1

            if (value < 0 || value > maxVal) {
                throw InstructionBuilderError("Variable $variableName must be within (0, $maxVal), not $value")
            }

            for (i in 0 until size) {
                result.add((value and 0xff).toUByte())
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

    fun createInstruction(
        mnemonic: String,
        index: UByte = Instruction.AUTO_INDEX_ASSIGMENT,
        group: String = "",
        emulate: (Emulator) -> Unit
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

    fun instructionFromID(id: UByte): Instruction {
        if (id !in instructionByIndex)
            throw InstructionBuilderError(id.toString())
        return instructionByIndex.getValue(id)
    }


    fun assembleMnemonic(mnemonic: String): List<UByte> {

        val assembler = Assembler(this)
        assembler.assembleMnemonic(mnemonic)
        return assembler.getResultingCode()
    }

    fun assembleMnemonicFile(file: String): List<UByte> {
        return assembleMnemonicFile(StringReader(file))
    }

    fun assembleMnemonicFile(file: StringReader): List<UByte> {
        val assembler = Assembler(this)

        file.forEachLine {
            assembler.assembleMnemonic(it.trim('\n'))
        }
        return assembler.getResultingCode()
    }

    fun disassemble(code: List<UByte>): List<String> {
        return disassembleWithIndex(code).entries.sortedBy { it.key }.map { it.value }
    }

    fun disassembleWithIndex(code: List<UByte>): Map<Int, String> {
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

private class Assembler(
    private val instructionSet: InstructionSet,
) {

    private val scopes = mutableListOf<MutableMap<String, String>>()

    init {
        pushScope()
    }


    private val currentCode = mutableListOf<UByte>()


    private fun pushScope() {
        scopes.add(mutableMapOf())
    }

    private fun popScope() {
        if (scopes.isEmpty()) {
            throw AssembleError("No scope to pop")
        }

        scopes.removeLast()
    }

    private fun adjustForBrackets(mnemonic: String): String {
        // TODO fix this hack
        // Add spaces after each token so we can treat them as a regular word
        var result = mnemonic
        for (letter in listOf("[", "]")) {
            result = result.replace(letter, " $letter ")
        }

        return result
    }

    private fun setVariable(variable: String, value: String) {
        val topScope = scopes.last()
        if (variable in topScope) {
            throw AssembleError("Cannot reassign variable: $variable")
        }
        topScope[variable] = value
    }

    private fun getVariable(variable: String): String {
        for (scope in scopes.reversed()) {
            if (variable in scope) {
                return scope.getValue(variable)
            }
        }
        throw AssembleError("No variable with name: $variable")
    }

    private fun getVariableOrConstant(value: String): Int {

        var toConvert = value

        if (!value.all { it.isDigit() }) {
            toConvert = getVariable(value)
        }

        return toConvert.toInt()
    }

    fun assembleMnemonic(mnemonic: String) {
        val trimmedMnemonic = adjustForBrackets(mnemonic).trim(' ')

        // Filter empty lines and comments
        if (trimmedMnemonic.isEmpty() || trimmedMnemonic.startsWith("//"))
            return

        if (trimmedMnemonic == "scope") {
            pushScope()
            return
        } else if (trimmedMnemonic == "endscope") {
            popScope()
            return
        }

        if (trimmedMnemonic.startsWith("#")) {
            val (name, value) = trimmedMnemonic.trimStart('#')
                .split('=').map { it.trim(' ') }
            setVariable(name, value)
            return
        }

        for (instr in instructionSet.getInstructions()) {

            val variables = mutableMapOf<String, Int>()

            val templateSplit =
                splitMany(adjustForBrackets(instr.mnemonic), MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }
            val mnemSplit = splitMany(trimmedMnemonic, MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }

            if (templateSplit.size != mnemSplit.size)
                continue

            var allMatch = true
            for ((templateWord, mnemWord) in templateSplit.zip(mnemSplit)) {
                if ('#' in templateWord && '#' in mnemWord) {
                    //Variable
                    val index = templateWord.indexOf("#")
                    if (index != mnemWord.indexOf("#")) {
                        allMatch = false
                        break
                    }
                    //check variable name matches
                    if (templateWord.lowercase().substring(0, index) !=
                        mnemWord.lowercase().substring(0, index)
                    ) {
                        allMatch = false
                        break
                    }

                    variables[templateWord.substring(index).trimStart('#')] =
                        getVariableOrConstant(mnemWord.substring(index).trimStart('#'))

                } else if (templateWord.lowercase() != mnemWord.lowercase()) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) {
                currentCode.addAll(instr.build(variables))
                return
            }
        }
        throw InstructionBuilderError("No instruction matches $mnemonic")
    }

    fun getResultingCode(): List<UByte> {
        popScope()

        if (scopes.isNotEmpty()) {
            throw AssembleError("Forgot to close a scope??")
        }

        return currentCode
    }

}