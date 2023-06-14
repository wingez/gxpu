package se.wingez.compiler.backends.emulator.instructions

import compiler.backends.emulator.SignatureBuilder
import compiler.backends.emulator.instructions.Instruction
import compiler.backends.emulator.instructions.InstructionBuilderError
import compiler.backends.emulator.instructions.InstructionSet
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference
import se.wingez.compiler.backends.emulator.Value
import se.wingez.compiler.frontend.Label
import se.wingez.splitMany

class AssembleError(message: String) : Exception(message)

class Assembler(
    private val lines: List<String>,
    private val instructionSet: InstructionSet,
) {
    private val scopes = mutableListOf<MutableMap<String, String>>()
    private var currentLine = 0
    private var referencesToAddToNextInstruction = mutableListOf<Reference>()

    private val NEW_SCOPE = "scope"
    private val END_SCOPE = "endscope"
    private val COMMENT = "//"
    private val VARIABLE = "#"
    private val LABEL = ":"


    init {
        pushScope()
    }


    private val currentInstructions = mutableListOf<EmulatorInstruction>()

    private val currentSize: Int
        get() = currentInstructions.size

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

    private fun getVariableOrConstant(value: String): Value {

        if (!value.all { it.isDigit() }) {
            return Value(reference = Reference(SignatureBuilder("main").getSignature(), Label(value)))
        }

        return Value(constant = value.toInt())
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
            referencesToAddToNextInstruction.add(Reference(SignatureBuilder("main").getSignature(), Label(labelName)))
            return
        }

        if (isVariable(trimmedMnemonic)) {
            val (name, value) = trimmedMnemonic.substring(VARIABLE.length)
                .split('=').map { it.trim(' ') }
            setVariable(name, value)
            return
        }

        val parseResult = findInstruction(trimmedMnemonic)

        val instructionValues = mutableMapOf<String, Value>()

        for ((variableName, variableValue) in parseResult.variableMap.entries) {
            val value = getVariableOrConstant(variableValue)
            instructionValues.put(variableName, value)
        }

        val instr = EmulatorInstruction(parseResult.instruction, instructionValues)
        referencesToAddToNextInstruction.forEach { instr.addReference(it) }
        referencesToAddToNextInstruction.clear()

        currentInstructions.add(instr)
    }

    private fun findInstruction(line: String): InstructionParseResult {
        for (instr in instructionSet.getInstructions()) {

            val variables = mutableMapOf<String, String>()

            val templateSplit =
                splitMany(adjustForBrackets(instr.mnemonic), Instruction.MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }
            val mnemSplit = splitMany(line, Instruction.MNEMONIC_DELIMITERS).filter { it.isNotEmpty() }

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

    fun getResultingInstructions(): List<EmulatorInstruction> {

        for ((index, line) in lines.indices.zip(lines)) {
            currentLine = index
            assembleLine(line)

        }

        popScope()

        if (scopes.isNotEmpty()) {
            throw AssembleError("Forgot to close a scope??")
        }

        return currentInstructions
    }
}


private data class InstructionParseResult(
    val instruction: Instruction,
    val variableMap: Map<String, String>
)
