package se.wingez.compiler

import se.wingez.ast.*
import se.wingez.byte
import se.wingez.emulator.DefaultEmulator
import se.wingez.instructions.Instruction

data class GenerateLater(
    val instruction: Instruction,
    val pos: Int,
    private val generator: CodeGenerator
) {
    fun generate(args: Map<String, UByte> = emptyMap()) {
        generator.generateAt(instruction.build(args), pos)
    }
}

interface CodeGenerator {
    fun generate(code: List<UByte>)
    fun generateAt(code: List<UByte>, at: Int)
    fun makeSpaceFor(instruction: Instruction): GenerateLater
    val currentSize: Int
}


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

    fun buildStatement(node: StatementNode) {
        if (node is PrintNode) {
            putValueInRegister(node.target)
            generator.generate(DefaultEmulator.print.build())
        } else if (node is AssignNode) {
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

        if (frameLayout.sizeOfVars > 0u) {
            throw CompileError("Not supported yet")
        } else {
            generator.generate(DefaultEmulator.ret.build())
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
        code.forEachIndexed { index, byte ->
            resultingCode[at + index] = byte
        }
    }

    override fun makeSpaceFor(instruction: Instruction): GenerateLater {
        val pos = currentSize
        for (i in 0 until instruction.size) {
            generate(listOf(0u))
        }
        return GenerateLater(instruction, pos, this)
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

    fun buildFunction(node: FunctionNode): AssemblyFunction {

        assertValidFunctionNode(node)

        val layout = calculateFrameLayout(node, this)

        val function = AssemblyFunction(this, layout, byte(currentSize))

        if (function.name in functions) {
            throw CompileError("Function ${function.name} already exists")
        }
        functions[function.name] = function

        function.buildBody(node.getNodes())

        return function

    }

    fun buildProgram(nodes: List<FunctionNode>): List<UByte> {
        generate(DefaultEmulator.ldfp.build(mapOf("val" to byte(STACK_START))))
        generate(DefaultEmulator.ldsp.build(mapOf("val" to byte(STACK_START))))

        val callMain = makeSpaceFor(DefaultEmulator.call_addr)
        generate(DefaultEmulator.exit.build())

        for (node in nodes) {
            buildFunction(node)
        }

        if (MAIN_NAME !in functions) {
            throw  CompileError("No main-function provided")
        }

        val mainFunction = functions.getValue(MAIN_NAME)

        callMain.generate(mapOf("addr" to mainFunction.memoryPosition))

        return resultingCode
    }

}