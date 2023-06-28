package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*

data class BuiltFunction(
    val signature: FunctionSignature,
    val layout: FunctionFrameLayout,
    val instructions: List<EmulatorInstruction>
) {

    fun getDependents(): Set<FunctionSignature> {
        return instructions.flatMap { it.values.values }.filter { it.isReference }.map { it.reference!!.function }
            .toSet()
    }

}

fun buildFunctionBody(
    intermediateFunction: FunctionContent,
): BuiltFunction {
    return FunctionBuilder(intermediateFunction).buildBody()
}

class FunctionBuilder(
    private val intermediateFunction: FunctionContent,
) : CodeGenerator, FunctionContext {
    val signature = intermediateFunction.definition.signature

    val resultingCode = mutableListOf<EmulatorInstruction>()

    private lateinit var layout: FunctionFrameLayout
    override val fieldLayout: LayedOutDatatype
        get() = layout

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingCode.add(emulatorInstruction)
    }


    private fun handleExecute(instr: Execute) {
        val expr = instr.expression

        if (expr.type != Datatype.Void) {
            requireGetValueIn(expr, WhereToPutResult.TopStack, this)
            addInstruction(emulate(DefaultEmulator.sub_sp, "val" to sizeOf(expr.type)))
        } else {
            requireGetValueIn(expr, WhereToPutResult.A, this)
        }
    }

    fun handleReturn() {
        addInstruction(emulate(DefaultEmulator.ret))
    }


    private fun handleAssign(instr: Assign) {

        val targetAddress = getAddressOf(instr.target, this)

        when (targetAddress) {
            is FpField -> {
                val field = targetAddress.field
                assert(field.type == Datatype.Integer || field.type.isPointer)

                requireGetValueIn(instr.value, WhereToPutResult.TopStack, this)

                addInstruction(emulate(DefaultEmulator.pop_fp_offset, "offset" to field.offset))
            }

            is DynamicAddress -> {
                requireGetValueIn(instr.value, WhereToPutResult.TopStack, this)

                targetAddress.instructions.forEach { addInstruction(it) }
                addInstruction(emulate(DefaultEmulator.pop_at_a_offset, "offset" to 0))
            }

            else -> TODO(targetAddress.toString())
        }
    }

    private fun buildInstruction(instr: Instruction) {


        when (instr) {
            is Execute -> handleExecute(instr)
            is Assign -> handleAssign(instr)
            is JumpOnFalse -> jumpHelper(instr.condition, false, instr.label)
            is JumpOnTrue -> jumpHelper(instr.condition, true, instr.label)
            is Jump -> addInstruction(emulate(DefaultEmulator.jump, "addr" to Reference(signature, instr.label)))
            is Return -> handleReturn()
            else -> TODO(instr.toString())
        }


    }

    private fun jumpHelper(expr: ValueExpression, jumpOn: Boolean, label: Label) {
        assert(expr.type == Datatype.Boolean)

        requireGetValueIn(expr, WhereToPutResult.Flag, this)
        if (!jumpOn) {
            addInstruction(emulate(DefaultEmulator.jump_not_flag, "addr" to Reference(signature, label)))
        } else {
            TODO()
        }
    }

    private fun buildCodeBody(code: IntermediateCode) {


        val referencesToAdd = mutableMapOf<Int, MutableList<Reference>>()

        for ((label, index) in code.labels) {
            val reference = Reference(signature, label)
            if (index !in referencesToAdd) {
                referencesToAdd[index] = mutableListOf()
            }
            referencesToAdd.getValue(index).add(reference)
        }




        for ((index, instruction) in code.instructions.withIndex()) {

            val indexOfAddedInstructions = resultingCode.size

            if (index == 0) {
                // Make space for local variables if necessary
                val sizeOfLocalVariables = layout.sizeOfType(FieldAnnotation.LocalVariable)
                if (sizeOfLocalVariables > 0) {
                    addInstruction(emulate(DefaultEmulator.add_sp, "val" to sizeOfLocalVariables))
                }
            }


            buildInstruction(instruction)

            if (index in referencesToAdd) {
                val references = referencesToAdd.getValue(index)
                references.forEach {
                    resultingCode[indexOfAddedInstructions].addReference(it)
                }
            }
        }


    }

    fun buildBody(): BuiltFunction {

        layout = calculateLayout(intermediateFunction.fields)

        buildCodeBody(intermediateFunction.code)

        return BuiltFunction(signature, layout, resultingCode)
    }


}


private fun assertFrameMatchesDefinition(layout: FunctionFrameLayout, definition: FunctionSignature) {


}

fun calculateLayout(
    localVariables: Datatype,
): FunctionFrameLayout {

    val variablesInOrder = mutableListOf<CompositeDataTypeField>()

    // Add in this order
    for (variableType in listOf(FieldAnnotation.Result, FieldAnnotation.Parameter, FieldAnnotation.LocalVariable)) {
        for (variable in localVariables.compositeFields.filter { it.annotation == variableType }) {
            variablesInOrder.add(variable)
        }
    }

    //first local variable should be at index 0.
    //subtract the size of Result & parameters
    val offset =
        variablesInOrder.filter { it.annotation == FieldAnnotation.Result || it.annotation == FieldAnnotation.Parameter }
            .sumOf { sizeOf(it.type) }


    val fieldType = Datatype.Composite("functionLayout", variablesInOrder)

    return FunctionFrameLayout(
        fieldType,
        offset,
    )
}

class FunctionFrameLayout(
    private val fieldType: Datatype,
    private val offset: Int,
) : LayedOutDatatype {

    private val base = LayedOutStruct(fieldType)

    override val size: Int
        get() = base.size

    override fun getField(fieldName: String): StructDataField {
        val baseField = base.getField(fieldName)
        return baseField.copy(offset = baseField.offset - offset)
    }

    fun sizeOfType(variableType: FieldAnnotation): Int {
        return fieldType.compositeFields.filter { it.annotation == variableType }.sumOf { sizeOf(it.type) }
    }

    fun getDescription(): List<String> {

        return fieldType.compositeFields.map {
            "${getField(it.name).offset}: ${it.name}: ${it.type}"
        }
    }


}