package compiler

import ast.AstNode
import ast.AstParser
import ast.FunctionType
import ast.NodeTypes
import compiler.frontend.*
import requireNotReached
import tokens.Token
import tokens.TokenType
import tokens.parseFile
import java.io.File
import java.io.Reader
import java.io.StringReader

val mainSignature = FunctionSignature("main", emptyList(), Datatype.Void, FunctionType.Normal)

interface BackendCompiler {
    fun buildAndRun(allTypes: List<Datatype>, functions: List<FunctionContent>, globals: GlobalsResult): List<Int>
}

interface BuiltInCollection : TypeProvider, FunctionSignatureResolver {
    val types: List<Datatype>
    val functions: List<FunctionSignature>

    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionSignature {
        return FunctionCollection(functions).getFunctionDefinitionMatching(name, functionType, parameterTypes)
    }

    override fun getType(name: String): Datatype? {
        return types.find { it.name == name }
    }
}

class FunctionCollection(
    private val signatures: List<FunctionSignature>,
) : FunctionSignatureResolver {
    override fun getFunctionDefinitionMatching(
        name: String,
        functionType: FunctionType,
        parameterTypes: List<Datatype>
    ): FunctionSignature {
        return signatures.find { it.matches(name, functionType, parameterTypes) }
            ?: throw FrontendCompilerError("write something here $name $functionType $parameterTypes")
    }
}

class TypeCollection(
    nodes: List<AstNode>,
    builtIns: BuiltInCollection
) : TypeProvider {

    private val result = mutableListOf<Datatype>()

    val allTypes get() = result

    override fun getType(name: String): Datatype? {
        return result.find { it.name == name }
    }

    init {
        fun addType(type: Datatype) {
            if (getType(type.name) != null) {
                throw FrontendCompilerError("Datatype with name: ${type.name} already added")
            }
            result.add(type)
        }

        for (type in builtIns.types) {
            addType(type)
        }

        for (node in nodes) {
            require(node.type == NodeTypes.Struct)
            val struct = buildStruct(node, this)
            addType(struct)
        }
    }
}


fun compileAndRunProgram(
    fileName: String,
    backendCompiler: BackendCompiler,
    builtIns: BuiltInCollection
): List<Int> {
    return compileAndRunProgram(
        File(fileName).inputStream().reader(),
        fileName, backendCompiler, builtIns
    )

}


fun compileAndRunProgram(
    reader: Reader,
    fileName: String,
    backendCompiler: BackendCompiler,
    builtIns: BuiltInCollection
): List<Int> {

    val tokens = parseFile(reader, fileName)
    val nodes = AstParser(tokens).parse()

    val (globalsAndInitializationNodes, structAndFunctionNodes) = nodes.partition {
        when (it.type) {
            NodeTypes.Function, NodeTypes.Struct -> false
            else -> true
        }
    }

    val (structNodes, functionNodes) = structAndFunctionNodes.partition {
        when (it.type) {
            NodeTypes.Struct -> true
            NodeTypes.Function -> false
            else -> requireNotReached()
        }
    }

    val types = TypeCollection(structNodes, builtIns)


    val allAvailableFunctionSignatures = mutableListOf<FunctionSignature>()

    builtIns.functions.forEach { allAvailableFunctionSignatures.add(it) }

    val functionBodiesWithDefinitions = mutableListOf<Pair<AstNode, FunctionDefinition>>()
    for (node in functionNodes) {
        val definition = definitionFromFunctionNode(node, types)
        allAvailableFunctionSignatures.add(definition.signature)
        functionBodiesWithDefinitions.add(node to definition)
    }


    val functionSignatureResolver = FunctionCollection(allAvailableFunctionSignatures)

    val globals = compileGlobalAndInitialization(
        globalsAndInitializationNodes,
        functionSignatureResolver, types,
    )

    val functions = functionBodiesWithDefinitions.map { (node, definition) ->
        compileFunctionBody(node.asFunction().body, definition, globals.variables, functionSignatureResolver, types)
    } + globals.initialize

    return backendCompiler.buildAndRun(types.allTypes, functions, globals)

}

fun compileAndRunBody(
    body: String,
    backendCompiler: BackendCompiler,
    builtIns: BuiltInCollection,
): List<Int> {
    val f = compileFunctionBody(body, builtIns)
    return backendCompiler.buildAndRun(
        builtIns.types,
        listOf(f),
        compileGlobalAndInitialization(emptyList(), builtIns, builtIns))
}

