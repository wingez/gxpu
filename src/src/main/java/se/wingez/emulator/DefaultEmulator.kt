package se.wingez.emulator

import se.wingez.byte
import se.wingez.instructions.InstructionSet

class DefaultEmulator : Emulator(instructionSet) {

    companion object {
        const val MISC = "misc"
        const val LOAD_STORE = "load/store"
        const val STACK = "stack"
        const val FLOW_CONTROL = "flow control"

        private val instructionSet = InstructionSet()

        val invalid = instructionSet.createInstruction("invalid", group = MISC) {
            throw EmulatorInvalidInstructionError("invalid")
        }
        val exit = instructionSet.createInstruction("exit", group = MISC) {
            true
        }
        val print = instructionSet.createInstruction("out", group = MISC) {
            it.print(it.a)
            false
        }
        val lda = instructionSet.createInstruction("LDA #val", group = LOAD_STORE) {
            it.a = it.getIncPC()
            false
        }

        val lda_fp_offset = instructionSet.createInstruction("LDA FP, #offset", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = it.getMemoryAt(it.fp + offset)
            false
        }

        val sta_fp_offset = instructionSet.createInstruction("STA FP, #offset", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.setMemoryAt(it.fp.toInt() + offset.toInt(), it.a)
            false
        }
        val ldsp = instructionSet.createInstruction("LDSP #val", group = STACK) {
            it.sp = it.getIncPC()
            false
        }
        val ldfp = instructionSet.createInstruction("LDFP #val", group = STACK) {
            it.fp = it.getIncPC()
            false
        }
        val ldfp_sp = instructionSet.createInstruction("LDFP SP", group = STACK) {
            it.fp = it.sp
            false
        }
        val pusha = instructionSet.createInstruction("PUSHA", group = STACK) {
            it.push(it.a)
            false
        }
        val popa = instructionSet.createInstruction("POPA", group = STACK) {
            it.a = it.pop()
            false
        }
        val sub_sp = instructionSet.createInstruction("SUBSÅ #val", group = STACK) {
            val value = it.getIncPC()
            it.sp = (it.sp - value).toUByte()
            false
        }

        val call_addr = instructionSet.createInstruction("CALL #addr", group = FLOW_CONTROL) {
            val addr = it.getIncPC()
            it.push(it.fp)
            it.push(it.pc)
            it.pc = addr
            false
        }

        private fun ret_generic(emulator: Emulator, size: UByte) {
            emulator.sp = emulator.fp
            emulator.sp = (size + emulator.sp).toUByte()

            emulator.pc = emulator.pop()
            emulator.fp = emulator.pop()
        }

        val ret = instructionSet.createInstruction("RET", group = FLOW_CONTROL) {
            ret_generic(it, 0u)
            false
        }
        val ret_frame = instructionSet.createInstruction("RET #size", group = FLOW_CONTROL) {
            val size = it.getIncPC()
            ret_generic(it, size)
            false
        }

        val jump = instructionSet.createInstruction("JMP #7", group = FLOW_CONTROL) {
            val addr = it.getMemoryAt(it.pc)
            it.pc = addr
            false
        }

        val jump_zero = instructionSet.createInstruction("JMPZ #addr", group = FLOW_CONTROL) {
            val addr = it.getIncPC()
            if (it.zeroFlag) {
                it.pc = addr
            }
            false
        }

        val testa = instructionSet.createInstruction("TSTA", group = FLOW_CONTROL) {
            it.zeroFlag = it.a == byte(0)
            false
        }

    }
}