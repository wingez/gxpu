package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import compiler.frontend.StructBuilder
import se.wingez.compiler.frontend.FunctionDefinition

interface BuiltIn {

    val signature: FunctionDefinition
    val sizeOfVars: Int
    val name: String
    fun compile(generator: CodeGenerator)
}


class Print : BuiltIn {
    override val name = "print"
    override val signature: FunctionDefinition
        get() = SignatureBuilder(name)
            .addParameter(Datatype.Integer)
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
//
//class ByteAddition : BuiltIn {
//    override val name = OperatorBuiltIns.Addition
//    override val signature: FunctionSignature
//        get() = SignatureBuilder(name)
//            .addParameter("left", byteType)
//            .addParameter("right", byteType)
//            .setReturnType(byteType)
//            .getSignature()
//
//    override val sizeOfVars = 0
//
//    override fun compile(generator: CodeGenerator) {
//
//        generator.generate(
//            DefaultEmulator.instructionSet.assembleMnemonicFile(
//                """
//            ADDSP #${stackFrameType.size}
//            POPA
//            POP ADDA
//            STA [SP #0]
//            ret
//        """
//            )
//        )
//    }
//}
//
//class ByteSubtraction : BuiltIn {
//    override val name = OperatorBuiltIns.Subtraction
//    override val signature: FunctionSignature
//        get() = SignatureBuilder(name)
//            .addParameter("initial", byteType)
//            .addParameter("toSubtract", byteType)
//            .setReturnType(byteType)
//            .getSignature()
//
//    override val sizeOfVars = 0
//
//    override fun compile(generator: CodeGenerator) {
//        generator.generate(
//            DefaultEmulator.instructionSet.assembleMnemonicFile(
//                """
//            ADDSP #${stackFrameType.size}
//            POPA
//            POP SUBA
//            STA [SP #0]
//            ret
//        """
//            )
//        )
//    }
//}

class BuiltInFunctions : BuiltInProvider {

    private val available = listOf(
        Print(), //ByteAddition(), ByteSubtraction(),
    )

    override fun getTypes(): Map<String, Datatype> {
        return mapOf(
            "void" to Datatype.Void,
            "byte" to Datatype.Integer,
        )
    }

    override fun getSignatures(): List<FunctionDefinition> {
        return available.map { it.signature }

    }

    override fun buildSignature(signature: FunctionDefinition): BuiltFunction {

        val result = available.find { it.signature == signature }
        if (result == null) {
            TODO()
        }

        val generator = CodeGenerator()
        result.compile(generator)

        val layoutBuilder = StructBuilder()

        //if (result.sizeOfVars > 0) {
         //   layoutBuilder.addMember("locals", PrimitiveDatatype(result.sizeOfVars, "Builtin_${result.name}_locals"))
        //}
        //TODO what does this do?
//
//        for (parameter in signature.parameters) {
//            layoutBuilder.addMember(parameter.name, parameter.type)
//        }
//
//        layoutBuilder.addMember("result", signature.returnType)

        val struct = layoutBuilder.getStruct(signature.name)


        throw NotImplementedError()
        //return BuiltFunction(result.signature, generator, struct, 0)
    }
}






