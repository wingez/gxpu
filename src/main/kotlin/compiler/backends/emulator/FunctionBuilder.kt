package se.wingez.compiler

import compiler.frontend.StructBuilder
import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.emulator.DefaultEmulator
import se.wingez.instructions.Instruction


interface FunctionProvider {
    fun findSignature(name: String, parameterSignature: List<DataType>): FunctionSignature
}

data class BuiltFunction(
    val signature: FunctionSignature,
    val generator: CodeGenerator,
    val layout: StructType,
    val sizeOfVars: Int,
)

fun buildFunctionBody(
    nodes: List<AstNode>,
    signature: FunctionSignature,
    functionProvider: FunctionProvider,
    typeProvider: TypeProvider,
): BuiltFunction {

    val builder = FunctionBuilder(signature, functionProvider, typeProvider)

    return builder.buildBody(nodes)
}

private data class VariableLink(
    val generateLater: GenerateLater,
    val variableName: String,
    val offset: Int,
)

class FunctionBuilder(
    val signature: FunctionSignature,
    val functionProvider: FunctionProvider,
    val typeProvider: TypeProvider,
) {

    val generator = CodeGenerator()

    private val variableLinks = mutableListOf<VariableLink>()

    private val localVariables = mutableMapOf<String, DataType>()


    fun linkVariable(instruction: Instruction, variableName: String, offset: Int) {
        val generateLater = generator.makeSpaceFor(instruction)
        variableLinks.add(VariableLink(generateLater, variableName, offset))
    }

    fun addLocalVariable(name: String, type: DataType) {
        if (name == "result" ||
            name in signature.parameters.map { it.name } ||
            name in localVariables
        ) {
            throw CompileError("Already added a variable with name $name")
        }

        localVariables[name] = type
    }

    fun getLocalVariableType(name: String): DataType {
        if (name == "result") {
            return signature.returnType
        }
        val parameter = signature.parameters.find { it.name == name }
        if (parameter != null) {
            return parameter.type
        }

        return localVariables.getValue(name)
    }


    private fun buildNodes(nodes: Iterable<AstNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun handleStatement(node: AstNode) {

        if (node.type == NodeTypes.Assign) {
            buildAssignment(node, this)
        } else {
            buildNoResultStatement(node, this)
        }
    }

    fun handleReturn(node: AstNode) {
        if (node.asReturn().hasValue()) {
            throw NotImplementedError()
        }
        generator.generate(DefaultEmulator.ret.build())
    }

    private fun handleIf(node: AstNode) {
        val ifData = node.asIf()

        putOnStack(ifData.condition, this)

        generator.generate(DefaultEmulator.test_pop.build())
        val jumpToFalseCondition = generator.makeSpaceFor(DefaultEmulator.jump_zero)
        buildNodes(ifData.ifBody)

        val jumpToEnd = if (ifData.hasElse) generator.makeSpaceFor(DefaultEmulator.jump) else null

        //TODO size
        generator.link(jumpToFalseCondition, signature, LinkType.FunctionAddress, generator.currentSize)

        if (ifData.hasElse) {
            buildNodes(ifData.elseBody)
            jumpToEnd ?: throw AssertionError()
            //TODO size
            generator.link(jumpToEnd, signature, LinkType.FunctionAddress, generator.currentSize)
        }
    }


    private fun handleWhile(node: AstNode) {
        val startOfBlock = generator.currentSize

        putOnStack(node.asWhile().condition, this)
        generator.generate(DefaultEmulator.test_pop.build())

        val jumpToExit = generator.makeSpaceFor(DefaultEmulator.jump_zero)
        buildNodes(node.asWhile().body)
        //TODO size
        generator.link(DefaultEmulator.jump, signature, LinkType.FunctionAddress, startOfBlock)
        generator.link(jumpToExit, signature, LinkType.FunctionAddress, generator.currentSize)
    }

    fun handleMemberDeclaration(node: AstNode) {
        assert(node.type == NodeTypes.NewVariable)
        val memberData = node.asNewVariable()
        val memberType = typeProvider.getType(memberData.optionalTypeDefinition!!)


        addLocalVariable(memberData.name, memberType)
    }

    fun buildStatement(node: AstNode) {


        when (node.type) {
            // TODO: what should we do here???
            NodeTypes.NewVariable -> handleMemberDeclaration(node)
            NodeTypes.Return -> handleReturn(node)
            NodeTypes.If -> handleIf(node)
            NodeTypes.While -> handleWhile(node)
            else -> handleStatement(node)

        }
    }

    fun buildBody(nodes: Iterable<AstNode>): BuiltFunction {
        buildNodes(nodes)

        handleReturn(AstNode.fromReturn())


        // Finalize

        val layoutBuilder = StructBuilder()
            .addMember("frame", stackFrameType)


        for (parameter in signature.parameters) {
            layoutBuilder.addMember(parameter.name, parameter.type)
        }

        for (localVar in localVariables) {
            layoutBuilder.addMember(localVar.key, localVar.value)
        }

        layoutBuilder.addMember("result", signature.returnType)

        val struct = layoutBuilder.getStruct(signature.name)

        for (link in variableLinks) {
            link.generateLater.generate(mapOf("offset" to struct.getField(link.variableName).offset + link.offset))
        }


        return BuiltFunction(signature, generator, struct, localVariables.values.sumOf { it.size })
    }
}