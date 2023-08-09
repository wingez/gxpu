package compiler.frontend

import compiler.BuiltInCollection
import java.io.File
import java.io.Reader

interface FileProvider {
    fun getReader(filename: String): Reader
}

private const val builtins = "Builtins"


class ProgramCompiler(
    private val fileProvider: FileProvider,
    private val mainFile: String,
    private val builtInCollection: BuiltInCollection,
) {

    fun compile(): CompiledFile {

        return compileFile(mainFile, fileProvider.getReader(mainFile), this)
    }

    fun import(file:String):Pair<List<Datatype>, List<FunctionDefinition>>{

        if (file== builtins){
            return builtInCollection.types to builtInCollection.functions
        }


        TODO()
    }

    fun importBuiltins(): Pair<List<Datatype>, List<FunctionDefinition>> {
        return import(builtins)
    }


}


fun compileProgram(filename: String, builtInCollection: BuiltInCollection): CompiledFile {

    val fileProvider = object : FileProvider {
        override fun getReader(f: String): Reader {
            require(filename == f)
            return File(f).inputStream().reader()
        }
    }

    return ProgramCompiler(fileProvider, filename, builtInCollection).compile()
}