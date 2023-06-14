package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import compiler.frontend.StructBuilder
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference
import se.wingez.compiler.backends.emulator.emulate
import se.wingez.compiler.frontend.FunctionDefinition
import se.wingez.compiler.frontend.Variable
import se.wingez.compiler.frontend.VariableType
import se.wingez.compiler.frontend.functionEntryLabel

interface BuiltIn {

    val signature: FunctionDefinition
    val sizeOfVars: Int
    val name: String
    fun compile(): List<EmulatorInstruction>
}


class Print : BuiltIn {
    override val name = "print"
    override val signature: FunctionDefinition
        get() = SignatureBuilder(name)
            .addParameter(Datatype.Integer)
            .getSignature()

    override val sizeOfVars = 0

    override fun compile(): List<EmulatorInstruction> {

        val valueOffset = -1
        return listOf(
            emulate(DefaultEmulator.lda_at_fp_offset, "offset" to valueOffset),
            emulate(DefaultEmulator.print),
            emulate(DefaultEmulator.ret),
        )
    }
}

class Bool : BuiltIn {
    override val name = "bool"
    override val signature: FunctionDefinition
        get() = SignatureBuilder(name)
            .addParameter(Datatype.Integer)
            .setReturnType(Datatype.Boolean)
            .getSignature()

    override fun compile(): List<EmulatorInstruction> {
        return emptyList()
    }

    override val sizeOfVars: Int = 0
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

        val instructions = result.compile()

        instructions.first().addReference(Reference(signature, functionEntryLabel))

        val variables = mutableListOf<Variable>()
        if (signature.returnType != Datatype.Void) {
            variables.add(Variable("result", signature.returnType, VariableType.Result))
        }
        for ((index, parameterType) in signature.parameterTypes.withIndex()) {
            variables.add(Variable("param$index", parameterType, VariableType.Parameter))
        }

        val layout = calculateLayout(variables, dummyDatatypeSizeProvider)

        return BuiltFunction(result.signature, layout, instructions)
    }
}






