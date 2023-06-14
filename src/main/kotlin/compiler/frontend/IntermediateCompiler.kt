package se.wingez.compiler.frontend

import compiler.frontend.TypeProvider
import se.wingez.ast.AstNode

fun compileFunction(
    functionNode: AstNode,
    functionProvider: FunctionDefinitionResolver,
    typeProvider: TypeProvider
): FunctionContent {

    return FunctionCompiler(functionProvider, typeProvider).compileFunction(functionNode)

}
