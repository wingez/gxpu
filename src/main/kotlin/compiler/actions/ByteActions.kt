package se.wingez.compiler.actions

import se.wingez.compiler.CodeGenerator
import se.wingez.emulator.DefaultEmulator

data class PopRegister(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.popa.build())
    }
}


data class ConstantRegister(
    val value: UByte
) : Action {
    override val cost: Int = 1
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.lda_constant.build(mapOf("val" to value)))
    }
}

fun PushConstant(value: UByte): Action {
    return CompositeAction(
        ConstantRegister(value),
        PushRegister(),
    )
}


data class PushRegister(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.pusha.build())
    }
}

data class PopThrow(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.build("ADDSP #1"))
    }
}

data class LoadRegisterFP(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.lda_fp_offset.build(mapOf("offset" to 0u)))
    }
}

data class AddRegister(
    val value: UByte,
    override val cost: Int = 1,
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.adda.build(mapOf("val" to value)))
    }
}

data class StoreRegisterAtStackAddress(
    val offset: UByte,
) : Action {
    /**
     * Expects Value to be in A, address on top of stack.
     * Does not clear the Stack afterwards
     */
    override val cost: Int = 1
    override fun compile(generator: CodeGenerator) {
        generator.generate(
            DefaultEmulator.sta_at_sp_offset.build(
                mapOf("offset" to offset)
            )
        )
    }
}

data class DerefByteAction(
    val offset: UByte,
) : Action {
    /**
     * Derefs a pointer in A Register.
     */
    override val cost: Int = 1
    override fun compile(generator: CodeGenerator) {
        generator.generate(
            DefaultEmulator.lda_at_a_offset.build(
                mapOf("offset" to offset)
            )
        )
    }
}

