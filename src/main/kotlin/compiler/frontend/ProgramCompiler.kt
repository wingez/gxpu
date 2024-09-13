package compiler.frontend

import ast.AstNode
import ast.AstParser
import ast.NodeTypes
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
    fun compile(): CompiledIntermediateProgram {


        // Step 1
        // Tokenize mainfile and all subsequent imports

        val nodesForFile = mutableMapOf<String, List<AstNode>>()
        val importsForFile = mutableMapOf<String, List<String>>()

        val filesToInclude = mutableListOf(mainFile)

        while (filesToInclude.isNotEmpty()) {

            val filename = filesToInclude.removeLast()
            if (filename in nodesForFile) {
                continue
            }

            val reader = fileProvider.getReader(filename)
                ?: throw FrontendCompilerError("File $filename not found.")

            val tokens = parseFile(reader, filename)
            val nodes = AstParser(tokens).parse()

            nodesForFile[filename] = nodes


            val importNodes = nodes.filter { it.type == NodeTypes.Import }
            val imports = importNodes.map { it.asIdentifier() }

            importsForFile[filename] = imports

            filesToInclude.addAll(imports)
        }

        // Step 2. Determine compilation order. First compile file with the least dependents
        // TODO: Remove recursion I dont like it
        val compilationOrder = mutableListOf<String>()

        fun recursiveAddToCompilationOrder(filename: String) {
            if (filename in compilationOrder) {
                return
            }
            for (dependent in importsForFile.getValue(filename)) {
                recursiveAddToCompilationOrder(dependent)
            }
            compilationOrder.add(filename)
        }

        recursiveAddToCompilationOrder(mainFile)

        // Step 3
        // Compile each file in order

        val compiledFunctions = mutableListOf<FunctionContent>()

        for (filename in compilationOrder) {
            val output = compileFile(filename, nodesForFile.getValue(filename), symbolTable)
            compiledFunctions.addAll(output.functions)
        }


        // Extract results
        val mainFunction = compiledFunctions.find { it.definition == mainDefinitionInFile(mainFile) }
            ?: throw FrontendCompilerError("missing main function")

        return CompiledIntermediateProgram(symbolTable, compiledFunctions, mainFunction)
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
        symbolTable,
        "",
        VariableType.Local,
    )
    return CompiledIntermediateProgram(
        symbolTable, functionContents, functionContents.find { it.definition == definition }!!,
    )
}