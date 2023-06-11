package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.StructBuilder
import se.wingez.ast.OperatorBuiltIns

interface BuiltIn {

    val signature: FunctionSignature
    val sizeOfVars: Int
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

    override val sizeOfVars = 0

    override fun compile(generator: CodeGenerator) {

        val valueOffset = stackFrameType.size

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
    override val name = OperatorBuiltIns.Addition
    override val signature: FunctionSignature
        get() = SignatureBuilder(name)
            .addParameter("left", byteType)
            .addParameter("right", byteType)
            .setReturnType(byteType)
            .getSignature()

    override val sizeOfVars = 0

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
    override val name = OperatorBuiltIns.Subtraction
    override val signature: FunctionSignature
        get() = SignatureBuilder(name)
            .addParameter("initial", byteType)
            .addParameter("toSubtract", byteType)
            .setReturnType(byteType)
            .getSignature()

    override val sizeOfVars = 0

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

    override fun buildSignature(signature: FunctionSignature): BuiltFunction {

        val result = available.find { it.signature == signature }
        if (result == null) {
            TODO()
        }

        val generator = CodeGenerator()
        result.compile(generator)

        val layoutBuilder = StructBuilder()
            .addMember("frame", stackFrameType)

        if (result.sizeOfVars > 0) {
            layoutBuilder.addMember("locals", PrimitiveDatatype(result.sizeOfVars, "Builtin_${result.name}_locals"))
        }

        for (parameter in signature.parameters) {
            layoutBuilder.addMember(parameter.name, parameter.type)
        }

        layoutBuilder.addMember("result", signature.returnType)

        val struct = layoutBuilder.getStruct(signature.name)



        return BuiltFunction(result.signature, generator, struct, 0)
    }
}






