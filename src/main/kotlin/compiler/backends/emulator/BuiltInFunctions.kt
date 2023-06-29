package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.frontend.*

interface BuiltIn {

    val signature: FunctionSignature
    val name: String
    fun compile(): List<EmulatorInstruction>
}

class ByteAddition : BuiltIn {
    override val name = OperatorBuiltIns.Addition
    override fun compile(): List<EmulatorInstruction> {
        return DefaultEmulator.instructionSet.assembleMnemonicFile(
            """
            POPA
            POP ADDA
            SUBSP #1
            STA [SP #0]
            ret
        """
        )
    }

    override val signature = BuiltInSignatures.add

}

class ByteSubtraction : BuiltIn {
    override val name = OperatorBuiltIns.Subtraction
    override fun compile(): List<EmulatorInstruction> {
        return DefaultEmulator.instructionSet.assembleMnemonicFile(
            """
            LDA [SP #-2]
            POP SUBA
            STA [FP #-3]
            ret
        """
        )
    }

    override val signature = BuiltInSignatures.sub
}

class PrintIntArray : BuiltIn {
    override val name = "print"
    override fun compile(): List<EmulatorInstruction> {
        return DefaultEmulator.instructionSet.assembleMnemonicFile(
            """
            // store size at FP+0
            LDA [FP #-1]
            LDA [A #0]
            STA [FP #0]
            
            // store current pointer at FP+1
            LDA [FP #-1]
            ADDA #1
            STA [FP #1]
            
            // 
            :loop
            LDA [FP #0]
            TSTZ A
            JMPF #ret
            
            //decrement size
            SUBA #1
            STA [FP #0]
            
            //print
            LDA [FP #1]
            LDA [A #0]
            out
            // increment pointer
            LDA [FP #1]
            ADDA #1
            STA [FP #1]
            
            JMP #loop
            
            
            :ret
            ret
        """
        )
    }

    override val signature = SignatureBuilder(name)
        .addParameter(Datatype.Str)
        .setReturnType(Datatype.Void)
        .setFunctionType(FunctionType.Normal)
        .getSignature()
}


class BuiltInFunctions : BuiltInProvider {

    private val available = listOf(
        ByteAddition(), ByteSubtraction(),
        PrintIntArray(),
    )

    override fun getSignatures(): List<FunctionSignature> {
        return available.map { it.signature }

    }

    override fun buildSignature(signature: FunctionSignature): BuiltFunction {

        val result = available.find { it.signature == signature }
            ?: TODO(signature.toString())

        val instructions = result.compile()

        instructions.first().addReference(Reference(signature, functionEntryLabel))

        val fields = mutableListOf<CompositeDataTypeField>()
        if (signature.returnType != Datatype.Void) {
            fields.add(CompositeDataTypeField("result", signature.returnType))
        }
        for ((index, parameterType) in signature.parameterTypes.withIndex()) {
            fields.add(CompositeDataTypeField("param$index", parameterType))
        }

        val layout =
            calculateLayout(FunctionDefinition(signature, emptyList()), Datatype.Composite(signature.name, fields))

        return BuiltFunction(result.signature, layout, instructions)
    }
}






