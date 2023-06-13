package compiler.backends.emulator.emulator

import compiler.backends.emulator.instructions.InstructionSet


open class EmulatorRuntimeError(message: String) : Exception(message)
class EmulatorCyclesExceeded(message: String) : EmulatorRuntimeError(message)
class EmulatorInstructionError(message: String) : EmulatorRuntimeError(message)
class EmulatorInvalidInstructionError(message: String) : EmulatorRuntimeError(message)

private data class Frame(
    val pc: UByte,
    val fp: UByte,
)

open class Emulator(
    val instructionSet: InstructionSet,
    val memorySize: Int = MEMORY_SIZE,
) {
    companion object {
        const val MEMORY_SIZE = 256
    }

    val outputStream = mutableListOf<UByte>()
    val memory = Array<UByte>(memorySize) { 0u }

    private val frameStack = mutableListOf<Frame>()


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

    fun setAllMemory(content: Collection<UByte>) {
        if (content.size > memorySize)
            throw EmulatorRuntimeError("Size of program greater than memory size")

        clearMemory()
        content.forEachIndexed { index, byte -> memory[index] = byte }
    }

    fun setMemoryAt(position: Int, value: UByte) {
        if (position !in 0 until memorySize)
            throw EmulatorRuntimeError("Trying to access memory at $position, which is outside memory range")
        memory[position] = value
    }

    fun setMemoryAt(position: UInt, value: UByte) {
        setMemoryAt(position.toInt(), value)
    }

    fun getMemoryAt(position: Int): UByte {
        if (position !in 0 until memorySize)
            throw EmulatorRuntimeError("Trying to access memory at $position, which is outside memory range")
        return memory[position]
    }

    fun getMemoryAt(position: UInt): UByte {
        return getMemoryAt(position.toInt())
    }

    fun getMemoryAt(position: UByte): UByte {
        return getMemoryAt(position.toInt())
    }

    fun getIncPC(): UByte {
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
        val ins = instructionSet.instructionFromID(getIncPC())
        ins.emulate.invoke(this)
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