package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.emulator.DefaultEmulator
import se.wingez.instructions.Instruction


interface CodeGenerator {
    fun generate(code: List<UByte>)
    fun generateAt(code: List<UByte>, at: Int)
    fun makeSpaceFor(instruction: Instruction): Int
    val currentSize: Int
}


class AssemblyFunction(
    val generator: CodeGenerator,
    val frameLayout: FrameLayout
) {

    fun buildNode(container: NodeContainer) {
        buildNodes(container.getNodes())
    }

    fun buildNodes(nodes: Iterable<StatementNode>) {
        for (node in nodes) {
            buildStatement(node)
        }
    }

    fun buildStatement(node: StatementNode) {
        if (node is PrintNode) {
            putValueInRegister(node.target)
            generator.generate(DefaultEmulator.print.build())
        } else if (node is AssignNode) {
            handleAssignment(node)
        } else if (node is IfNode) {

            putValueInRegister(node.condition)
            generator.generate(DefaultEmulator.testa.build())
            val toPutJumpToFalseCondition = generator.makeSpaceFor(DefaultEmulator.jump_zero)
            buildNodes(node.body)

            val toPutJumpToEnd = if (node.hasElse) generator.makeSpaceFor(DefaultEmulator.jump) else 0

            //TODO size
            generator.generateAt(
                DefaultEmulator.jump_zero.build(mapOf("addr" to generator.currentSize.toUByte())),
                toPutJumpToFalseCondition
            )

            if (node.hasElse) {
                buildNodes(node.elseBody)
                //TODO size
                generator.generateAt(
                    DefaultEmulator.jump.build(mapOf("addr" to generator.currentSize.toUByte())),
                    toPutJumpToEnd
                )
            }
        } else if (node is WhileNode) {
            val startOfBlock = generator.currentSize
            putValueInRegister(node.condition)
            generator.generate(DefaultEmulator.testa.build())
            val toPutJumpToExit = generator.makeSpaceFor(DefaultEmulator.jump_zero)

            buildNodes(node.body)
            //TODO size
            generator.generate(DefaultEmulator.jump.build(mapOf("addr" to startOfBlock.toUByte())))
            generator.generateAt(
                DefaultEmulator.jump_zero.build(mapOf("addr" to generator.currentSize.toUByte())),
                toPutJumpToExit
            )
        } else if (node is CallNode) {
            val function = callFunc(node)

            //TODO pop return value if exists
            if (function.hasReturnSize) {
//                compiler.putCode(DefaultEmulator.)
            }

        }


    }

    private fun callFunc(node: CallNode): FrameLayout {
        TODO("Not yet implemented")

    }

    private fun handleAssignment(node: AssignNode) {


    }

    private fun putValueInRegister(node: ValueProviderNode) {


    }

}

class Compiler : TypeProvider, CodeGenerator {

    val resultingCode = mutableListOf<UByte>()
    val functions = mutableMapOf<String, AssemblyFunction>()
    val types = mutableMapOf<String, DataType>()

    override val currentSize
        get() = resultingCode.size

    override fun generate(code: List<UByte>) {
        resultingCode.addAll(code)
    }

    override fun generateAt(code: List<UByte>, at: Int) {
        resultingCode.addAll(at, code)
    }

    override fun makeSpaceFor(instruction: Instruction): Int {
        val pos = currentSize
        for (i in 0 until instruction.size) {
            generate(listOf(0u))
        }
        return pos
    }

    override fun getType(name: String): DataType {
        if (name.isEmpty()) {
            return DEFAULT_TYPE
        }
        if (name !in types) {
            throw CompileError("No type with name $name found")
        }
        return types.getValue(name)
    }

}