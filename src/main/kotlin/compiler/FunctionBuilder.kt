package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.emulator.DefaultEmulator


interface FunctionProvider {
    fun includeFunction(name: String, parameters: List<DataType>): FunctionSignature
}

fun buildFunctionBody(
    nodes: List<AstNode>,
    functionSignature: FunctionSignature,
    functionProvider: FunctionProvider
): CodeGenerator {

    val generator = CodeGenerator()
    val builder = FunctionBuilder(functionSignature, functionProvider, generator)

    builder.buildBody(nodes)

    return generator
}

private class FunctionBuilder(
    val functionInfo: FunctionSignature,
    val functionProvider: FunctionProvider,
    val generator: CodeGenerator,
) {


    private fun buildNodes(nodes: Iterable<AstNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun handleStatement(node: AstNode) {
        buildStatement(node, functionInfo, generator, functionProvider)


    }

    fun handleReturn(node: AstNode) {
        if (node.asReturn().hasValue()) {
            throw NotImplementedError()
        }
        generator.generate(DefaultEmulator.ret.build())
    }

    private fun handleIf(node: AstNode) {
        val ifData = node.asIf()

        assert(false)
        //TODO
        // optimizeGenerate(actionBuilder.buildStatement(ifData.condition))
        val jumpToFalseCondition = generator.makeSpaceFor(DefaultEmulator.jump_zero)
        buildNodes(ifData.ifBody)

        val jumpToEnd = if (ifData.hasElse) generator.makeSpaceFor(DefaultEmulator.jump) else null

        //TODO size
        jumpToFalseCondition.generate(mapOf("addr" to generator.currentSize))

        if (ifData.hasElse) {
            buildNodes(ifData.elseBody)
            jumpToEnd ?: throw AssertionError()
            //TODO size
            jumpToEnd.generate(mapOf("addr" to generator.currentSize))
        }
    }


    private fun handleWhile(node: AstNode) {
        val startOfBlock = generator.currentSize

        assert(false)
        // TODO optimizeGenerate(actionBuilder.buildStatement(node.asWhile().condition))
        val jumpToExit = generator.makeSpaceFor(DefaultEmulator.jump_zero)

        buildNodes(node.asWhile().body)
        //TODO size
        generator.generate(DefaultEmulator.jump.build(mapOf("addr" to startOfBlock)))
        jumpToExit.generate(mapOf("addr" to generator.currentSize))
    }


    fun buildStatement(node: AstNode) {


        when (node.type) {
            // TODO: what should we do here???
            NodeTypes.MemberDeclaration -> return
            NodeTypes.Return -> handleReturn(node)
            NodeTypes.If -> handleIf(node)
            NodeTypes.While -> handleWhile(node)
            else -> handleStatement(node)

        }
    }

    fun buildBody(nodes: Iterable<AstNode>) {
        buildNodes(nodes)

        handleReturn(AstNode.fromReturn())
    }
}