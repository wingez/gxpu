package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import ast.FunctionType
import ast.expression.OperatorBuiltIns
import compiler.BuiltInSignatures
import compiler.frontend.*

interface BuiltIn {

    val definition: FunctionDefinition
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

    override val definition = BuiltInSignatures.add

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

    override val definition = BuiltInSignatures.sub
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

    override val definition = DefinitionBuilder(name)
        .addParameter("str", Primitives.Str)
        .setReturnType(Primitives.Nothing)
        .setFunctionType(FunctionType.Normal)
        .getDefinition()
}


class BuiltInFunctions : BuiltInProvider {

    private val available = listOf(
        ByteAddition(), ByteSubtraction(),
        PrintIntArray(),
    )

    override fun getDefinitions(): List<FunctionDefinition> {
        return available.map { it.definition }

    }

    override fun buildDefinition(definition: FunctionDefinition): BuiltFunction {

        val result = available.find { it.definition == definition }
            ?: TODO(definition.toString())

        val instructions = result.compile()

        instructions.first().addReference(Reference(definition, functionEntryLabel))

        val fields = mutableListOf<CompositeDataTypeField>()
        if (definition.returnType != Primitives.Nothing) {
            fields.add(CompositeDataTypeField("result", definition.returnType))
        }
        for ((index, parameter) in definition.parameters.withIndex()) {
            val (parameterName, parameterType) = parameter
            fields.add(CompositeDataTypeField(parameterName, parameterType))
        }

        val layout = calculateLayout(definition, CompositeDatatype(definition.name, fields))

        return BuiltFunction(result.definition, layout, instructions)
    }
}






