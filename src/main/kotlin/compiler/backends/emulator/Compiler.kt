package compiler.backends.emulator

import compiler.BackendCompiler
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*
import kotlin.Error

class EmulatorRunner(
    private val builtInProvider: BuiltInProvider
) : BackendCompiler {

    fun compileIntermediate(
        intermediateProgram: CompiledIntermediateProgram,
    ): CompiledProgram {
        return Compiler(builtInProvider, intermediateProgram).buildProgram()
    }

    override fun buildAndRun(
        intermediateProgram: CompiledIntermediateProgram,
    ): List<String> {
        val program = compileIntermediate(intermediateProgram)


        val emulator = DefaultEmulator()
        emulator.setProgram(program.instructions)
        emulator.run()

        return emulator.outputStream.map { it.toChar() }.joinToString("").lines().filter { it.isNotBlank() }
    }
}


class EmulatorBackendCompilerError(message: String) : Error(message)
interface CodeGenerator {
    fun addInstruction(emulatorInstruction: EmulatorInstruction)
}


data class CompiledProgram(
    val instructions: List<EmulatorInstruction>
)

interface BuiltInProvider {
    fun getDefinitions(): List<FunctionDefinition>
    fun buildDefinition(signature: FunctionDefinition): BuiltFunction
}

private interface FunctionSource {
    val definition: FunctionDefinition
    fun build(): BuiltFunction
}

private class BuiltinSource(
    val builtInProvider: BuiltInProvider,
    override val definition: FunctionDefinition,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return builtInProvider.buildDefinition(definition)
    }
}

private class CodeSource(
    private val functionContent: FunctionContent,
    private val globals: LayedOutDatatype,
    val fields: CompositeDatatype,
) : FunctionSource {
    override val definition = functionContent.definition
    override fun build(): BuiltFunction {
        return buildFunctionBody(functionContent, globals, fields)
    }
}


class Compiler(
    val builtInProvider: BuiltInProvider,
    val intermediateProgram: CompiledIntermediateProgram,
) : CodeGenerator {

    private val resultingInstructions = mutableListOf<EmulatorInstruction>()

    private val availableFunctionSignatures = mutableSetOf<FunctionDefinition>()

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingInstructions.add(emulatorInstruction)
    }

    fun buildProgram(): CompiledProgram {

        // Build the globals
        val allGlobals = intermediateProgram.symbolTable.getAllGlobalVariables()
        val allGlobalsFields = CompositeDatatype("globals",
            allGlobals.map { CompositeDataTypeField(it.name, it.datatype) })
        val globalsLayout = LayedOutStruct(allGlobalsFields)

        // the globals is placed at address 0
        // stack begins after the globals
        val stackStart = globalsLayout.size
        /// Add the header, should always be present

        addInstruction(emulate(DefaultEmulator.ldfp, "val" to stackStart))
        addInstruction(emulate(DefaultEmulator.ldsp, "val" to stackStart))


        val toPlace = mutableListOf(intermediateProgram.mainFunction.definition)

        addInstruction(
            emulate(
                DefaultEmulator.call_addr,
                "addr" to Reference(intermediateProgram.mainFunction.definition, functionEntryLabel)
            )
        )
        addInstruction(emulate(DefaultEmulator.exit))


        /// Find all available function signatures

        val functionSources = mutableListOf<FunctionSource>()


        for (signature in builtInProvider.getDefinitions()) {
            functionSources.add(BuiltinSource(builtInProvider, signature))
            availableFunctionSignatures.add(signature)
        }
        availableFunctionSignatures.addAll(builtinInlinedSignatures)

        for (f in intermediateProgram.functions) {

            val localVariables = intermediateProgram.symbolTable.getVariablesForFunction(f.definition)

            val fields = CompositeDatatype("fields", localVariables.map { CompositeDataTypeField(it.name, it.datatype) })


            availableFunctionSignatures.add(f.definition)
            functionSources.add(CodeSource(f, globalsLayout, fields))
        }

        /// Compile all functions
        val compiledFunctions = mutableMapOf<FunctionDefinition, BuiltFunction>()

        for (source in functionSources) {
            val builtFunction = source.build()
            compiledFunctions[source.definition] = builtFunction
        }

        // Then add the main-function and all functions it references (recursively)
        val alreadyPlaced = mutableSetOf<FunctionDefinition>()


        while (toPlace.isNotEmpty()) {

            val top = toPlace.removeLast()

            if (alreadyPlaced.contains(top)) {
                continue
            }

            val included = compiledFunctions.getValue(top)

            // Add all dependents to be included
            val notAddedDependents = included.getDependents()
                .filter { it !in alreadyPlaced }
                .filter { it != top }

            toPlace.addAll(notAddedDependents)


            alreadyPlaced.add(included.definition)

            included.instructions.forEach { addInstruction(it) }
        }

        return CompiledProgram(resultingInstructions)
    }
}