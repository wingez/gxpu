package compiler.backendemulator

import compiler.backends.emulator.*
import compiler.backends.emulator.emulator.DefaultEmulator
import org.junit.jupiter.api.Test
import ast.*
import compiler.BackendCompiler
import compiler.BuiltInSignatures
import compiler.backends.emulator.EmulatorInstruction
import compiler.backends.emulator.Reference
import compiler.backends.emulator.builtinInlinedSignatures
import compiler.backends.emulator.emulate
import compiler.compileAndRunProgram
import compiler.frontend.*
import java.io.StringReader
import kotlin.test.assertEquals


class DummyBuiltInProvider(
    private val builtIns: List<BuiltIn> = listOf(ByteAddition(), ByteSubtraction(), PrintIntArray())
) : BuiltInProvider, FunctionSignatureResolver {
    override fun getSignatures(): List<FunctionSignature> {
        return builtIns.map { it.signature }
    }


    override fun buildSignature(signature: FunctionSignature): BuiltFunction {
        for (builtIn in builtIns) {
            if (builtIn.signature == signature) {

                val instructions = builtIn.compile()
                instructions.first().addReference(Reference(signature, functionEntryLabel))

                val variables = mutableListOf<CompositeDataTypeField>()
                if (builtIn.signature.returnType != Primitives.Nothing) {
                    variables.add(
                        CompositeDataTypeField(
                            "result",
                            builtIn.signature.returnType,
                        )
                    )
                }
                for ((index, parameterType) in builtIn.signature.parameterTypes.withIndex()) {
                    variables.add(CompositeDataTypeField("param$index", parameterType))
                }
                val definition = FunctionDefinition(signature, builtIn.signature.parameterTypes.map { it.name })

                val layout = calculateLayout(definition, CompositeDatatype(signature.name, variables))

                return BuiltFunction(builtIn.signature, layout, instructions)
            }
        }
        throw AssertionError()
    }

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionSignature {

        for (definition in builtinInlinedSignatures) {
            if (definition.matches(name, functionType, parameterTypes)) {
                return definition
            }
        }

        for (builtIn in builtIns) {
            if (builtIn.signature.matches(name, functionType, parameterTypes)) {
                return builtIn.signature
            }
        }

        throw AssertionError("No function matching: $name$parameterTypes")
    }
}

private val noGlobals = LayedOutStruct(CompositeDatatype("noglobals", emptyList()))

private class GetInstructionsRunner : BackendCompiler {

    lateinit var compiledProgram: CompiledProgram
    override fun buildAndRun(
        allTypes: List<Datatype>,
        functions: List<FunctionContent>,
        globals: GlobalsResult
    ): List<Int> {
        compiledProgram = EmulatorRunner(BuiltInFunctions()).compileIntermediate(allTypes, functions, globals)
        return emptyList()
    }
}


fun buildSingleMainFunction(nodes: List<AstNode>): CompiledProgram {
    TODO()
//    val node = function("main", emptyList(), nodes, "")
//    val c = Compiler(DummyBuiltInProvider(), listOf(node))
//    return c.buildProgram()
}

fun buildBody(body: String): List<EmulatorInstruction> {

    val intermediate = compileFunctionBody(body, BuiltInSignatures())

    return buildFunctionBody(intermediate, noGlobals).instructions
}


fun buildProgram(body: String): CompiledProgram {

    val runner = GetInstructionsRunner()
    compileAndRunProgram(StringReader(body), "dummyfile", runner, BuiltInSignatures())

    return runner.compiledProgram
}

fun shouldMatch(code: List<EmulatorInstruction>, expected: List<EmulatorInstruction>) {

    //Fixme compare references also


    assertEquals(
        expected.map { it.instruction }, code.map { it.instruction },
        (listOf("Disassembled: ") + code.map { it.toString() }).joinToString(
            "\n"
        )
    )
}

fun bodyShouldMatchAssembled(body: String, expectedAssembly: String) {

    val code = buildBody(body)
    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))

    shouldMatch(code, expected)
}

fun programShouldMatchAssembled(program: String, expectedAssembly: String) {
    val code = buildProgram(program)
    val expected = DefaultEmulator().instructionSet.assembleMnemonicFile(StringReader(expectedAssembly))
    shouldMatch(code.instructions, expected)
}

class CompilerCompareWithAssembly {
    @Test
    fun testFpOffset() {


        /**
        #Expected
        ldfp #100
        ldsp #100
        subsp #0
        subsp #0
        call #7
        exit

        ret
        ret
         */
        val program = """
           def main():
             return
        """.trimIndent()

        val code = buildProgram(program)
        assertEquals(
            listOf(
                // Init stack and frame
                emulate(DefaultEmulator.ldfp, "val" to 0),
                emulate(DefaultEmulator.ldsp, "val" to 0),
                // Call
                emulate(DefaultEmulator.call_addr, "addr" to Reference(mainSignature, functionEntryLabel)),
                // On return
                emulate(DefaultEmulator.exit),
                //Start of function
                emulate(DefaultEmulator.ret),
                emulate(DefaultEmulator.ret),
            ), code.instructions
        )
    }

    @Test
    fun testBasicAssign() {

        val expected = """
        addsp #1
        push #5
        pop [FP #0]
        ret
        """

        val body = """
          val var:byte=5
        """
        bodyShouldMatchAssembled(body, expected)

    }

