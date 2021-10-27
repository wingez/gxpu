package se.wingez.compiler

import se.wingez.ast.AstNode
import se.wingez.ast.FunctionNode
import se.wingez.ast.StructNode
import se.wingez.byte
import se.wingez.compiler.actions.ActionBuilder
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


class Compiler : TypeProvider, FunctionProvider {
    val generator = CodeGenerator()
    val functions = mutableMapOf<String, FunctionInfo>()

    val types = mutableMapOf<String, DataType>(
        "void" to voidType,
        "byte" to byteType,
    )


    override fun getType(name: String): DataType {
        if (name.isEmpty()) {
            return DEFAULT_TYPE
        }
        if (name !in types) {
            throw CompileError("No type with name $name found")
        }
        return types.getValue(name)
    }

    override fun getFunction(name: String): FunctionInfo {
        if (name !in functions) {
            throw CompileError("No function with name $name found")
        }
        return functions.getValue(name)
    }


    fun buildFunction(node: FunctionNode): FunctionInfo {

        assertValidFunctionNode(node)

        val functionInfo = calculateFrameLayout(node, this, byte(generator.currentSize))

        val builder = FunctionBuilder(generator, functionInfo, ActionBuilder(functionInfo, this))

        if (functionInfo.name in functions) {
            throw CompileError("Function ${functionInfo.name} already exists")
        }
        functions[functionInfo.name] = functionInfo

        builder.buildBody(node.getNodes())

        return functionInfo

    }

    fun buildStruct(node: StructNode) {
        val struct = buildStruct(node, this)
        if (struct.name in types) {
            throw CompileError("Function ${struct.name} already exists")
        }
        types[struct.name] = struct
    }

    fun buildProgram(nodes: List<AstNode>): List<UByte> {
        generator.generate(DefaultEmulator.ldfp.build(mapOf("val" to byte(STACK_START))))
        generator.generate(DefaultEmulator.ldsp.build(mapOf("val" to byte(STACK_START))))

        val callMain = generator.makeSpaceFor(DefaultEmulator.call_addr)
        generator.generate(DefaultEmulator.exit.build())

        for (node in nodes) {
            when (node) {
                is FunctionNode -> buildFunction(node)
                is StructNode -> buildStruct(node)
                else -> throw CompileError("Dont know what to do with $node")

            }
        }

        if (MAIN_NAME !in functions) {
            throw  CompileError("No main-function provided")
        }

        val mainFunction = functions.getValue(MAIN_NAME)

        callMain.generate(mapOf("addr" to mainFunction.memoryPosition))

        return generator.resultingCode
    }

}