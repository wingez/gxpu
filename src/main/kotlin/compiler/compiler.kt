package compiler

import ast.AstNode
import ast.AstParser
import ast.FunctionType
import ast.NodeTypes
import compiler.frontend.*
import tokens.parseFile
import java.io.StringReader

interface BackendCompiler {
    fun buildAndRun(allTypes: List<Datatype>, functions: List<FunctionContent>): List<String>
}

interface BuiltInCollection {
    val types: List<Datatype>
    val functions: List<FunctionDefinition>
}


private fun buildStructs(nodes: List<AstNode>, builtIns: BuiltInCollection): List<Datatype> {

    val result = mutableListOf<Datatype>()

    val typeProvider = object : TypeProvider {
        override fun getType(name: String): Datatype? {
            return result.find { it.name == name }
        }
    }

    fun addType(type: Datatype) {
        if (typeProvider.getType(type.name) != null) {
            throw FrontendCompilerError("Datatype with name: ${type.name} already added")
        }
        result.add(type)
    }

    for (type in builtIns.types) {
        addType(type)
    }

    for (node in nodes) {
        require(node.type == NodeTypes.Struct)
        val struct = buildStruct(node, typeProvider)
        addType(struct)
    }

    return result
}

fun compileAndRunProgram(
    program: String,
    fileName: String,
    backendCompiler: BackendCompiler,
    builtIns: BuiltInCollection
): List<String> {

    val tokens = parseFile(StringReader(program), fileName)
    val nodes = AstParser(tokens).parse()

    val (structNodes, functionNodes) = nodes.partition {
        when (it.type) {
            NodeTypes.Struct -> true
            NodeTypes.Function -> false
            else -> TODO()
        }
    }

    val allTypes = buildStructs(structNodes, builtIns)


    val allAvailableFunctionDefinitions = mutableListOf<FunctionDefinition>()


    val typeProvider = object : TypeProvider {
        override fun getType(name: String): Datatype? {
            return allTypes.find { it.name == name }
                ?: throw FrontendCompilerError("write something here")
        }

    }


    builtIns.functions.forEach { allAvailableFunctionDefinitions.add(it) }

    val functionBodiesWithDefinitions = mutableListOf<Pair<AstNode, FunctionDefinition>>()
    for (node in functionNodes) {
        val definition = FunctionDefinition.fromFunctionNode(node, typeProvider)
        allAvailableFunctionDefinitions.add(definition)
        functionBodiesWithDefinitions.add(node to definition)
    }


    val functionDefinitionResolver = object : FunctionDefinitionResolver {
        override fun getFunctionDefinitionMatching(
            name: String,
            functionType: FunctionType,
            parameterTypes: List<Datatype>
        ): FunctionDefinition {
            return allAvailableFunctionDefinitions.find { it.matches(name, functionType, parameterTypes) }
                ?: throw FrontendCompilerError("write something here")
        }

    }


    val functions = functionBodiesWithDefinitions.map { (node, definition) ->
        compileFunctionBody(node, definition, functionDefinitionResolver, typeProvider)
    }


    return backendCompiler.buildAndRun(allTypes, functions)

}




