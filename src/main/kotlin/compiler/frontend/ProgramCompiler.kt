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
    val types: List<Datatype>,
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
    private val builtInCollection: BuiltInCollection = BuiltInSignatures(),
) {

    private val compiledFiles = mutableMapOf<String, CompiledIntermediateFile>()

    private fun compileFile(filename: String): CompiledIntermediateFile {

        if (filename !in compiledFiles) {
            val reader = fileProvider.getReader(filename)
                ?: throw FrontendCompilerError("Cant find import: $filename")

            val compiled = compileFile(filename, reader, this)

            compiledFiles[filename] = compiled
        }

        return compiledFiles.getValue(filename)
    }

    fun import(file: String): Pair<List<Datatype>, List<FunctionDefinition>> {
        if (file == builtins) {
            return builtInCollection.types to builtInCollection.functions
        }

        //TODO: detect cyclic import

        val compiled = compileFile(file)
        return compiled.allTypes to compiled.functions.map { it.definition }
    }

    fun importBuiltins(): Pair<List<Datatype>, List<FunctionDefinition>> {
        return import(builtins)
    }


    fun compile(): CompiledIntermediateProgram {
        val fileWithMain = compileFile(mainFile)

        val allTypes = compiledFiles.values.flatMap { it.allTypes }
        val allFunctions = compiledFiles.values.flatMap { it.functions }

        val globals = compiledFiles.values.map { it.globals }

        val mainFunction = fileWithMain.functions.find { it.definition == mainDefinitionInFile(mainFile) }
            ?: throw FrontendCompilerError("missing main function")

        return CompiledIntermediateProgram(allTypes, allFunctions, mainFunction, globals)
    }
}


fun compileProgram(filename: String, builtInCollection: BuiltInCollection): CompiledIntermediateProgram {

    val fileProvider = object : FileProvider {
        override fun getReader(f: String): Reader {
            require(filename == f)
            return File(f).inputStream().reader()
        }
    }

    return ProgramCompiler(fileProvider, filename, builtInCollection).compile()
}


fun compileProgramFromSingleBody(body: String, builtIns: BuiltInCollection): CompiledIntermediateProgram {
    //Used in testing

    val tokens =
        parseFile(StringReader(body), "dummyfile") + listOf(Token(TokenType.EndBlock, "", SourceInfo.notApplicable))
    val nodes = AstParser(tokens).parseStatementsUntilEndblock()

    val types = builtIns.types

    val definition = mainDefinitionInFile("dummyfile")

    val functionContents = compileFunctionBody(
        AstNode.fromBody(nodes),
        definition,
        emptyList(),
        FunctionCollection(builtIns.functions),
        TypeCollection(emptyList(), builtIns)
    )
    return CompiledIntermediateProgram(
        types, functionContents, functionContents.find { it.definition == definition }!!, emptyList()
    )
}