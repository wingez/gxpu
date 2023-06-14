package se.wingez.compiler.backends.emulator

import compiler.backends.emulator.emulator.Emulator
import compiler.backends.emulator.emulator.ReferenceIndexProvider
import compiler.backends.emulator.instructions.Instruction
import se.wingez.byte
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.Label


data class Reference(
    val function: FunctionDefinition,
    val label: Label
)

data class Value(
    val constant: Int = 0,
    val reference: Reference? = null,
) {
    init {
        assertValid()
    }

    val isReference = reference != null
    val isConstant = !isReference

    private fun assertValid() {
        if (isReference && constant != 0) {
            throw AssertionError()
        }
    }

    override fun toString(): String {
        return if (isConstant) {constant.toString()} else {"${reference!!.function.name}.${reference.label.identifier}"}
    }
}


data class EmulatorInstruction(
    val instruction: Instruction,
    val values: Map<String, Value>,
) {

    private val referenceList = mutableListOf<Reference>()

    val references: List<Reference>
        get() = referenceList

    fun addReference(reference: Reference):EmulatorInstruction {
        referenceList.add(reference)
        return this
    }

    fun emulate(emulator: Emulator, indexProvider: ReferenceIndexProvider) {

        val parameters = values.entries.associate { (name, value) ->
            name to byte(
                if (value.isConstant) {
                    value.constant
                } else {
                    indexProvider.getIndexOfReference(value.reference!!)
                }
            )
        }

        instruction.emulate.invoke(emulator, parameters)
    }

    override fun toString(): String {
        return "${instruction.mnemonic}, $values"
    }

}

fun emulate(instruction: Instruction): EmulatorInstruction {
    return emulate(instruction, emptyList(), emptyList())
}

fun emulate(instruction: Instruction, value: Pair<String, Int>): EmulatorInstruction {
    return emulate(instruction, listOf(value), emptyList())
}

@JvmName("jvmisstupid")
fun emulate(instruction: Instruction, reference: Pair<String, Reference>): EmulatorInstruction {
    return emulate(instruction, emptyList(), listOf(reference))
}

fun emulate(
    instruction: Instruction,
    values: List<Pair<String, Int>>,
    references: List<Pair<String, Reference>>
): EmulatorInstruction {

    return EmulatorInstruction(instruction,
        (values.map { (name, const) ->
            name to Value(constant = const)
        } + references.map { (name, reference) ->
            name to Value(reference = reference)
        }).toMap()
    )
}



