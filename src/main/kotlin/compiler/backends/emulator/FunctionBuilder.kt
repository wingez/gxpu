package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import se.wingez.ast.AstNode
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference
import se.wingez.compiler.backends.emulator.emulate
import se.wingez.compiler.frontend.*

data class BuiltFunction(
    val signature: FunctionDefinition,
    val layout: FunctionFrameLayout,
    val instructions: List<EmulatorInstruction>
) {

    fun getDependents(): Set<FunctionDefinition> {
        return instructions.mapNotNull { it.reference?.function }.toSet()
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
    val signature: FunctionDefinition,
    val functionProvider: FunctionDefinitionResolver,
    val typeProvider: TypeProvider,
    val datatypeLayoutProvider: DatatypeLayoutProvider,
) : CodeGenerator {

    val resultingCode = mutableListOf<EmulatorInstruction>()


    private val localVariables = mutableMapOf<String, DataType>()

    private lateinit var layout: FunctionFrameLayout

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingCode.add(emulatorInstruction)
    }

    private fun buildNodes(nodes: Iterable<AstNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun handleStatement(node: AstNode) {

    }

    fun handleReturn(node: AstNode) {
        if (node.asReturn().hasValue()) {
            throw NotImplementedError()
        }
        addInstruction(emulate(DefaultEmulator.ret))
    }


    fun handleMemberDeclaration(node: AstNode) {
    }

    fun buildStatement(node: AstNode) {


    }

    private fun putOnStack(expr: ValueExpression) {

        when (expr) {
            is ConstantExpression -> {
                addInstruction(
                    emulate(
                        DefaultEmulator.push, "val" to expr.value
                    )
                )
            }

            is VariableExpression -> {
                assert(expr.type == Datatype.Integer)

                val field = layout.layout.values.find { it.name == expr.variable.name } ?: throw AssertionError()

                addInstruction(
                    emulate(
                        DefaultEmulator.push_fp_offset, "offset" to field.offset
                    )
                )
            }

            else -> throw AssertionError()
        }


    }

    private fun handleCall(instr: Execute) {
        //TODO: handle pop result

        val expr = instr.expression

        if (expr !is CallExpression) throw NotImplementedError()

        for (parameterExpr in expr.parameters) {
            putOnStack(parameterExpr)
        }

        addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(expr.function, functionEntryLabel)))

        // pop arguments if neccesary
        val argumentSize = expr.parameters.sumOf { this.datatypeLayoutProvider.sizeOf(it.type) }
        if (argumentSize > 0) {
            addInstruction(emulate(DefaultEmulator.sub_sp, "val" to argumentSize))
        }

    }


    private fun handleAssign(instr: Assign) {
        putOnStack(instr.value)

        val field = layout.layout.values.find { it.name == instr.member } ?: throw AssertionError()

        assert(field.type == Datatype.Integer)

        val offset = field.offset
        addInstruction(emulate(DefaultEmulator.pop_fp_offset, "offset" to offset))
    }

    private fun buildInstruction(instr: se.wingez.compiler.frontend.Instruction) {


        when (instr) {
            is Execute -> handleCall(instr)
            is Assign -> handleAssign(instr)
            is JumpOnFalse -> jumpHelper(instr.condition, false, instr.label)
            is JumpOnTrue -> jumpHelper(instr.condition, true, instr.label)
            else -> TODO(instr.toString())
        }


    }

    private fun jumpHelper(expr: ValueExpression, jumpOn: Boolean, label: Label) {
        assert(expr.type == Datatype.Boolean)

        putOnStack(expr)
        //generator.generate(DefaultEmulator.test_pop.build())
        if (!jumpOn) {
            TODO()
            //generator.link(DefaultEmulator.jump_zero,)
        }
    }

    private fun buildCodeBody(code: IntermediateCode) {

        val referencesToAdd = mutableMapOf<Int,Reference>()

        for ((label, index) in code.labels) {
            val reference = Reference(signature, label)
            assert(index !in referencesToAdd)
            referencesToAdd[index] = reference
        }


        for ((index, instruction) in code.instructions.withIndex()) {

            val indexOfAddedInstructions = resultingCode.size

            buildInstruction(instruction)

            if (index in referencesToAdd) {
                resultingCode[indexOfAddedInstructions].reference = referencesToAdd.getValue(index)
            }
        }


    }

    fun buildBody(node: AstNode): BuiltFunction {

        val functionContent = compileFunction(node, functionProvider, typeProvider)

        layout = calculateLayout(functionContent.localVariables, datatypeLayoutProvider)

        buildCodeBody(functionContent.code)

        handleReturn(AstNode.fromReturn())

        return BuiltFunction(signature, layout, resultingCode)
    }


}


data class FunctionFrameLayout(
    val layout: Map<Variable, StructDataField>,
    val size: Int,
) {
    fun sizeOfType(variableType: VariableType): Int {
        return layout.keys.filter { it.type == variableType }.sumOf { layout.getValue(it).size }
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
    localVariables: List<Variable>,
    datatypeLayoutProvider: DatatypeLayoutProvider
): FunctionFrameLayout {

    val variablesInOrder = mutableListOf<Pair<Variable, StructDataField>>()
    var totalSizeSoFar = 0

    // Add in this order
    for (variableType in listOf(VariableType.Result, VariableType.Parameter, VariableType.Local)) {
        for (variable in localVariables.filter { it.type == variableType }) {

            val size = datatypeLayoutProvider.sizeOf(variable.datatype)

            variablesInOrder.add(variable to StructDataField(variable.name, variable.datatype, totalSizeSoFar, size))
            totalSizeSoFar += size
        }
    }

    //first local variable should be at index 0.
    //subtract the size of Result & parameters
    val offset =
        variablesInOrder.filter { it.first.type == VariableType.Result || it.first.type == VariableType.Parameter }
            .sumOf { datatypeLayoutProvider.sizeOf(it.first.datatype) }


    return variablesInOrder.map { (variable, field) ->
        variable to field.copy(offset = field.offset - offset)
    }.let { FunctionFrameLayout(it.toMap(), totalSizeSoFar) }
}
