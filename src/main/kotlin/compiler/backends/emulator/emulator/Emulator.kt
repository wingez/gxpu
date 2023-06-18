package compiler.backends.emulator.emulator

import compiler.backends.emulator.instructions.InstructionSet
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference


open class EmulatorRuntimeError(message: String) : Exception(message)
class EmulatorCyclesExceeded(message: String) : EmulatorRuntimeError(message)
class EmulatorInstructionError(message: String) : EmulatorRuntimeError(message)
class EmulatorInvalidInstructionError(message: String) : EmulatorRuntimeError(message)


interface ReferenceIndexProvider {
    fun getIndexOfReference(reference: Reference): Int
}

private data class Frame(
    val pc: Int,
    val fp: Int,
)

open class Emulator(
    val instructionSet: InstructionSet,
    val memorySize: Int = MEMORY_SIZE,
) : ReferenceIndexProvider {
    companion object {
        const val MEMORY_SIZE = 256
    }

    val outputStream = mutableListOf<Int>()
    val memory = Array(memorySize) { 0 }

    private val frameStack = mutableListOf<Frame>()

    lateinit var instructions: List<EmulatorInstruction>

    var a: Int = 0
    var pc: Int = 0
    var fp: Int = 0
    var sp: Int = 0
    var flag: Boolean = false
    var shouldHalt: Boolean = false
    fun reset() {
        a = 0
        pc = 0
        fp = 0
        sp = 0
        flag = false
        shouldHalt = false
    }

    fun clearMemory() {
        for (i in 0 until memorySize) {
            memory[i] = 0
        }
    }

    fun setProgram(program: List<EmulatorInstruction>) {
        this.instructions = program
    }

    fun setAllMemory(content: Collection<Int>) {
        if (content.size > memorySize)
            throw EmulatorRuntimeError("Size of program greater than memory size")

        clearMemory()
        content.forEachIndexed { index, byte -> memory[index] = byte }
    }

    fun setMemoryAt(position: Int, value: Int) {
        if (position !in 0 until memorySize)
            throw EmulatorRuntimeError("Trying to access memory at $position, which is outside memory range")
        memory[position] = value
    }

    fun getMemoryAt(position: Int): Int {
        if (position !in 0 until memorySize)
            throw EmulatorRuntimeError("Trying to access memory at $position, which is outside memory range")
        return memory[position]
    }

    private fun getIncPC(): Int {
        return getMemoryAt((pc++))
    }

    fun print(value: Int) {
        outputStream.add(value)
    }

    fun push(value: Int) {
        setMemoryAt(sp, value)
        sp++
    }

    fun pop(): Int {
        sp--
        return getMemoryAt((sp))
    }

    fun halt() {
        shouldHalt = true
    }

    fun stepSingleInstruction() {
        /**
        Runs a single instruction, return True if the instruction indicated the program should terminate, False otherwise
        :return:
         */
        val ins = instructions[pc]
        pc++
        ins.emulate(this, this)
    }

    override fun getIndexOfReference(reference: Reference): Int {
        for ((index, instr) in instructions.withIndex()) {
            if (reference in instr.references) {
                return index
            }
        }
        throw AssertionError()
    }

    fun run(maxClockCycles: Int = 1000) {
        shouldHalt = false
        for (clockCounter in 0 until maxClockCycles) {
            stepSingleInstruction()
            if (shouldHalt) {
                return
            }
        }
        throw EmulatorCyclesExceeded("Maximum execution cycles exceeded, stuck in infinite loop perhaps?")
    }

    fun pushFrame() {
        frameStack.add(Frame(pc = pc, fp = fp))
    }

    fun restoreFrame() {
        frameStack.removeLast().also {
            this.pc = it.pc
            this.fp = it.fp
        }
    }
}