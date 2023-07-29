package compiler.backends.emulator

import ast.FunctionType
import compiler.BackendCompiler
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*
import requireNotReached
import kotlin.Error

class EmulatorRunner(
    private val builtInProvider: BuiltInProvider
) : BackendCompiler {

    fun compileIntermediate(
        allTypes: List<Datatype>,
        functions: List<FunctionContent>,
        globals: GlobalsResult
    ): CompiledProgram {
        return Compiler(builtInProvider, allTypes, globals, functions).buildProgram()
    }

    override fun buildAndRun(
        allTypes: List<Datatype>,
        functions: List<FunctionContent>,
        globals: GlobalsResult
    ): List<Int> {
        val program = compileIntermediate(allTypes, functions, globals)


        val emulator = DefaultEmulator()
        emulator.setProgram(program.instructions)
        emulator.run()

        return emulator.outputStream
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
    fun buildDefinition(definition: FunctionDefinition): BuiltFunction
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
) : FunctionSource {
    override val definition = functionContent.definition
    override fun build(): BuiltFunction {
        return buildFunctionBody(functionContent, globals)
    }
}

val mainDefinition = DefinitionBuilder("main")
    .setReturnType(Primitives.Nothing)
    .getDefinition()


class Compiler(
    val builtInProvider: BuiltInProvider,
    types: List<Datatype>,
    val globals: GlobalsResult,
    val intermediateFunctions: List<FunctionContent>,
) : TypeProvider, FunctionSignatureResolver, CodeGenerator {

    private val resultingInstructions = mutableListOf<EmulatorInstruction>()

    val includedTypes = types.associateBy { it.name }

    private val availableFunctionDefinitions = mutableSetOf<FunctionDefinition>()

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingInstructions.add(emulatorInstruction)
    }

    override fun getType(name: String): Datatype {
        if (name.isEmpty()) {
            return Primitives.Nothing
        }
        if (name !in includedTypes) {
            throw CompileError("No type with name $name found")
        }
        return includedTypes.getValue(name)
    }

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionDefinition {
        for (definition in availableFunctionDefinitions) {
            if (definition.signature.matches(name, functionType, parameterTypes)) {
                return definition
            }
        }
        TODO(listOf(name, parameterTypes.toString(), functionType.toString()).toString())
    }

    override fun getFunctionDefinitionMatchingName(name: String): FunctionDefinition {
        return availableFunctionDefinitions.find { it.signature.name==name }
            ?:throw CompileError("No type with name $name found")
    }

    fun buildProgram(): CompiledProgram {

        // Build the globals
        val globalsLayout = LayedOutStruct(globals.fields)

        // the globals is placed at address 0
        // stack begins after the globals
        val stackStart = globalsLayout.size
        /// Add the header, should always be present

        addInstruction(emulate(DefaultEmulator.ldfp, "val" to stackStart))
        addInstruction(emulate(DefaultEmulator.ldsp, "val" to stackStart))

        if (globals.needsInitialization) {
            addInstruction(
                emulate(
                    DefaultEmulator.call_addr, "addr" to Reference(
                        initializeGlobalsDefinition,
                        functionEntryLabel
                    )
                )
            )
        }
        addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(mainDefinition, functionEntryLabel)))
        addInstruction(emulate(DefaultEmulator.exit))


        /// Find all available function signatures

        val functionSources = mutableListOf<FunctionSource>()


        for (signature in builtInProvider.getDefinitions()) {
            functionSources.add(BuiltinSource(builtInProvider, signature))
            availableFunctionDefinitions.add(signature)
        }
        availableFunctionDefinitions.addAll(builtinInlinedSignatures)

        for (f in intermediateFunctions) {
            availableFunctionDefinitions.add(f.definition)
            functionSources.add(CodeSource(f, globalsLayout))
        }

        // Then add the main-function and all functions it references (recursively)
        val alreadyPlaced = mutableSetOf<FunctionDefinition>()

        val toPlace = mutableListOf<FunctionDefinition>(mainDefinition)

        if (globals.needsInitialization){
            toPlace.add(initializeGlobalsDefinition)
        }


        while (toPlace.isNotEmpty()) {

            val top = toPlace.removeLast()

            if (alreadyPlaced.contains(top)) {
                continue
            }

            val source = functionSources.find{it.definition == top}
                ?: requireNotReached()

            val builtFunction = source.build()


            // Add all dependents to be included
            val notAddedDependents = builtFunction.getDependents()
                .filter { it !in alreadyPlaced }
                .filter { it != top }

            toPlace.addAll(notAddedDependents)


            alreadyPlaced.add(top)

            builtFunction.instructions.forEach { addInstruction(it) }
        }

        return CompiledProgram(resultingInstructions)
    }
}