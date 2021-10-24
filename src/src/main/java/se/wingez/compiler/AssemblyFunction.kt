package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.emulator.DefaultEmulator

class AssemblyFunction(
    val generator: CodeGenerator,
    val frameLayout: FrameLayout,
    val memoryPosition: UByte,
) {

    val name: String
        get() = frameLayout.name

    fun buildNode(container: NodeContainer) {
        buildNodes(container.getNodes())
    }

    fun buildNodes(nodes: Iterable<StatementNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun handleStatement(node: StatementNode) {
        val action = flatten(node, frameLayout)
        action.compile(generator)
    }

    fun handleReturn(node: ReturnNode) {
        if (node.value != null) {
            throw NotImplementedError()
        }

        if (frameLayout.sizeOfVars > 0u) {
            generator.generate(DefaultEmulator.ret_frame.build(mapOf("size" to frameLayout.sizeOfVars)))
        } else {
            generator.generate(DefaultEmulator.ret.build())
        }
    }


    fun buildStatement(node: StatementNode) {

        when (node) {
            is PrintNode -> handleStatement(node)
            is ReturnNode -> handleReturn(node)
            is AssignNode -> handleStatement(node)
        }

        return

        if (node is AssignNode) {
            handleAssignment(node)
        } else if (node is IfNode) {

            putValueInRegister(node.condition)
            generator.generate(DefaultEmulator.testa.build())
            val jumpToFalseCondition = generator.makeSpaceFor(DefaultEmulator.jump_zero)
            buildNodes(node.body)

            val jumpToEnd = if (node.hasElse) generator.makeSpaceFor(DefaultEmulator.jump) else null

            //TODO size
            jumpToFalseCondition.generate(mapOf("addr" to generator.currentSize.toUByte()))

            if (node.hasElse) {
                buildNodes(node.elseBody)
                //TODO size
                jumpToEnd?.generate(mapOf("addr" to generator.currentSize.toUByte()))
            }
        } else if (node is WhileNode) {
            val startOfBlock = generator.currentSize
            putValueInRegister(node.condition)
            generator.generate(DefaultEmulator.testa.build())
            val jumpToExit = generator.makeSpaceFor(DefaultEmulator.jump_zero)

            buildNodes(node.body)
            //TODO size
            generator.generate(DefaultEmulator.jump.build(mapOf("addr" to startOfBlock.toUByte())))
            jumpToExit.generate(mapOf("addr" to generator.currentSize.toUByte()))
        } else if (node is CallNode) {
            val function = callFunc(node)

            //TODO pop return value if exists
            if (function.hasReturnSize) {
//                compiler.putCode(DefaultEmulator.)
            }

        }
    }

    fun buildBody(nodes: Iterable<StatementNode>) {
        // Make space on stack for local variables
        if (frameLayout.sizeOfVars > 0u) {
            generator.generate(DefaultEmulator.sub_sp.build(mapOf("val" to frameLayout.sizeOfVars)))
        }
        //Move fp to sp = bottom of frame
        generator.generate(DefaultEmulator.ldfp_sp.build())

        buildNodes(nodes)

        handleReturn(ReturnNode())
    }

    private fun callFunc(node: CallNode): FrameLayout {
        TODO("Not yet implemented")

    }

    private fun handleAssignment(node: AssignNode) {


    }

    private fun putValueInRegister(node: ValueProviderNode) {


    }

}