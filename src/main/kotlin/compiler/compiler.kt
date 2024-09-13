package compiler

import ast.AstNode
import ast.FunctionType
import ast.NodeTypes
import compiler.frontend.*

interface BackendCompiler {
    fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String>
}


fun compileAndRunProgram(
    fileName: String,
    backendCompiler: BackendCompiler,
    symbolTable: MutableSymbolTable,
): List<String> {
    val compiledProgram = compileProgram(fileName, symbolTable)
    return backendCompiler.buildAndRun(compiledProgram)
}

fun compileAndRunBody(
    body: String,
    backendCompiler: BackendCompiler,
    symbolTable: MutableSymbolTable,
): List<String> {
    val f = compileProgramFromSingleBody(body, symbolTable)
    return backendCompiler.buildAndRun(f)
}

