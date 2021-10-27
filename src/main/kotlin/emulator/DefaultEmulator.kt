package se.wingez.emulator

import se.wingez.byte
import se.wingez.instructions.InstructionSet

class DefaultEmulator : Emulator(instructionSet) {

    companion object {
        const val MISC = "misc"
        const val LOAD_STORE = "load/store"
        const val STACK = "stack"
        const val FLOW_CONTROL = "flow control"
        const val ARITHMETIC = "arithmetic"

        private val instructionSet = InstructionSet()


        fun build(mnemonic: String): List<UByte> {
            return instructionSet.assembleMnemonic(mnemonic)
        }


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
        val lda_constant = instructionSet.createInstruction("LDA #val", group = LOAD_STORE) {
            it.a = it.getIncPC()
            false
        }

        val lda_at_fp_offset = instructionSet.createInstruction("LDA [FP #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = it.getMemoryAt(it.fp + offset)
            false
        }

        val lda_at_a_offset = instructionSet.createInstruction("LDA [A #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = it.getMemoryAt(it.a + offset)
            false
        }
        val lda_fp_offset = instructionSet.createInstruction("LDA FP #offset", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = (it.fp + offset).toUByte()
            false
        }
        val lda_at_sp_offset = instructionSet.createInstruction("LDA [[SP #offset]]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            val addr = it.getMemoryAt((it.sp + offset).toInt())
            it.a = it.getMemoryAt(addr).toUByte()
            false
        }

        val sta_fp_offset = instructionSet.createInstruction("STA [FP #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.setMemoryAt(it.fp.toInt() + offset.toInt(), it.a)
            false
        }
        val sta_at_sp_offset = instructionSet.createInstruction("STA [[SP #offset]]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            val addr = it.getMemoryAt((it.sp + offset).toInt())
            it.setMemoryAt(addr.toInt(), it.a)
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
        val sub_sp = instructionSet.createInstruction("SUBSP #val", group = STACK) {
            val value = it.getIncPC()
            it.sp = (it.sp - value).toUByte()
            false
        }
        val add_sp = instructionSet.createInstruction("ADDSP #val", group = STACK) {
            val value = it.getIncPC()
            it.sp = (it.sp + value).toUByte()
            false
        }

        val call_addr = instructionSet.createInstruction("CALL #addr", group = FLOW_CONTROL) {
            val addr = it.getIncPC()
            it.push(it.fp)
            it.push(it.pc)
            it.pc = addr
            false
        }

        private fun retGeneric(emulator: Emulator, size: UByte) {
            emulator.sp = emulator.fp
            emulator.sp = (size + emulator.sp).toUByte()

            emulator.pc = emulator.pop()
            emulator.fp = emulator.pop()
        }

        val ret = instructionSet.createInstruction("RET", group = FLOW_CONTROL) {
            retGeneric(it, 0u)
            false
        }
        val ret_frame = instructionSet.createInstruction("RET #size", group = FLOW_CONTROL) {
            val size = it.getIncPC()
            retGeneric(it, size)
            false
        }

        val jump = instructionSet.createInstruction("JMP #addr", group = FLOW_CONTROL) {
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

        val adda = instructionSet.createInstruction("ADDA #val", group = ARITHMETIC) {
            val value = it.getIncPC()
            it.a = (it.a + value).toUByte()
            false
        }

        val adda_sp = instructionSet.createInstruction("ADDA SP #offset", group = ARITHMETIC) {
            val offset = it.getIncPC()
            it.a = (it.a + it.getMemoryAt(it.sp + offset)).toUByte()
            false
        }
        val suba_sp = instructionSet.createInstruction("SUBA SP #offset", group = ARITHMETIC) {
            val offset = it.getIncPC()
            it.a = (it.a - it.getMemoryAt(it.sp + offset)).toUByte()
            false
        }

    }
}