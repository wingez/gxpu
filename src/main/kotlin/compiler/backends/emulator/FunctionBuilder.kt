package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*
import ast.AstNode

data class BuiltFunction(
    val signature: FunctionDefinition,
    val layout: FunctionFrameLayout,
    val instructions: List<EmulatorInstruction>
) {

    fun getDependents(): Set<FunctionDefinition> {
        return instructions.flatMap { it.values.values }.filter { it.isReference }.map { it.reference!!.function }
            .toSet()
    }

}

fun buildFunctionBody(
    node: AstNode,
    signature: FunctionDefinition,
    functionProvider: FunctionDefinitionResolver,
    typeProvider: TypeProvider,
    datatypeLayoutProvider: DatatypeLayoutProvider,
): BuiltFunction {

    val builder = FunctionBuilder(signature, functionProvider, typeProvider, datatypeLayoutProvider)

    return builder.buildBody(node)
}

class FunctionBuilder(
    private val signature: FunctionDefinition,
    private val functionProvider: FunctionDefinitionResolver,
    private val typeProvider: TypeProvider,
    override val datatypeLayoutProvider: DatatypeLayoutProvider,
) : CodeGenerator, FunctionContext {

    val resultingCode = mutableListOf<EmulatorInstruction>()

    private lateinit var layout: FunctionFrameLayout

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingCode.add(emulatorInstruction)
    }

    override fun getField(name: String): StructDataField {
        return layout.layout.values.find { it.name == name } ?: throw AssertionError()
    }

    private fun handleExecute(instr: Execute) {
        val expr = instr.expression

        if (expr !is CallExpression) throw NotImplementedError()


        if (expr.type != Datatype.Void) {
            handleCall(expr, WhereToPutResult.TopStack, this)
            addInstruction(emulate(DefaultEmulator.sub_sp, "val" to datatypeLayoutProvider.sizeOf(expr.type)))
        } else {
            handleCall(expr, WhereToPutResult.A, this)
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

                getValue(instr.value, WhereToPutResult.TopStack, this)

                addInstruction(emulate(DefaultEmulator.pop_fp_offset, "offset" to field.offset))
            }

            is DynamicAddress -> {
                getValue(instr.value, WhereToPutResult.TopStack, this)

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

        getValue(expr, WhereToPutResult.Flag, this)
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

    fun buildBody(node: AstNode): BuiltFunction {

        val functionContent = compileFunction(node, functionProvider, typeProvider)

        layout = calculateLayout(functionContent.fields, datatypeLayoutProvider)

        buildCodeBody(functionContent.code)

        return BuiltFunction(signature, layout, resultingCode)
    }


}


data class FunctionFrameLayout(
    val type: Datatype,
    val layout: Map<CompositeDataTypeField, StructDataField>,
    val size: Int,
) {
    fun sizeOfType(variableType: FieldAnnotation): Int {
        return layout.keys.filter { it.annotation == variableType }.sumOf { layout.getValue(it).size }
    }

    fun getDescription(): List<String> {

        return layout.values.sortedBy { it.offset }.map {
            "${it.offset}: ${it.name}: ${it.type}"
        }
    }
}

private fun assertFrameMatchesDefinition(layout: FunctionFrameLayout, definition: FunctionDefinition) {


}

fun calculateLayout(
    localVariables: Datatype,
    datatypeLayoutProvider: DatatypeLayoutProvider
): FunctionFrameLayout {

    val variablesInOrder = mutableListOf<Pair<CompositeDataTypeField, StructDataField>>()
    var totalSizeSoFar = 0

    // Add in this order
    for (variableType in listOf(FieldAnnotation.Result, FieldAnnotation.Parameter, FieldAnnotation.LocalVariable)) {
        for (variable in localVariables.compositeFields.filter { it.annotation == variableType }) {

            val size = datatypeLayoutProvider.sizeOf(variable.type)

            variablesInOrder.add(variable to StructDataField(variable.name, variable.type, totalSizeSoFar, size))
            totalSizeSoFar += size
        }
    }

    //first local variable should be at index 0.
    //subtract the size of Result & parameters
    val offset =
        variablesInOrder.filter { it.first.annotation == FieldAnnotation.Result || it.first.annotation == FieldAnnotation.Parameter }
            .sumOf { datatypeLayoutProvider.sizeOf(it.first.type) }


    return variablesInOrder.map { (variable, field) ->
        variable to field.copy(offset = field.offset - offset)
    }.let { FunctionFrameLayout(localVariables, it.toMap(), totalSizeSoFar) }
}
