package se.wingez.compiler

import se.wingez.ast.*
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
    fun buildNodes(nodes: Iterable<StatementNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun handleStatement(node: StatementNode) {
        optimizeGenerate(actionBuilder.buildStatement(node))
    }

    fun handleReturn(node: ReturnNode) {
        if (node.value != null) {
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

    private fun handleIf(node: IfNode) {

        optimizeGenerate(actionBuilder.buildStatement(node.condition))
        val jumpToFalseCondition = generator.makeSpaceFor(DefaultEmulator.jump_zero)
        buildNodes(node.body)

        val jumpToEnd = if (node.hasElse) generator.makeSpaceFor(DefaultEmulator.jump) else null

        //TODO size
        jumpToFalseCondition.generate(mapOf("addr" to generator.currentSize.toUByte()))

        if (node.hasElse) {
            buildNodes(node.elseBody)
            jumpToEnd ?: throw AssertionError()
            //TODO size
            jumpToEnd.generate(mapOf("addr" to generator.currentSize.toUByte()))
        }
    }


    private fun handleWhile(node: WhileNode) {
        val startOfBlock = byte(generator.currentSize)
        optimizeGenerate(actionBuilder.buildStatement(node.condition))
        val jumpToExit = generator.makeSpaceFor(DefaultEmulator.jump_zero)

        buildNodes(node.body)
        //TODO size
        generator.generate(DefaultEmulator.jump.build(mapOf("addr" to startOfBlock)))
        jumpToExit.generate(mapOf("addr" to byte(generator.currentSize)))
    }


    fun buildStatement(node: StatementNode) {

        when (node) {
            is PrintNode -> handleStatement(node)
            is ReturnNode -> handleReturn(node)
            is AssignNode -> handleStatement(node)
            is IfNode -> handleIf(node)
            is WhileNode -> handleWhile(node)
            is CallNode -> handleStatement(node)
            is PrimitiveMemberDeclaration -> {
            }
            else -> throw CompileError("Dont know how to parse $node")
        }
    }

    fun buildBody(nodes: Iterable<StatementNode>) {
        buildNodes(nodes)

        handleReturn(ReturnNode())
    }
}