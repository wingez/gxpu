package compiler.frontend

import ast.AstNode
import ast.AstParser
import ast.NodeTypes
import compiler.BuiltInSourceFile
import compiler.Filename
import compiler.backends.astwalker.Template
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
        val globalsInitFunctions = mutableListOf<FunctionContent>()

        for (filename in compilationOrder) {
            val output =
                compileFile(filename, nodesForFile.getValue(filename), symbolTable, importsForFile.getValue(filename))
            compiledFunctions.addAll(output.functions)
            globalsInitFunctions.add(output.globalInit)
        }

        // Extract results
        val mainFunction = compiledFunctions.find { it.definition == mainDefinitionInFile(mainFile) }
            ?: throw FrontendCompilerError("missing main function")

        // Create entry function setting up globals and calling main

        val entryCodeContent = mutableListOf<Instruction>()
        var counter = 0
        for (global in globalsInitFunctions) {
            if (global.hasContent) {

                val instr = TempValue((counter++).toString(), Call(global.definition, emptyList()))
                entryCodeContent.add(instr)
            }
        }

        val entryFunction: FunctionContent

        if (entryCodeContent.isNotEmpty()) {
            val instr = TempValue((counter++).toString(), Call(mainFunction.definition, emptyList()))
            entryCodeContent.add(instr)
            entryCodeContent.add(ReturnNothing())

            entryFunction = FunctionContent(
                DefinitionBuilder("entry")
                    .setSourceFile(BuiltInSourceFile)
                    .getDefinition(),
                entryCodeContent.map { it to emptyList() }
            )
            compiledFunctions.add(entryFunction)
        } else {
            entryFunction = mainFunction
        }

        return CompiledIntermediateProgram(symbolTable, compiledFunctions, entryFunction)
    }
}


fun compileProgram(filename: Filename, symbolTable: MutableSymbolTable): CompiledIntermediateProgram {

    val fileProvider = object : FileProvider {
        override fun getReader(f: String): Reader {
            require(filename == f)
            return File(f).inputStream().reader()
        }
    }

    return ProgramCompiler(fileProvider, filename, symbolTable).compile()
}


fun compileProgramFromSingleBody(body: String, symbolTable: MutableSymbolTable): CompiledIntermediateProgram {
    //Used in testing

    val tokens =
        parseFile(StringReader(body), "dummyfile") + listOf(Token(TokenType.EndBlock, "", SourceInfo.notApplicable))
    val nodes = AstParser(tokens).parseStatementsUntilEndblock()

    val definition = mainDefinitionInFile("dummyfile")

    val functionContents = compileFunctionBody(
        AstNode.fromBody(nodes),
        definition,
        symbolTable,
        VariableType.Local,
        emptyList(),
    )
    return CompiledIntermediateProgram(
        symbolTable, functionContents, functionContents.find { it.definition == definition }!!,
    )
}