    @Test
    fun testCopy() {
        val expected = """
        ADDSP #2
        PUSH #5
        POP[FP #0]
        PUSH [FP #0]
        POP [FP #1]
        RET
        """

        val body = """
          val val1:byte=5
          val val2:byte=val1
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testPrint() {
        val expected = """
        // main
        LDA #5
        OUT
        ret
        """

        val body = """
          print(5)
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfNoElse() {
        val expected = """
        LDA #5
        TSTNZ A
       
        jmpnf #end
        LDA #1
        OUT
        :end
        RET
        """
        val body = """
          if bool(5):
            print(1)
        """
        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testIfElse() {
        val expected = """
        LDA #5
        TSTNZ A
        JMPNF #else
        LDA #1
        OUT
        JMP #end
        :else
        LDA #2
        OUT
        :end
        RET
        """
        val body = """
          if bool(5):
            print(1)
          else:
            print(2)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testWhile() {
        val expected = """
        LDA #5
        TSTNZ A
        :loop
        JMPNF #end
        LDA #1
        OUT
        JMP #loop
        :end
        RET 
        """
        val body = """
          while bool(5):
            print(1)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testCall() {
        val expected = """
        LDFP #0
        LDSP #0
        CALL #main
        exit
        
        :main
        CALL #test1
        LDA #3
        OUT
        RET
        
        :test1 
        LDA #10
        OUT
        RET
        
        """
        val body = """
          def test1():
            print(10)
            
          def main():
            test1()
            print(3)
        """

        programShouldMatchAssembled(body, expected)
    }

    @Test
    fun testPointerRead() {
        val body = """
          def main():
            val i=5
           
            val p=&i
            print(*p)  
          
        """

        val expected = """
        LDFP #100
        LDSP #100
        CALL #main
        exit
        
        :main
        ADDSP #2
        
        //i=5
        PUSH #5
        POP [FP #0]
        
        //p=&i
        LDA FP #0
        PUSHA
        POP [FP #1]
        
        //print(*p)
        LDA [FP #1]
        LDA [A #0]
        out
        
        RET
        """

        programShouldMatchAssembled(body, expected)
    }

    @Test
    fun testPointerWrite() {

        val body = """
          def main():
            val i=5
            val p=&i
            *p=10  
          
        """


        val expected = """
        LDFP #100
        LDSP #100
        CALL #main
        exit
        
        :main
        ADDSP #2
        
        //i=5
        PUSH #5
        POP [FP #0]
        //p=&1
        LDA FP #0
        PUSHA
        POP [FP #1]
        //*p=10
        PUSH #10
        LDA [FP #1]
        POP [A #0]
        
        RET
        """

        programShouldMatchAssembled(body, expected)
    }

    @Test
    fun createArray() {
        val expected = """
          ADDSP #1
          
          PUSH #5
          LDA SP #-1
          ADDSP [SP #-1]
          PUSHA 
          POP [FP #0]
          ret
        """
        val body = """
          val i = createArray(5)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun readArray() {
        val expected = """
          ADDSP #1
          
          PUSH #5
          LDA SP #-1
          ADDSP [SP #-1]
          PUSHA 
          POP [FP #0]
          ret
        """
        val body = """
          val i = createArray(5)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun readStruct() {
        var expected = """
          ADDSP #2
          
          PUSH [FP #0]
          SUBSP #1
          ret
        """
        var body = """
          val i:intpair
          i.first
        """

        bodyShouldMatchAssembled(body, expected)

        expected = """
          ADDSP #2
          
          PUSH [FP #1]
          SUBSP #1
          ret
        """
        body = """
          val i:intpair
          i.second
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun writeStruct() {
        var expected = """
          ADDSP #2
          
          PUSH #5
          POP [FP #0]
          ret
        """
        var body = """
          val i:intpair
          i.first=5
        """

        bodyShouldMatchAssembled(body, expected)

        expected = """
          ADDSP #2
          
          PUSH #5
          POP [FP #1]
          ret
        """
        body = """
          val i:intpair
          i.second=5
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun pointerMemberAccess() {
        val expected = """
          ADDSP #3
          
          //val p =&x
          LDA FP #0
          PUSHA
          POP [FP #2]
          
          //print(p->first)
          LDA [FP #2]
          ADDA #0
          LDA [A #0]
          out

          //print(p->first)
          LDA [FP #2]
          ADDA #1
          LDA [A #0]
          out

          ret
        """
        val body = """
            val x:intpair
            
            val p =&x
            
            print(p->first)
            print(p->second)
        """

        bodyShouldMatchAssembled(body, expected)
    }

    @Test
    fun testReadWriteGlobal() {
        val expected = """
          LDFP #1
          LDSP #1
          CALL #initglobals
          CALL #main
          exit
          
          :initglobals
          ADDSP #1
          LDA #5
          STA [#0]
          RET
          :main
          LDA [#0] 
          out
          RET
        """
        val program = """
            
            val i=5
            def main():
              print(i)
        """

        programShouldMatchAssembled(program, expected)
    }

    @Test
    fun testCreateString() {
        val expected = """
            ADDSP #1
            PUSH #4
            PUSH #97
            PUSH #98
            PUSH #99
            PUSH #100
            LDA SP #-5
            PUSHA
            POP [FP #0]
            RET
        """.trimIndent()

        val program = """
              val a = "abcd"
        """.trimIndent()

        bodyShouldMatchAssembled(program, expected)
    }

}