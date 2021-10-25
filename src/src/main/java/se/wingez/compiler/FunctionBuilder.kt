package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.byte
import se.wingez.compiler.actions.ActionBuilder
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
        val action = actionBuilder.buildStatement(node)
        action.compile(generator)
    }

    fun handleReturn(node: ReturnNode) {
        if (node.value != null) {
            throw NotImplementedError()
        }

        if (functionInfo.sizeOfVars > 0u) {
            generator.generate(DefaultEmulator.ret_frame.build(mapOf("size" to functionInfo.sizeOfVars)))
        } else {
            generator.generate(DefaultEmulator.ret.build())
        }
    }

    private fun handleIf(node: IfNode) {

        val compareAction = actionBuilder.getActionInRegister(node.condition, compareType)
            ?: throw CompileError("Could not parse condition")
        compareAction.compile(generator)
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
        val compareAction = actionBuilder.getActionInRegister(node.condition, compareType)
            ?: throw CompileError("Could not parse condition")
        compareAction.compile(generator)
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
            else -> throw CompileError("Dont know how to parse $node")
        }
    }

    fun buildBody(nodes: Iterable<StatementNode>) {
        // Make space on stack for local variables
        if (functionInfo.sizeOfVars > 0u) {
            generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to functionInfo.sizeOfVars)))
        }
        //Move fp to sp = bottom of frame
        generator.generate(DefaultEmulator.ldfp_sp.build())

        buildNodes(nodes)

        handleReturn(ReturnNode())
    }
}