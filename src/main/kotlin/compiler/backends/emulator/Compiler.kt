package compiler.backends.emulator

import ast.FunctionType
import compiler.BackendCompiler
import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.*
import kotlin.Error


class EmulatorRunner(
    private val builtInProvider: BuiltInProvider
) : BackendCompiler {

    fun compileIntermediate(allTypes: List<Datatype>, functions: List<FunctionContent>):CompiledProgram{
        return Compiler(builtInProvider, allTypes, functions).buildProgram()
    }
    override fun buildAndRun(allTypes: List<Datatype>, functions: List<FunctionContent>): List<String> {
        val program = compileIntermediate(allTypes, functions)


        val emulator = DefaultEmulator()
        emulator.setProgram(program.instructions)
        emulator.run()

        return emulator.outputStream.map { it.toString() }
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
    fun getSignatures(): List<FunctionSignature>
    fun buildSignature(signature: FunctionSignature): BuiltFunction
}

private interface FunctionSource {
    val signature: FunctionSignature
    fun build(): BuiltFunction
}

private class BuiltinSource(
    val builtInProvider: BuiltInProvider,
    override val signature: FunctionSignature,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return builtInProvider.buildSignature(signature)
    }
}

private class CodeSource(
    private val functionContent: FunctionContent,
) : FunctionSource {
    override val signature=functionContent.definition.signature
    override fun build(): BuiltFunction {
        return buildFunctionBody(functionContent)
    }
}

val mainSignature = SignatureBuilder("main")
    .setReturnType(Datatype.Void)
    .getSignature()


class Compiler(
    val builtInProvider: BuiltInProvider,
    types: List<Datatype>,
    val intermediateFunctions: List<FunctionContent>,
) : TypeProvider, FunctionSignatureResolver, CodeGenerator {

    private val resultingInstructions = mutableListOf<EmulatorInstruction>()

    val includedTypes = types.associateBy { it.name }

    private val availableFunctionSignatures = mutableSetOf<FunctionSignature>()

    override fun addInstruction(emulatorInstruction: EmulatorInstruction) {
        resultingInstructions.add(emulatorInstruction)
    }

    override fun getType(name: String): Datatype {
        if (name.isEmpty()) {
            return Datatype.Void
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
    ): FunctionSignature {
        for (definition in availableFunctionSignatures) {
            if (definition.matches(name, functionType, parameterTypes)) {
                return definition
            }
        }
        TODO(listOf(name, parameterTypes.toString(), functionType.toString()).toString())
    }

    fun buildProgram(): CompiledProgram {

        /// Add the header, should always be present


        addInstruction(emulate(DefaultEmulator.ldfp, "val" to STACK_START))
        addInstruction(emulate(DefaultEmulator.ldsp, "val" to STACK_START))

        addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(mainSignature, functionEntryLabel)))
        addInstruction(emulate(DefaultEmulator.exit))


        /// Find all available function signatures

        val functionSources = mutableListOf<FunctionSource>()


        for (signature in builtInProvider.getSignatures()) {
            functionSources.add(BuiltinSource(builtInProvider, signature))
            availableFunctionSignatures.add(signature)
        }
        availableFunctionSignatures.addAll(builtinInlinedSignatures)

        for (f in intermediateFunctions) {
            availableFunctionSignatures.add(f.definition.signature)
            functionSources.add(CodeSource(f))
        }

        /// Compile all functions
        val compiledFunctions = mutableMapOf<FunctionSignature, BuiltFunction>()

        for (source in functionSources) {
            val builtFunction = source.build()
            compiledFunctions[source.signature] = builtFunction
        }

        // Then add the main-function and all functions it references (recursively)
        val alreadyPlaced = mutableSetOf<FunctionSignature>()

        val toPlace = mutableListOf(mainSignature)

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


            alreadyPlaced.add(included.signature)

            included.instructions.forEach { addInstruction(it) }
        }

        return CompiledProgram(resultingInstructions)
    }
}