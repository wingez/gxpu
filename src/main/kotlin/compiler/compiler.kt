package compiler

import ast.AstNode
import ast.FunctionType
import ast.NodeTypes
import compiler.frontend.*

typealias Filename = String

interface BackendCompiler {
    fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String>
}


fun compileAndRunBody(
    body: String,
    backendCompiler: BackendCompiler,
    symbolTable: MutableSymbolTable,
): List<String> {
    val f = compileProgramFromSingleBody(body, symbolTable)
    return backendCompiler.buildAndRun(f)
}

