package compiler.backends.emulator

import compiler.backends.emulator.emulator.DefaultEmulator
import compiler.frontend.Datatype
import compiler.frontend.TypeProvider
import compiler.frontend.buildStruct
import se.wingez.ast.AstNode
import se.wingez.ast.FunctionType
import se.wingez.ast.NodeTypes
import se.wingez.compiler.backends.emulator.EmulatorInstruction
import se.wingez.compiler.backends.emulator.Reference
import se.wingez.compiler.backends.emulator.emulate
import se.wingez.compiler.frontend.*

interface CodeGenerator {
    fun addInstruction(emulatorInstruction: EmulatorInstruction)
}

data class CompiledProgram(
    val instructions: List<EmulatorInstruction>
)

interface BuiltInProvider {
    fun getSignatures(): List<FunctionDefinition>
    fun getTypes(): Map<String, Datatype>

    fun buildSignature(signature: FunctionDefinition): BuiltFunction
}

private interface FunctionSource {
    val signature: FunctionDefinition
    fun build(): BuiltFunction
}

private class BuiltinSource(
    val builtInProvider: BuiltInProvider,
    override val signature: FunctionDefinition,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return builtInProvider.buildSignature(signature)
    }
}

private class CodeSource(
    val node: AstNode,
    override val signature: FunctionDefinition,
    val typeProvider: TypeProvider,
    val functionProvider: FunctionDefinitionResolver,
    val datatypeLayoutProvider: DatatypeLayoutProvider,
) : FunctionSource {

    override fun build(): BuiltFunction {
        return buildFunctionBody(node, signature, functionProvider, typeProvider, datatypeLayoutProvider)
    }
}

val mainSignature = SignatureBuilder("main")
    .setReturnType(Datatype.Void)
    .getSignature()

class Compiler(
    val builtInProvider: BuiltInProvider,
    val nodes: List<AstNode>
) : TypeProvider, FunctionDefinitionResolver, CodeGenerator {

    private val resultingInstructions = mutableListOf<EmulatorInstruction>()

    private val functionSources = mutableListOf<FunctionSource>()

    val includedTypes = mutableMapOf<String, Datatype>()


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
    ): FunctionDefinition {
        for (source in functionSources) {
            if (source.signature.matches(name, functionType, parameterTypes)) {
                return source.signature
            }
        }
        TODO()
    }

    fun buildStructs() {
        for (node in nodes.filter { it.type == NodeTypes.Struct }) {

            val struct = buildStruct(node, this)

            if (struct.name in includedTypes) {
                throw CompileError("Function ${struct.name} already exists")
            }
            //TODO:
            //includedTypes[struct.name] = struct
        }
    }

    fun buildProgram(): CompiledProgram {

        /// Add the header, should always be present


        addInstruction(emulate(DefaultEmulator.ldfp, "val" to STACK_START))
        addInstruction(emulate(DefaultEmulator.ldsp, "val" to STACK_START))

        addInstruction(emulate(DefaultEmulator.call_addr, "addr" to Reference(mainSignature, functionEntryLabel)))
        addInstruction(emulate(DefaultEmulator.exit))


        /// Find all types
        includedTypes.putAll(builtInProvider.getTypes())
        buildStructs()

        /// Find all available function signatures
        for (signature in builtInProvider.getSignatures()) {
            functionSources.add(BuiltinSource(builtInProvider, signature))
        }
        for (functionNode in nodes.filter { it.type == NodeTypes.Function }) {
            val signature = FunctionDefinition.fromFunctionNode(functionNode, this)
            functionSources.add(CodeSource(functionNode, signature, this, this, dummyDatatypeSizeProvider))
        }

        /// Compile all functions
        val compiledFunctions = mutableMapOf<FunctionDefinition, BuiltFunction>()

        for (source in functionSources) {
            val builtFunction = source.build()
            compiledFunctions[source.signature] = builtFunction
        }

        // Then add the main-function and all functions it references (recursively)
        val alreadyPlaced = mutableSetOf<FunctionDefinition>()

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

val dummyDatatypeSizeProvider = object : DatatypeLayoutProvider {
    override fun sizeOf(dataType: Datatype): Int {
        return when (dataType) {
            Datatype.Integer -> 1
            Datatype.Boolean -> 1
            else -> throw TODO(dataType.toString())
        }


    }
}