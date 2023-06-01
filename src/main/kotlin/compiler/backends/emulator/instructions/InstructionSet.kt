package se.wingez.compiler.backends.emulator.instructions

import se.wingez.compiler.backends.emulator.emulator.Emulator
import se.wingez.compiler.backends.emulator.instructions.Instruction.Companion.MNEMONIC_DELIMITERS
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

        val assembler = Assembler(listOf(mnemonic), this)
        return assembler.getResultingCode()
    }

    fun assembleMnemonicFile(file: String): List<UByte> {
        return assembleMnemonicFile(StringReader(file))
    }

    fun assembleMnemonicFile(file: StringReader): List<UByte> {

        val lines = file.readLines().map { it.trim('\n') }.toList()

        val assembler = Assembler(lines, this)

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

private data class InstructionParseResult(
    val instruction: Instruction,
    val variableMap: Map<String, String>
)

private class Assembler(
    private val lines: List<String>,
    private val instructionSet: InstructionSet,
) {
    private val scopes = mutableListOf<MutableMap<String, String>>()
    private var currentLine = 0

    private val NEW_SCOPE = "scope"
    private val END_SCOPE = "endscope"
    private val COMMENT = "//"
    private val VARIABLE = "#"
    private val LABEL = ":"


    init {
        pushScope()
    }


    private val currentCode = mutableListOf<UByte>()

    private val currentSize: Int
        get() = currentCode.size

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

        val lookaheadResult = doLabelLoockahead(variable)
        if (lookaheadResult != null) {
            return lookaheadResult.toString()
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

    private fun isEmptyOrComment(line: String): Boolean {
        return line.isEmpty() || line.startsWith(COMMENT)
    }

    private fun isNewScope(line: String): Boolean {
        return line.startsWith(NEW_SCOPE)
    }

    private fun isEndScope(line: String): Boolean {
        return line.startsWith(END_SCOPE)
    }

    private fun isLabel(line: String): Boolean {
        return line.startsWith(LABEL)
    }

    private fun isVariable(line: String): Boolean {
        return line.startsWith(VARIABLE)
    }

    private fun prepareLine(line: String): String {
        return adjustForBrackets(line).trim(' ')
    }

    private fun assembleLine(mnemonic: String) {
        val trimmedMnemonic = prepareLine(mnemonic)

        // Filter empty lines and comments
        if (isEmptyOrComment(trimmedMnemonic))
            return

        if (isNewScope(trimmedMnemonic)) {
            pushScope()
            return
        }
        if (isEndScope(trimmedMnemonic)) {
            popScope()
            return
        }
        if (isLabel(trimmedMnemonic)) {
            val labelName = trimmedMnemonic.substring(LABEL.length)
            setVariable(labelName, currentSize.toString())
            return
        }

        if (isVariable(trimmedMnemonic)) {
            val (name, value) = trimmedMnemonic.substring(VARIABLE.length)
                .split('=').map { it.trim(' ') }
            setVariable(name, value)
            return
        }

        val parseResult = findInstruction(trimmedMnemonic)

        val instructionValues = mutableMapOf<String, Int>()

        for ((variableName, variableValue) in parseResult.variableMap.entries) {
            val value = getVariableOrConstant(variableValue)
            instructionValues.put(variableName, value)
        }

        currentCode.addAll(parseResult.instruction.build(instructionValues))
    }

    fun findInstruction(line: String): InstructionParseResult {
        for (instr in instructionSet.getInstructions()) {

            val variables = mutableMapOf<String, String>()

            val templateSplit =
                splitMany(adjustForBrackets(instr.mnemonic), MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }
            val mnemSplit = splitMany(line, MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }

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
                        mnemWord.substring(index).trimStart('#')

                } else if (templateWord.lowercase() != mnemWord.lowercase()) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) {
                return InstructionParseResult(instr, variables)
            }
        }
        throw InstructionBuilderError("No instruction matches $line")
    }

    fun doLabelLoockahead(labelToSearchFor: String): Int? {

        var scopeNesting = 0
        var offset = currentSize

        for (line in lines.slice(currentLine until lines.size).map { prepareLine(it) }) {
            if (isEmptyOrComment(line) || isVariable(line)) {
                continue
            }
            if (isNewScope(line)) {
                scopeNesting++
                continue
            }
            if (isEndScope(line)) {
                scopeNesting--
                if (scopeNesting < 0) {
                    return null
                }
                continue
            }
            if (isLabel(line)) {
                // Ignore labels set in further nested scope
                if (scopeNesting > 0) {
                    continue
                }

                val labelName = line.substring(LABEL.length)
                if (labelName == labelToSearchFor) {
                    return offset
                } else {
                    continue
                }
            }

            val parseResult = findInstruction(line)
            offset += parseResult.instruction.size
        }
        return null
    }

    fun getResultingCode(): List<UByte> {

        for ((index, line) in lines.indices.zip(lines)) {
            currentLine = index
            assembleLine(line)

        }

        popScope()

        if (scopes.isNotEmpty()) {
            throw AssembleError("Forgot to close a scope??")
        }

        return currentCode
    }

}