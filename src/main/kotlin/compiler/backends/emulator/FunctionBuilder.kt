package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.backends.emulator.instructions.Instruction
import compiler.frontend.StructBuilder
import compiler.frontend.TypeProvider
import se.wingez.ast.AstNode
import se.wingez.byte
import se.wingez.compiler.frontend.*

data class BuiltFunction(
    val signature: FunctionDefinition,
    val generator: CodeGenerator,
    val layout: StructType,
    val sizeOfVars: Int,
)

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
            generator.generate(DefaultEmulator.add_sp.build(mapOf("val" to argumentSize)))
        }

    }

    private fun buildInstruction(instr: se.wingez.compiler.frontend.Instruction) {


        when (instr) {
            is Execute -> handleCall(instr)
        }


    }

    private fun buildCodeBody(code: SemiCompiledCode) {


        for ((index, instruction) in code.instructions.withIndex()) {
            buildInstruction(instruction)

        }


    }

    fun buildBody(node: AstNode): BuiltFunction {

        val functionContent = compileFunction(node, functionProvider, typeProvider)

        assert(functionContent.localVariables.isEmpty())
        //FIXME: ADD variables

        buildCodeBody(functionContent.code)

        handleReturn(AstNode.fromReturn())


        // Finalize

        val layoutBuilder = StructBuilder()
            .addMember("frame", stackFrameType)


        // for (parameter in signature.parameters) {
        //   layoutBuilder.addMember(parameter.name, parameter.type)
        //}

        for (localVar in localVariables) {
            layoutBuilder.addMember(localVar.key, localVar.value)
        }

        //layoutBuilder.addMember("result", signature.returnType)

        val struct = layoutBuilder.getStruct(signature.name)

        for (link in variableLinks) {
            link.generateLater.generate(mapOf("offset" to struct.getField(link.variableName).offset + link.offset))
        }


        return BuiltFunction(signature, generator, struct, localVariables.values.sumOf { it.size })
    }

}

