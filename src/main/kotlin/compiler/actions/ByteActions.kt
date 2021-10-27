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


data class PushByte(
    val value: UByte
) : Action {
    override val cost: Int = 1
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.lda_constant.build(mapOf("val" to value)))
        generator.generate(DefaultEmulator.pusha.build())
    }
}

data class PushRegister(
    override val cost: Int = 1
) : Action {
    override fun compile(generator: CodeGenerator) {
        generator.generate(DefaultEmulator.pusha.build())
    }
}