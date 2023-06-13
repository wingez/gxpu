package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.backends.emulator.instructions.Instruction
import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import se.wingez.ast.AstNode
import se.wingez.compiler.frontend.*

data class BuiltFunction(
    val signature: FunctionDefinition,
    val generator: CodeGenerator,
    val layout: FunctionFrameLayout,
) {
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

private data class VariableLink(
    val generateLater: GenerateLater,
    val variableName: String,
    val offset: Int,
)

class FunctionBuilder(
    val signature: FunctionDefinition,
    val functionProvider: FunctionDefinitionResolver,
    val typeProvider: TypeProvider,
    val datatypeLayoutProvider: DatatypeLayoutProvider,
) {

    val generator = CodeGenerator()

    private val variableLinks = mutableListOf<VariableLink>()

    private val localVariables = mutableMapOf<String, DataType>()

    private lateinit var layout: FunctionFrameLayout

    fun linkVariable(instruction: Instruction, variableName: String, offset: Int) {
        val generateLater = generator.makeSpaceFor(instruction)
        variableLinks.add(VariableLink(generateLater, variableName, offset))
    }

    fun addLocalVariable(name: String, type: DataType) {
//        if (name == "result" ||
//            name in signature.parameters.map { it.name } ||
//            name in localVariables
//        ) {
//            throw CompileError("Already added a variable with name $name")
//        }
//
//        localVariables[name] = type
//    }

//    fun getLocalVariableType(name: String): DataType {
//        if (name == "result") {
//            return signature.returnType
//        }
//        val parameter = signature.parameters.find { it.name == name }
//        if (parameter != null) {
//            return parameter.type
//        }
//
//        return localVariables.getValue(name)
        throw NotImplementedError()
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
        generator.generate(DefaultEmulator.ret.build())
    }


    fun handleMemberDeclaration(node: AstNode) {
    }

    fun buildStatement(node: AstNode) {


    }

    private fun putOnStack(expr: ValueExpression) {

        when (expr) {
            is ConstantExpression -> {
                generator.generate(DefaultEmulator.push.build(mapOf("val" to expr.value)))
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

        generator.link(DefaultEmulator.call_addr, expr.function, LinkType.FunctionAddress)

        // pop arguments if neccesary
        val argumentSize = expr.parameters.sumOf { this.datatypeLayoutProvider.sizeOf(it.type) }
        if (argumentSize > 0) {
            generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to argumentSize)))
        }

    }


    private fun handleAssign(instr: Assign) {
        putOnStack(instr.value)

        val field = layout.layout.values.find { it.name == instr.member } ?: throw AssertionError()

        assert(field.type == Datatype.Integer)

        val offset = field.offset
        generator.generate(DefaultEmulator.pop_fp_offset.build(mapOf("offset" to offset)))

    }

    private fun buildInstruction(instr: se.wingez.compiler.frontend.Instruction) {


        when (instr) {
            is Execute -> handleCall(instr)
            is Assign -> handleAssign(instr)
            else -> TODO()
        }


    }


    private fun buildCodeBody(code: SemiCompiledCode) {


        for ((index, instruction) in code.instructions.withIndex()) {
            buildInstruction(instruction)

        }


    }

    fun buildBody(node: AstNode): BuiltFunction {

        val functionContent = compileFunction(node, functionProvider, typeProvider)

        layout = calculateLayout(functionContent, datatypeLayoutProvider)

        buildCodeBody(functionContent.code)

        handleReturn(AstNode.fromReturn())

        return BuiltFunction(signature, generator, layout)
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

private fun calculateLayout(
    functionContent: FunctionContent,
    datatypeLayoutProvider: DatatypeLayoutProvider
): FunctionFrameLayout {

    val variablesInOrder = mutableListOf<Pair<Variable, StructDataField>>()
    var totalSizeSoFar = 0

    // Add in this order
    for (variableType in listOf(VariableType.Result, VariableType.Parameter, VariableType.Local)) {
        for (variable in functionContent.localVariables.filter { it.type == variableType }) {

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
