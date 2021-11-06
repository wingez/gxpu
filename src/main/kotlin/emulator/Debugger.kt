package se.wingez.emulator

import se.wingez.ast.AstParser
import se.wingez.compiler.Compiler
import se.wingez.compiler.FunctionInfo
import se.wingez.tokens.parseFile
import java.io.File
import kotlin.math.max
import kotlin.math.min

class InteractiveDebugger(
    val initialCode: List<UByte>,
    val functions: List<FunctionInfo>,
) {

    companion object {
        const val WIDTH = 80
        const val HEIGHT = 40
    }

    val emulator = DefaultEmulator()

    val buffer: Array<CharArray>

    init {
        buffer = IntRange(0, HEIGHT - 1).map { CharArray(WIDTH) { ' ' } }.toTypedArray()
        reset()
    }

    fun reset() {
        emulator.reset()
        emulator.clearMemory()
        emulator.setAllMemory(initialCode)
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

    fun getCurrentFunction(): FunctionInfo? {

        var bestMatch: FunctionInfo? = null

        for (func in functions) {
            if (func.memoryPosition <= emulator.pc) {
                if (bestMatch == null || func.memoryPosition > bestMatch.memoryPosition)
                    bestMatch = func
            }
        }
        return bestMatch
    }

    fun printProgram() {

        val instructions = emulator.instructionSet.disassembleWithIndex(initialCode).entries.sortedBy { it.key }

        val currentIndex = instructions.find { it.key == emulator.pc.toInt() }?.key ?: throw AssertionError()

        val startPos = max(0, currentIndex - 15)
        val endPos = min(instructions.size, startPos + 30)

        (startPos until endPos).forEachIndexed { i, position ->

            val labels = mutableListOf<String>()

            val mempos = instructions[position].key


            for (f in functions) {
                if (mempos == f.memoryPosition.toInt()) {
                    labels.add(f.name)
                }
            }
            if (mempos == emulator.pc.toInt()) {
                labels.add(">")
            }

            val prefixes = labels.joinToString(" ").padStart(15)


            val line = "$prefixes ${instructions[position].value}"

            setText(line, 40, i)
        }
    }

    fun renderStatus() {

        val function = getCurrentFunction()
        val functionName = function?.name ?: "no"

        val registerX = 0
        val registerY = 0
        setText("a : ${emulator.a}", registerX, registerY + 0)
        setText("fp: ${emulator.fp}", registerX, registerY + 1)
        setText("sp: ${emulator.sp}", registerX, registerY + 2)
        setText("pc: ${emulator.pc}", registerX, registerY + 3)

        setText("func: $functionName", registerX, registerY + 5)


        val memoryStartX = 1
        val memoryStartY = 10

        (220..255).reversed().forEachIndexed { rowPos, i ->

            val labels = mutableListOf<String>()

            if (i == emulator.fp.toInt()) labels.add("fp")
            if (i == emulator.sp.toInt()) labels.add("sp")

            if (function != null) {
                for (field in function.fields.values) {
                    if (i == emulator.fp.toInt() + field.offset.toInt()) {
                        labels.add("$functionName.${field.name}")
                    }
                }
            }

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
}

fun main(array: Array<String>) {

    val fileName = array[0]

    val tokens = parseFile(File(fileName).inputStream().reader())
    val nodes = AstParser(tokens).parse()

    val compiler = Compiler()
    compiler.buildProgram(nodes)

    val i = InteractiveDebugger(compiler.generator.resultingCode, compiler.functions.values.toList())

    i.interactiveLoop()
}
