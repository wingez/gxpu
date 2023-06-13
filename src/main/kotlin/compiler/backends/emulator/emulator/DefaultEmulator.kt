package compiler.backends.emulator.emulator

import compiler.backends.emulator.instructions.InstructionSet
import se.wingez.byte

class DefaultEmulator : Emulator(instructionSet) {

    companion object {
        const val MISC = "misc"
        const val LOAD_STORE = "load/store"
        const val STACK = "stack"
        const val FLOW_CONTROL = "flow control"
        const val ARITHMETIC = "arithmetic"

        val instructionSet = InstructionSet()


        fun build(mnemonic: String): List<UByte> {
            return instructionSet.assembleMnemonic(mnemonic)
        }


        val invalid = instructionSet.createInstruction("invalid", group = MISC) {
            throw EmulatorInvalidInstructionError("invalid")
        }
        val exit = instructionSet.createInstruction("exit", group = MISC) {
            it.halt()
        }
        val print = instructionSet.createInstruction("out", group = MISC) {
            it.print(it.a)
        }
        val lda_constant = instructionSet.createInstruction("LDA #val", group = LOAD_STORE) {
            it.a = it.getIncPC()
        }

        val lda_at_fp_offset = instructionSet.createInstruction("LDA [FP #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = it.getMemoryAt(it.fp.toInt() + offset.toByte().toInt())
        }

        val lda_at_a_offset = instructionSet.createInstruction("LDA [A #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = it.getMemoryAt(it.a + offset)
        }
        val lda_fp_offset = instructionSet.createInstruction("LDA FP #offset", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = (it.fp + offset).toUByte()
        }
        val lda_at_sp_offset_deref = instructionSet.createInstruction("LDA [[SP #offset]]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            val addr = it.getMemoryAt((it.sp + offset).toInt())
            it.a = it.getMemoryAt(addr)
        }
        val lda_at_sp_offset = instructionSet.createInstruction("LDA [SP #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.a = it.getMemoryAt((it.sp + offset).toInt())
        }

        val sta_fp_offset = instructionSet.createInstruction("STA [FP #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.setMemoryAt(it.fp.toInt() + offset.toInt(), it.a)
        }
        val sta_sp_offset = instructionSet.createInstruction("STA [SP #offset]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            it.setMemoryAt(it.sp.toInt() + offset.toInt(), it.a)
        }
        val sta_at_sp_offset = instructionSet.createInstruction("STA [[SP #offset]]", group = LOAD_STORE) {
            val offset = it.getIncPC()
            val addr = it.getMemoryAt((it.sp + offset).toInt())
            it.setMemoryAt(addr.toInt(), it.a)
        }


        val ldsp = instructionSet.createInstruction("LDSP #val", group = STACK) {
            it.sp = it.getIncPC()
        }
        val ldfp = instructionSet.createInstruction("LDFP #val", group = STACK) {
            it.fp = it.getIncPC()
        }
        val ldfp_sp = instructionSet.createInstruction("LDFP SP", group = STACK) {
            it.fp = it.sp
        }

        val push = instructionSet.createInstruction("PUSH #val", group = STACK) {
            val value = it.getIncPC()
            it.push(value)
        }

        val pusha = instructionSet.createInstruction("PUSHA", group = STACK) {
            it.push(it.a)
        }
        val pushsp = instructionSet.createInstruction("PUSHSP", group = STACK) {
            it.push(it.sp)
        }

        val push_fp_offset = instructionSet.createInstruction("PUSH [FP #offset]", group = STACK) {
            val offset = it.getIncPC()
            it.push(it.getMemoryAt(it.fp + offset))
        }

        val popa = instructionSet.createInstruction("POPA", group = STACK) {
            it.a = it.pop()
        }

        val pop_adda = instructionSet.createInstruction("POP ADDA", group = STACK) {
            it.a = byte(it.a + it.pop())
        }

        val pop_suba = instructionSet.createInstruction("POP SUBA", group = STACK) {
            it.a = byte(it.a - it.pop())
        }

        val pop_fp_offset = instructionSet.createInstruction("POP [FP #offset]", group = STACK) {
            val offset = it.getIncPC()
            val value = it.pop()
            it.setMemoryAt(it.fp + offset, value)
        }

        val sub_sp = instructionSet.createInstruction("SUBSP #val", group = STACK) {
            val value = it.getIncPC()
            it.sp = (it.sp - value).toUByte()
        }

        val sub_sp_a = instructionSet.createInstruction("SUBSP A", group = STACK) {
            it.sp = (it.sp - it.a).toUByte()
        }

        val add_sp = instructionSet.createInstruction("ADDSP #val", group = STACK) {
            val value = it.getIncPC()
            it.sp = (it.sp + value).toUByte()
        }

        val call_addr = instructionSet.createInstruction("CALL #addr", group = FLOW_CONTROL) {
            val addr = it.getIncPC()
            it.pushFrame()
            it.fp = it.sp
            it.pc = addr
        }

        val ret = instructionSet.createInstruction("RET", group = FLOW_CONTROL) {
            it.sp = it.fp
            it.restoreFrame()
        }

        val jump = instructionSet.createInstruction("JMP #addr", group = FLOW_CONTROL) {
            val addr = it.getMemoryAt(it.pc)
            it.pc = addr
        }

        val jump_zero = instructionSet.createInstruction("JMPZ #addr", group = FLOW_CONTROL) {
            val addr = it.getIncPC()
            if (it.zeroFlag) {
                it.pc = addr
            }
        }

        val branch = instructionSet.createInstruction("bra #offset", group = FLOW_CONTROL) {
            val offset = it.getMemoryAt(it.pc)
            it.pc = byte(it.pc + offset)
        }

        val branch_zero = instructionSet.createInstruction("braz #offset", group = FLOW_CONTROL) {
            val offset = it.getIncPC()
            if (it.zeroFlag) {
                it.pc = byte(it.pc + offset)
            }
        }


        val testa = instructionSet.createInstruction("TSTA", group = FLOW_CONTROL) {
            it.zeroFlag = it.a == byte(0)
        }

        val test_pop = instructionSet.createInstruction("TST POP", group = FLOW_CONTROL) {
            val value = it.pop()
            it.zeroFlag = value == byte(0)
        }

        val adda = instructionSet.createInstruction("ADDA #val", group = ARITHMETIC) {
            val value = it.getIncPC()
            it.a = (it.a + value).toUByte()
        }

        val adda_sp = instructionSet.createInstruction("ADDA [SP #offset]", group = ARITHMETIC) {
            val offset = it.getIncPC()
            it.a = (it.a + it.getMemoryAt(it.sp + offset)).toUByte()
        }
        val suba_sp = instructionSet.createInstruction("SUBA SP #offset", group = ARITHMETIC) {
            val offset = it.getIncPC()
            it.a = (it.a - it.getMemoryAt(it.sp + offset)).toUByte()
        }

        val memcpy_stack_to_frame =
            instructionSet.createInstruction("CPY [SP #spoffset] [FP #fpoffset]", group = LOAD_STORE) {
                val fromSPoffset = it.getIncPC()
                val toFPoffset = it.getIncPC()

                val value = it.getMemoryAt(it.sp + fromSPoffset)
                it.setMemoryAt(it.fp + toFPoffset, value)
            }

    }
}