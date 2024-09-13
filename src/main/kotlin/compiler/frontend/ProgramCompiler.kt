package compiler.frontend

import ast.AstNode
import ast.AstParser
import compiler.*
import compiler.backends.emulator.emulator.main
import tokens.Token
import tokens.TokenType
import tokens.parseFile
import java.io.File
import java.io.Reader
import java.io.StringReader

interface FileProvider {
    fun getReader(filename: String): Reader?
}

private const val builtins = "Builtins"

data class CompiledIntermediateProgram(
    val symbolTable: SymbolTable,

    //FIXME remove these
    val functions: List<FunctionContent>,
    val mainFunction: FunctionContent,
    val globals: List<GlobalsResult>,
)

private fun mainDefinitionInFile(filename: String): FunctionDefinition {
    return DefinitionBuilder("main")
        .setSourceFile(filename)
        .getDefinition()
}


class ProgramCompiler(
    private val fileProvider: FileProvider,
    private val mainFile: String,
    private val symbolTable: MutableSymbolTable,
) {

    private val compiledFiles = mutableMapOf<String, CompiledIntermediateFile>()

    private fun compileFile(filename: String): CompiledIntermediateFile {

        if (filename !in compiledFiles) {
            val reader = fileProvider.getReader(filename)
                ?: throw FrontendCompilerError("Cant find import: $filename")

            val compiled = compileFile(filename, reader, this,symbolTable)

            compiledFiles[filename] = compiled
        }

        return compiledFiles.getValue(filename)
    }


    fun compile(): CompiledIntermediateProgram {
        val fileWithMain = compileFile(mainFile)

        val allTypes = compiledFiles.values.flatMap { it.allTypes }
        val allFunctions = compiledFiles.values.flatMap { it.functions }

        val globals = compiledFiles.values.map { it.globals }

        val mainFunction = fileWithMain.functions.find { it.definition == mainDefinitionInFile(mainFile) }
            ?: throw FrontendCompilerError("missing main function")

        return CompiledIntermediateProgram(symbolTable, allFunctions, mainFunction, globals)
    }
}


fun compileProgram(filename: String, symbolTable: MutableSymbolTable): CompiledIntermediateProgram {

    val fileProvider = object : FileProvider {
        override fun getReader(f: String): Reader {
            require(filename == f)
            return File(f).inputStream().reader()
        }
    }

    return ProgramCompiler(fileProvider, filename, symbolTable).compile()
}


fun compileProgramFromSingleBody(body: String, symbolTable: SymbolTable): CompiledIntermediateProgram {
    //Used in testing

    val tokens =
        parseFile(StringReader(body), "dummyfile") + listOf(Token(TokenType.EndBlock, "", SourceInfo.notApplicable))
    val nodes = AstParser(tokens).parseStatementsUntilEndblock()

    val definition = mainDefinitionInFile("dummyfile")

    val functionContents = compileFunctionBody(
        AstNode.fromBody(nodes),
        definition,
        emptyMap(),
        symbolTable,
        "",
        VariableType.Local,
    )
    return CompiledIntermediateProgram(
        symbolTable, functionContents, functionContents.find { it.definition == definition }!!, emptyList()
    )
}