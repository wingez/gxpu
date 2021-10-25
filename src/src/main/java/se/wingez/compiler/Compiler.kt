package se.wingez.compiler

import se.wingez.ast.FunctionNode
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

class CodeGenerator {
    private val codeList = mutableListOf<UByte>()


    fun generate(code: List<UByte>) {
        codeList.addAll(code)
    }

    fun generateAt(code: List<UByte>, at: Int) {
        code.forEachIndexed { index, byte ->
            codeList[at + index] = byte
        }
    }

    fun makeSpaceFor(instruction: Instruction): GenerateLater {
        val pos = currentSize
        for (i in 0 until instruction.size) {
            generate(listOf(0u))
        }
        return GenerateLater(instruction, pos, this)
    }

    val currentSize: Int
        get() = codeList.size

    val resultingCode: List<UByte>
        get() = codeList.toList()
}


class Compiler : TypeProvider {
    val generator = CodeGenerator()
    val functions = mutableMapOf<String, AssemblyFunction>()
    val types = mutableMapOf<String, DataType>()


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

        val function = AssemblyFunction(generator, layout, byte(generator.currentSize))

        if (function.name in functions) {
            throw CompileError("Function ${function.name} already exists")
        }
        functions[function.name] = function

        function.buildBody(node.getNodes())

        return function

    }

    fun buildProgram(nodes: List<FunctionNode>): List<UByte> {
        generator.generate(DefaultEmulator.ldfp.build(mapOf("val" to byte(STACK_START))))
        generator.generate(DefaultEmulator.ldsp.build(mapOf("val" to byte(STACK_START))))

        val callMain = generator.makeSpaceFor(DefaultEmulator.call_addr)
        generator.generate(DefaultEmulator.exit.build())

        for (node in nodes) {
            buildFunction(node)
        }

        if (MAIN_NAME !in functions) {
            throw  CompileError("No main-function provided")
        }

        val mainFunction = functions.getValue(MAIN_NAME)

        callMain.generate(mapOf("addr" to mainFunction.memoryPosition))

        return generator.resultingCode
    }

}