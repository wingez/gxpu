package compiler.backends.emulator.emulator


import ast.AstParser
import compiler.BackendCompiler
import compiler.BuiltInSignatures
import compiler.backends.emulator.*
import compiler.compileAndRunProgram
import compiler.frontend.Datatype
import compiler.frontend.FunctionContent
import compiler.frontend.GlobalsResult
import compiler.frontend.functionEntryLabel
import tokens.parseFile
import java.io.File
import kotlin.math.max
import kotlin.math.min

class InteractiveDebugger(
) : BackendCompiler {

    companion object {
        const val WIDTH = 180
        const val HEIGHT = 25
    }

    lateinit var instructions: List<EmulatorInstruction>

    val emulator = DefaultEmulator()

    val buffer: Array<CharArray> = IntRange(0, HEIGHT - 1).map { CharArray(WIDTH) { ' ' } }.toTypedArray()

    fun reset() {
        emulator.reset()
        emulator.clearMemory()
        emulator.setProgram(instructions)
    }

    fun clearBuffer() {
        for (row in buffer) {
            for (index in row.indices) {
                row[index] = ' '
            }
        }
    }

    fun setText(text: String, x: Int, y: Int) {

        if (y !in buffer.indices)
            return

        val row = buffer[y]
        for (i in text.indices) {
            if (x + i !in row.indices)
                continue
            row[x + i] = text[i]
        }
    }

    fun print() {
        for (row in buffer) {
            val s = StringBuilder(WIDTH)
            row.forEach { s.append(it) }
            println(s)
        }
    }

    fun getFunctionPositions(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        for ((index, instr) in instructions.withIndex()) {
            for (ref in instr.references) {
                if (ref.label == functionEntryLabel) {
                    result.add(ref.function.signature.name to index)
                }
            }
        }

        return result.sortedBy { it.second }
    }

    private fun getCurrentFunction(): String? {
        val current = emulator.pc

        for ((s, i) in getFunctionPositions()) {
            if (current >= i) {
                return s
            }
        }
        return null
    }

    fun printProgram() {

        val currentIndex = emulator.pc

        val startPos = max(0, currentIndex - 10)
        val endPos = min(instructions.size, startPos + 25)

        val functionPositions = getFunctionPositions()

        (startPos until endPos).forEachIndexed { i, position ->

            val labels = mutableListOf<String>()


            for ((f, mempos) in functionPositions) {
                if (mempos == position) {
                    labels.add(f)
                }
            }
            if (position == currentIndex) {
                labels.add(">")
            }

            val prefixes = labels.joinToString(" ").padStart(15)


            val line = "$prefixes ${instructions[position]}"

            setText(line, 40, i)
        }
    }

    fun renderStatus() {

        val function = getCurrentFunction()
        val functionName = function ?: "no"

        val registerX = 0
        val registerY = 0
        setText("a : ${emulator.a}", registerX, registerY + 0)
        setText("fp: ${emulator.fp}", registerX, registerY + 1)
        setText("sp: ${emulator.sp}", registerX, registerY + 2)
        setText("pc: ${emulator.pc}", registerX, registerY + 3)

        setText("func: $functionName", registerX, registerY + 5)


        val memoryStartX = 1
        val memoryStartY = 10

        (0..( 10)).reversed().forEachIndexed { rowPos, i ->

            val labels = mutableListOf<String>()

            if (i == emulator.fp) labels.add("fp")
            if (i == emulator.sp) labels.add("sp")

            val prefixes = labels.joinToString().padEnd(15)

            setText("$prefixes $i: ${emulator.getMemoryAt(i)}", memoryStartX, memoryStartY + rowPos)
        }

        printProgram()
    }


    fun interactiveLoop() {

        while (true) {
            clearBuffer()
            renderStatus()
            print()


            when (readLine()) {
                "q" -> break
                "r" -> reset()
                "" -> emulator.stepSingleInstruction()
                "s" -> emulator.stepSingleInstruction()
                else -> println("unknown command")
            }

        }
    }

    override fun buildAndRun(
        allTypes: List<Datatype>,
        functions: List<FunctionContent>,
        globals: GlobalsResult
    ): List<Int> {
        val emulatorRunner = EmulatorRunner(BuiltInFunctions())
        instructions = emulatorRunner.compileIntermediate(allTypes, functions, globals).instructions

        reset()

        interactiveLoop()

        return emptyList()
    }
}

fun main(array: Array<String>) {

    val fileName = array[0]

    val debugger = InteractiveDebugger()
    compileAndRunProgram(fileName, debugger, BuiltInSignatures())
}
