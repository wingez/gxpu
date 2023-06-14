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
    val pc: UByte,
    val fp: UByte,
)

open class Emulator(
    val instructionSet: InstructionSet,
    val memorySize: Int = MEMORY_SIZE,
) : ReferenceIndexProvider {
    companion object {
        const val MEMORY_SIZE = 256
    }

    val outputStream = mutableListOf<UByte>()
    val memory = Array<UByte>(memorySize) { 0u }

    private val frameStack = mutableListOf<Frame>()

    lateinit var instructions: List<EmulatorInstruction>

    var a: UByte = 0u
    var pc: UByte = 0u
    var fp: UByte = 0u
    var sp: UByte = 0u
    var zeroFlag: Boolean = false
    var shouldHalt: Boolean = false
    fun reset() {
        a = 0u
        pc = 0u
        fp = 0u
        sp = 0u
        zeroFlag = false
        shouldHalt = false
    }

    fun clearMemory() {
        for (i in 0 until memorySize) {
            memory[i] = 0u
        }
    }

    fun setProgram(program: List<EmulatorInstruction>) {
        this.instructions = program
    }

    fun setAllMemory(content: Collection<UByte>) {
        if (content.size > memorySize)
            throw EmulatorRuntimeError("Size of program greater than memory size")

        clearMemory()
        content.forEachIndexed { index, byte -> memory[index] = byte }
    }

    fun setMemoryAt(position: Int, value: UByte) {
        val wrapped = position% MEMORY_SIZE

        if (wrapped !in 0 until memorySize)
            throw EmulatorRuntimeError("Trying to access memory at $wrapped, which is outside memory range")
        memory[wrapped] = value
    }

    fun setMemoryAt(position: UInt, value: UByte) {
        setMemoryAt(position.toInt(), value)
    }

    fun getMemoryAt(position: Int): UByte {
        val wrapped = position% MEMORY_SIZE
        if (wrapped !in 0 until memorySize)
            throw EmulatorRuntimeError("Trying to access memory at $wrapped, which is outside memory range")
        return memory[wrapped]
    }

    fun getMemoryAt(position: UInt): UByte {
        return getMemoryAt(position.toInt())
    }

    fun getMemoryAt(position: UByte): UByte {
        return getMemoryAt(position.toInt())
    }

    private fun getIncPC(): UByte {
        return getMemoryAt((pc++).toInt())
    }

    fun print(value: UByte) {
        outputStream.add(value)
    }

    fun push(value: UByte) {
        setMemoryAt(sp.toInt(), value)
        sp++
    }

    fun pop(): UByte {
        sp--
        return getMemoryAt((sp).toInt())
    }

    fun halt() {
        shouldHalt = true
    }

    fun stepSingleInstruction() {
        /**
        Runs a single instruction, return True if the instruction indicated the program should terminate, False otherwise
        :return:
         */
        val ins = instructions[pc.toInt()]
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