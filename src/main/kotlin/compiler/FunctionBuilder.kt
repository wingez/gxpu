package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.NodeTypes
import se.wingez.byte
import se.wingez.compiler.actions.Action
import se.wingez.compiler.actions.ActionBuilder
import se.wingez.compiler.actions.flatten
import se.wingez.compiler.actions.optimizers.applyAllOptimizations
import se.wingez.emulator.DefaultEmulator

interface FunctionProvider {
    fun getFunction(name: String): FunctionInfo
}

class FunctionBuilder(
    val generator: CodeGenerator,
    val functionInfo: FunctionInfo,
    private val actionBuilder: ActionBuilder,
) {
    fun buildNodes(nodes: Iterable<AstNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun handleStatement(node: AstNode) {
        optimizeGenerate(actionBuilder.buildStatement(node))
    }

    fun handleReturn(node: AstNode) {
        if (node.asReturn().hasValue()) {
            throw NotImplementedError()
        }
        generator.generate(DefaultEmulator.ret.build())
    }

    private fun optimizeGenerate(action: Action) {
        val actionsMutable = flatten(action).toMutableList()
        applyAllOptimizations(actionsMutable)
        for (a in actionsMutable) {
            a.compile(generator)
        }
    }

    private fun handleIf(node: AstNode) {
        val ifData = node.asIf()

        optimizeGenerate(actionBuilder.buildStatement(ifData.condition))
        val jumpToFalseCondition = generator.makeSpaceFor(DefaultEmulator.jump_zero)
        buildNodes(ifData.ifBody)

        val jumpToEnd = if (ifData.hasElse) generator.makeSpaceFor(DefaultEmulator.jump) else null

        //TODO size
        jumpToFalseCondition.generate(mapOf("addr" to generator.currentSize.toUByte()))

        if (ifData.hasElse) {
            buildNodes(ifData.elseBody)
            jumpToEnd ?: throw AssertionError()
            //TODO size
            jumpToEnd.generate(mapOf("addr" to generator.currentSize.toUByte()))
        }
    }


    private fun handleWhile(node: AstNode) {
        val startOfBlock = byte(generator.currentSize)
        optimizeGenerate(actionBuilder.buildStatement(node.asWhile().condition))
        val jumpToExit = generator.makeSpaceFor(DefaultEmulator.jump_zero)

        buildNodes(node.asWhile().body)
        //TODO size
        generator.generate(DefaultEmulator.jump.build(mapOf("addr" to startOfBlock)))
        jumpToExit.generate(mapOf("addr" to byte(generator.currentSize)))
    }


    fun buildStatement(node: AstNode) {


        when (node.type) {
            // TODO: what should we do here???
            NodeTypes.MemberDeclaration -> return
            NodeTypes.Assign, NodeTypes.Print, NodeTypes.Call -> handleStatement(node)
            NodeTypes.Return -> handleReturn(node)
            NodeTypes.If -> handleIf(node)
            NodeTypes.While -> handleWhile(node)
            else -> throw CompileError("Dont know how to parse $node")

        }
    }

    fun buildBody(nodes: Iterable<AstNode>) {
        buildNodes(nodes)

        handleReturn(AstNode.fromReturn())
    }
}