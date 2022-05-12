package se.wingez.emulator

import se.wingez.instructions.InstructionSet

open class EmulatorRuntimeError(message: String) : Exception(message)
class EmulatorCyclesExceeded(message: String) : EmulatorRuntimeError(message)
class EmulatorInstructionError(message: String) : EmulatorRuntimeError(message)
class EmulatorInvalidInstructionError(message: String) : EmulatorRuntimeError(message)

open class Emulator(
    val instructionSet: InstructionSet,
    val memorySize: Int = MEMORY_SIZE,
) {
    companion object {
        const val MEMORY_SIZE = 256
    }

    val outputStream = mutableListOf<UByte>()
    val memory = Array<UByte>(memorySize) { 0u }

    var a: UByte = 0u
    var pc: UByte = 0u
    var fp: UByte = 0u
    var sp: UByte = 0u
    var zeroFlag: Boolean = false

    fun reset() {
        a = 0u
        pc = 0u
        fp = 0u
        sp = 0u
        zeroFlag = false
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
        sp--
        setMemoryAt(sp.toInt(), value)
    }

    fun pop(): UByte {
        return getMemoryAt((sp++).toInt())
    }

    fun stepSingleInstruction(): Boolean {
        /**
        Runs a single instruction, return True if the instruction indicated the program should terminate, False otherwise
        :return:
         */
        val ins = instructionSet.instructionFromID(getIncPC())
        return ins.emulate.invoke(this)
    }

    fun run(maxClockCycles: Int = 1000) {
        for (clockCounter in 0 until maxClockCycles) {
            val shouldTerminate = stepSingleInstruction()
            if (shouldTerminate) {
                return
            }
        }
        throw EmulatorCyclesExceeded("Maximum execution cycles exceeded, stuck in infinite loop perhaps?")
    }
}