package se.wingez.compiler

import se.wingez.emulator.DefaultEmulator

interface BuiltIn {

    val signature: FunctionSignature
    val name: String
    fun compile(generator: CodeGenerator)
}


class Print : BuiltIn {
    override val name = "print"
    override val signature: FunctionSignature
        get() = SignatureBuilder(name)
            .addParameter("value", byteType)
            .getSignature()


    override fun compile(generator: CodeGenerator) {
        generator.generate(
            DefaultEmulator.lda_at_fp_offset.build(
                mapOf("offset" to signature.getField("value").offset)
            )
        )
        generator.generate(DefaultEmulator.print.build())
        generator.generate(DefaultEmulator.ret.build())
    }
}

class ByteAddition : BuiltIn {
    override val name = "add"
    override val signature: FunctionSignature
        get() = SignatureBuilder(name)
            .addParameter("left", byteType)
            .addParameter("right", byteType)
            .setReturnType(byteType)
            .getSignature()


    override fun compile(generator: CodeGenerator) {

        generator.generate(
            DefaultEmulator.instructionSet.assembleMnemonicFile(
                """
            ADDSP #${stackFrameType.size}
            POPA
            POP ADDA
            STA [SP #0]
            ret
        """
            )
        )
    }
}

class ByteSubtraction : BuiltIn {
    override val name = "subtract"
    override val signature: FunctionSignature
        get() = SignatureBuilder(name)
            .addParameter("toSubtract", byteType)
            .addParameter("initial", byteType)
            .setReturnType(byteType)
            .getSignature()


    override fun compile(generator: CodeGenerator) {
        generator.generate(
            DefaultEmulator.instructionSet.assembleMnemonicFile(
                """
            ADDSP #${stackFrameType.size}
            POPA
            POP SUBA
            STA [SP #0]
            ret
        """
            )
        )
    }
}


fun findBuiltin(name: String, signature: List<DataType>): BuiltIn? {
    val available = listOf(
        Print(), ByteAddition(), ByteSubtraction(),
    )

    for (builtIn in available) {
        if (builtIn.name == name && builtIn.signature.parameterSignature == signature) {
            return builtIn
        }
    }
    return null
}







