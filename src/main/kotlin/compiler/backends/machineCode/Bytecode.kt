package compiler.backends.machineCode

import compiler.BackendCompiler
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerState
import compiler.frontend.*
import requireNotReached
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*


enum class Register {
    Param0
}


class RegisterData(private val register: Register) : DataItem {
    override fun generateAssembly(): String {
        val label = when (register) {
            Register.Param0 -> "edi"
        }
        return "%$label"
    }
}

class Constant(val value: Int) : DataItem {
    override fun generateAssembly(): String {
        return "$$value"
    }
}

interface Instruction {
    fun generateAssembly(): List<String>
}


interface DataItem {
    fun generateAssembly(): String
}


class MoveInstruction(private val from: DataItem, val to: DataItem) : Instruction {


    override fun generateAssembly(): List<String> {
        return listOf("movl\t${from.generateAssembly()}, ${to.generateAssembly()}")
    }
}

class CallInstruction(val toCall: FunctionDefinition) : Instruction {
    override fun generateAssembly(): List<String> {
        return listOf("call ${toCall.functionName}")
    }
}

class ReturnInstruction() : Instruction {
    override fun generateAssembly(): List<String> {
        return listOf(
            "popq\t%rbp",
            "ret",
        )
    }
}

fun generateHeader(functionContent: FunctionContent): List<String> {
    val name = functionContent.definition.functionName
    return listOf(
        "\t.text",
        "\t.globl $name",
        "$name:",
        "\tpushq\t%rbp",
        "\tmovq\t%rsp, %rbp"
    )
}


fun generateMachineAssembly(functionContent: FunctionContent): List<String> {
    val result = mutableListOf<String>()

    result.addAll(generateHeader(functionContent))


    for ((instr, labels) in functionContent.instructions) {


        generateAssembly(instr)
            .flatMap { it.generateAssembly() }
            .forEach {
                result.add("\t" + it)
            }
    }

    return result
}

fun putValueInRegister(value: ValueExpr, register: Register): Instruction {

    return when (value) {
        is IntConstant -> MoveInstruction(Constant(value.value), RegisterData(register))
        else -> TODO()
    }


}

fun callAssembly(call: Call): List<Instruction> {

    val result = mutableListOf<Instruction>()

    val registerOrder = listOf(Register.Param0)


    for ((index, parameter) in call.params.withIndex()) {
        result.add(putValueInRegister(parameter, registerOrder[index]))

    }

    result.add(CallInstruction(call.func))

    return result
}


fun generateAssembly(instruction: IRinstruction): List<Instruction> {

    return when (instruction) {
        is ReturnNothing -> listOf(ReturnInstruction())

        is TempValue -> {

            require(instruction.value is Call)

            callAssembly(instruction.value)
        }

        else -> TODO()
    }
}

fun buildToAssembly(intermediateProgram: CompiledIntermediateProgram): List<String> {

    val lines = mutableListOf<String>()


    for (func in intermediateProgram.functions) {
        lines.addAll(generateMachineAssembly(func))
    }

    return lines
}

fun writeToFileAndRun(lines: List<String>): String {


    val tempDir = createTempDirectory()

    val includePath = Path("", "src", "test", "stdlib", "assembly")
    for (include in includePath.listDirectoryEntries()) {
        if (!include.isRegularFile()) {
            continue
        }

        val newPath = tempDir.resolve(include.fileName)


        include.copyTo(newPath)
    }

    val mainFile = tempDir.resolve("main.s")

    mainFile.writeLines(lines)

    println()

    println(runCommand(listOf("gcc") + tempDir.listDirectoryEntries().map { it.toString() }, tempDir))


    val result = runCommand(listOf(tempDir.resolve("a.out").toString()), tempDir)

    return result
}

fun sizeOf(datatype: Datatype): Int {
    return when (datatype) {
        Primitives.Integer -> 4
        else -> TODO()
    }
}

class Emitter() {

    private val result = mutableListOf<Instruction>()


    fun emit(instruction: Instruction) {
        result.add(instruction)
        instruction.generateAssembly()
            .forEach { println(it) }
    }


}


class MachineCodeRunner(
) : BackendCompiler {
    override fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String> {

        val lines = buildToAssembly(intermediateProgram)

        val result = writeToFileAndRun(lines)

        return listOf(result)

    }
}

private fun runCommand(command: List<String>, location: Path): String {
    return runCatching {
        ProcessBuilder(command)
            .directory(location.toFile())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
            .also { it.waitFor(1, TimeUnit.SECONDS) }
            .inputStream.bufferedReader().readText()
    }.onFailure { it.printStackTrace() }.getOrThrow()

}






