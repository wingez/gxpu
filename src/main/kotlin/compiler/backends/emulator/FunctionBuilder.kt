package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*

data class BuiltFunction(
    val definition: FunctionDefinition,
    val layout: FunctionFrameLayout,
    val instructions: List<EmulatorInstruction>
) {

    fun getDependents(): Set<FunctionDefinition> {
        return instructions.flatMap { it.values.values }.filter { it.isReference }.map { it.reference!!.function }
            .toSet()
    }

}

fun buildFunctionBody(
    intermediateFunction: FunctionContent,
    globals: LayedOutDatatype,
): BuiltFunction {
    return FunctionBuilder(intermediateFunction, globals).buildBody()
}

class FunctionBuilder(
    private val intermediateFunction: FunctionContent,
    override val globalsLayout: LayedOutDatatype,
) : CodeGenerator, FunctionContext {
    val definition = intermediateFunction.definition

    val resultingCode = mutableListOf<EmulatorInstruction>()

    private lateinit var layout: FunctionFrameLayout
    override val localsLayout: LayedOutDatatype
        get() = layout

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingCode.add(emulatorInstruction)
    }


    private fun handleExecute(instr: Execute) {
        val expr = instr.expression

        if (expr.type != Primitives.Nothing) {
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
                assert(field.type is PrimitiveDataType || field.type is PointerDatatype)

                requireGetValueIn(instr.value, WhereToPutResult.TopStack, this)

                addInstruction(emulate(DefaultEmulator.pop_fp_offset, "offset" to field.offset))
            }
            is GlobalsField -> {
                val field = targetAddress.field
                assert(field.type is PrimitiveDataType)
                requireGetValueIn(instr.value, WhereToPutResult.A, this)
                addInstruction(emulate(DefaultEmulator.sta, "addr" to field.offset))
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
            is Jump -> addInstruction(emulate(DefaultEmulator.jump, "addr" to Reference(definition, instr.label)))
            is Return -> handleReturn()
            else -> TODO(instr.toString())
        }


    }

    private fun jumpHelper(expr: ValueExpression, jumpOn: Boolean, label: Label) {
        assert(expr.type == Primitives.Boolean)

        requireGetValueIn(expr, WhereToPutResult.Flag, this)
        if (!jumpOn) {
            addInstruction(emulate(DefaultEmulator.jump_not_flag, "addr" to Reference(definition, label)))
        } else {
            TODO()
        }
    }

    private fun buildCodeBody(code: IntermediateCode) {


        val referencesToAdd = mutableMapOf<Int, MutableList<Reference>>()

        for ((label, index) in code.labels) {
            val reference = Reference(definition, label)
            if (index !in referencesToAdd) {
                referencesToAdd[index] = mutableListOf()
            }
            referencesToAdd.getValue(index).add(reference)
        }




        for ((index, instruction) in code.instructions.withIndex()) {

            val indexOfAddedInstructions = resultingCode.size

            if (index == 0) {
                // Make space for local variables if necessary
                val sizeOfLocalVariables = layout.sizeOfLocalVariables
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

        layout = calculateLayout(intermediateFunction.definition, intermediateFunction.fields)

        buildCodeBody(intermediateFunction.code)

        return BuiltFunction(definition, layout, resultingCode)
    }
}

fun calculateLayout(
    definition: FunctionDefinition,
    localVariables: CompositeDatatype,
): FunctionFrameLayout {

    val variablesInOrder = mutableListOf<CompositeDataTypeField>()


    // Add in this order
    if (definition.hasReturnType) {
        variablesInOrder.add(localVariables.getField(RETURN_VALUE_NAME))
    }
    for (paramName in definition.parameterNames) {
        variablesInOrder.add(localVariables.getField(paramName))
    }

    //first local variable should be at index 0.
    //subtract the size of Result & parameters
    val offset = variablesInOrder.sumOf { sizeOf(it.type) }

    for (variables in localVariables.compositeFields.filter { it !in variablesInOrder }) {
        variablesInOrder.add(variables)
    }

    val fieldType = CompositeDatatype("functionLayout", variablesInOrder)

    return FunctionFrameLayout(
        fieldType,
        offset,
    )
}

class FunctionFrameLayout(
    private val fieldType: CompositeDatatype,
    private val offset: Int,
) : LayedOutDatatype {

    private val base = LayedOutStruct(fieldType)

    override val size: Int
        get() = base.size

    override fun getField(fieldName: String): StructDataField {
        val baseField = base.getField(fieldName)
        return baseField.copy(offset = baseField.offset - offset)
    }

    fun getDescription(): List<String> {

        return fieldType.compositeFields.map {
            "${getField(it.name).offset}: ${it.name}: ${it.type}"
        }
    }

    val sizeOfLocalVariables get() = sizeOf(fieldType) - offset

}