package compiler.backends.emulator.emulator

import compiler.backends.emulator.instructions.InstructionSet
import compiler.backends.emulator.EmulatorInstruction

class DefaultEmulator : Emulator(instructionSet) {

    companion object {
        const val MISC = "misc"
        const val LOAD_STORE = "load/store"
        const val STACK = "stack"
        const val FLOW_CONTROL = "flow control"
        const val ARITHMETIC = "arithmetic"

        val instructionSet = InstructionSet()


        fun build(mnemonic: String): List<EmulatorInstruction> {
            return instructionSet.assembleMnemonic(mnemonic)
        }


        val invalid = instructionSet.createInstruction("invalid", group = MISC) { _, _ ->
            throw EmulatorInvalidInstructionError("invalid")
        }

        val exit = instructionSet.createInstruction("exit", group = MISC) { em, _ ->
            em.halt()
        }
        val print = instructionSet.createInstruction("out", group = MISC) { em, _ ->
            em.print(em.a)
        }
        val lda_constant = instructionSet.createInstruction("LDA #val", group = LOAD_STORE) { em, params ->
            em.a = params.getValue("val")
        }

        val lda_at = instructionSet.createInstruction("LDA [#addr]", group = LOAD_STORE) { em, params ->
            em.a = em.getMemoryAt(params.getValue("addr"))
        }

        val lda_at_fp_offset = instructionSet.createInstruction("LDA [FP #offset]", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.a = em.getMemoryAt(em.fp + offset.toByte().toInt())
        }

        val lda_at_a_offset = instructionSet.createInstruction("LDA [A #offset]", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.a = em.getMemoryAt(em.a + offset)
        }
        val lda_fp_offset = instructionSet.createInstruction("LDA FP #offset", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.a = (em.fp + offset)
        }

        val lda_sp_offset = instructionSet.createInstruction("LDA SP #offset", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.a = (em.sp + offset)
        }

        val lda_at_sp_offset = instructionSet.createInstruction("LDA [SP #offset]", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.a = em.getMemoryAt((em.sp + offset))
        }

        val sta = instructionSet.createInstruction("STA [#addr]", group = LOAD_STORE) { em, params ->
            em.setMemoryAt(params.getValue("addr"), em.a)
        }

        val sta_fp_offset = instructionSet.createInstruction("STA [FP #offset]", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.setMemoryAt(em.fp + offset, em.a)
        }
        val sta_sp_offset = instructionSet.createInstruction("STA [SP #offset]", group = LOAD_STORE) { em, params ->
            val offset = params.getValue("offset")
            em.setMemoryAt(em.sp + offset, em.a)
        }
        val sta_at_sp_offset =
            instructionSet.createInstruction("STA [[SP #offset]]", group = LOAD_STORE) { em, params ->
                val offset = params.getValue("offset")
                val addr = em.getMemoryAt((em.sp + offset))
                em.setMemoryAt(addr, em.a)
            }


        val ldsp = instructionSet.createInstruction("LDSP #val", group = STACK) { em, params ->
            em.sp = params.getValue("val")
        }
        val ldfp = instructionSet.createInstruction("LDFP #val", group = STACK) { em, params ->
            em.fp = params.getValue("val")
        }
        val ldfp_sp = instructionSet.createInstruction("LDFP SP", group = STACK) { em, _ ->
            em.fp = em.sp
        }

        val push = instructionSet.createInstruction("PUSH #val", group = STACK) { em, params ->
            val value = params.getValue("val")
            em.push(value)
        }

        val pusha = instructionSet.createInstruction("PUSHA", group = STACK) { em, _ ->
            em.push(em.a)
        }
        val pushsp = instructionSet.createInstruction("PUSHSP", group = STACK) { em, _ ->
            em.push(em.sp)
        }

        val push_fp_offset = instructionSet.createInstruction("PUSH [FP #offset]", group = STACK) { em, params ->
            val offset = params.getValue("offset")
            em.push(em.getMemoryAt(em.fp + offset))
        }

        val popa = instructionSet.createInstruction("POPA", group = STACK) { em, _ ->
            em.a = em.pop()
        }

        val pop_adda = instructionSet.createInstruction("POP ADDA", group = STACK) { em, _ ->
            em.a = em.a + em.pop()
        }

        val pop_suba = instructionSet.createInstruction("POP SUBA", group = STACK) { em, _ ->
            em.a = em.a - em.pop()
        }

        val pop_fp_offset = instructionSet.createInstruction("POP [FP #offset]", group = STACK) { em, params ->
            val offset = params.getValue("offset")
            val value = em.pop()
            em.setMemoryAt(em.fp + offset, value)
        }

        val pop_at_a_offset = instructionSet.createInstruction("POP [A #offset]", group = STACK) { em, params ->
            val offset = params.getValue("offset")
            val value = em.pop()
            em.setMemoryAt(em.a + offset, value)
        }

        val sub_sp = instructionSet.createInstruction("SUBSP #val", group = STACK) { em, params ->
            val value = params.getValue("val")
            em.sp = em.sp - value
        }

        val sub_sp_a = instructionSet.createInstruction("SUBSP A", group = STACK) { em, _ ->
            em.sp = em.sp - em.a
        }

        val add_sp = instructionSet.createInstruction("ADDSP #val", group = STACK) { em, params ->
            val value = params.getValue("val")
            em.sp = em.sp + value
        }
        val addsp_at_sp_offset = instructionSet.createInstruction("ADDSP [SP #offset]", group = STACK) { em, params ->
            val offset = params.getValue("offset")
            val toAdd = em.getMemoryAt(em.sp + offset)
            em.sp = em.sp + toAdd
        }

        val call_addr = instructionSet.createInstruction("CALL #addr", group = FLOW_CONTROL) { em, params ->
            val addr = params.getValue("addr")
            em.pushFrame()
            em.fp = em.sp
            em.pc = addr
        }

        val ret = instructionSet.createInstruction("RET", group = FLOW_CONTROL) { em, params ->
            em.sp = em.fp
            em.restoreFrame()
        }

        val jump = instructionSet.createInstruction("JMP #addr", group = FLOW_CONTROL) { em, params ->
            val addr = params.getValue("addr")
            em.pc = addr
        }

        val jump_flag = instructionSet.createInstruction("JMPF #addr", group = FLOW_CONTROL) { em, params ->
            val addr = params.getValue("addr")
            if (em.flag) {
                em.pc = addr
            }
        }

        val jump_not_flag = instructionSet.createInstruction("JMPNF #addr", group = FLOW_CONTROL) { em, params ->
            val addr = params.getValue("addr")
            if (!em.flag) {
                em.pc = addr
            }
        }

        val branch = instructionSet.createInstruction("bra #offset", group = FLOW_CONTROL) { em, params ->
            val offset = params.getValue("offset")
            em.pc = em.pc + offset
        }

        val branch_zero = instructionSet.createInstruction("braz #offset", group = FLOW_CONTROL) { em, params ->
            val offset = params.getValue("offset")
            if (em.flag) {
                em.pc = em.pc + offset
            }
        }

        val test_z_a = instructionSet.createInstruction("TSTZ A", group = FLOW_CONTROL) { em, params ->
            em.flag = em.a == 0
        }
        val test_nz_a = instructionSet.createInstruction("TSTNZ A", group = FLOW_CONTROL) { em, params ->
            em.flag = em.a != 0
        }

        val test_neg_a = instructionSet.createInstruction("TSTN A", group = FLOW_CONTROL) { em, params ->
            em.flag = em.a < 0
        }
        val test_nonneg_a = instructionSet.createInstruction("TSTNN A", group = FLOW_CONTROL) { em, params ->
            em.flag = em.a >= 0
        }

        val test_z_pop = instructionSet.createInstruction("TSTZ POP", group = FLOW_CONTROL) { em, params ->
            val value = em.pop()
            em.flag = value == 0
        }

        val log_inv_a = instructionSet.createInstruction("LINV A", group = ARITHMETIC) { em, _ ->
            // Logical inverse
            if (em.a == 0) {
                em.a = 1
            } else {
                em.a = 0
            }
        }

        val adda = instructionSet.createInstruction("ADDA #val", group = ARITHMETIC) { em, params ->
            val value = params.getValue("val")
            em.a = em.a + value
        }

        val suba = instructionSet.createInstruction("SUBA #val", group = ARITHMETIC) { em, params ->
            val value = params.getValue("val")
            em.a = em.a - value
        }

        val adda_sp = instructionSet.createInstruction("ADDA [SP #offset]", group = ARITHMETIC) { em, params ->
            val offset = params.getValue("offset")
            em.a = em.a + em.getMemoryAt(em.sp + offset)
        }
        val suba_sp = instructionSet.createInstruction("SUBA SP #offset", group = ARITHMETIC) { em, params ->
            val offset = params.getValue("offset")
            em.a = em.a - em.getMemoryAt(em.sp + offset)
        }

        val memcpy_stack_to_frame =
            instructionSet.createInstruction("CPY [SP #spoffset] [FP #fpoffset]", group = LOAD_STORE) { em, params ->
                val fromSPoffset = params.getValue("spoffset")
                val toFPoffset = params.getValue("fpoffset")

                val value = em.getMemoryAt(em.sp + fromSPoffset)
                em.setMemoryAt(em.fp + toFPoffset, value)
            }

    }
}