package se.wingez.compiler

import se.wingez.emulator.DefaultEmulator

interface BuiltIn {

    val signature: FunctionSignature
    val layout: FrameLayout
    val name: String
    fun compile(generator: CodeGenerator)
}


class Print : BuiltIn {
    override val name = "print"
    override val signature: FunctionSignature
        get() = SignatureBuilder(name)
            .addParameter("value", byteType)
            .addAnnotation(FunctionAnnotation.NoFrame)
            .getSignature()

    override val layout: FrameLayout
        get() = LayoutBuilder.fromSignature(signature)
            .getLayout()

    override fun compile(generator: CodeGenerator) {

        val valueOffset = LayoutBuilder.fromSignature(signature).getLayout()
            .getField("value").offset


        generator.generate(
            DefaultEmulator.lda_at_fp_offset.build(
                mapOf("offset" to valueOffset)
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

    override val layout: FrameLayout
        get() = LayoutBuilder.fromSignature(signature)
            .getLayout()

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

    override val layout: FrameLayout
        get() = LayoutBuilder.fromSignature(signature)
            .getLayout()

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

class BuiltInFunctions : BuiltInProvider {

    private val available = listOf(
        Print(), ByteAddition(), ByteSubtraction(),
    )

    override fun getTypes(): Map<String, DataType> {
        return mapOf(
            "void" to voidType,
            "byte" to byteType,
        )
    }

    override fun getSignatures(): List<FunctionSignature> {
        return available.map { it.signature }

    }

    override fun buildSignature(signature: FunctionSignature): Pair<CodeGenerator, FrameLayout> {

        val result = available.find { it.signature == signature }
        if (result == null) {
            TODO()
        }

        val generator = CodeGenerator()
        result.compile(generator)

        return generator to result.layout
    }
}






