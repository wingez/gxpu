package compiler.frontend

import compiler.BuiltInCollection
import java.io.File
import java.io.Reader

interface FileProvider{
    fun getReader(filename: String): Reader
}

class ProgramCompiler(
    private val fileProvider: FileProvider,
    private val mainFile:String,
    private val builtInCollection: BuiltInCollection,
) {


    fun compile():CompiledFile{

        return compileFile(mainFile,fileProvider.getReader(mainFile),builtInCollection)


    }




}


fun compileProgram(filename:String,builtInCollection: BuiltInCollection):CompiledFile{

    val fileProvider = object:FileProvider{
        override fun getReader(f: String): Reader {
            require(filename==f)
            return File(f).inputStream().reader()
        }
    }

    return ProgramCompiler(fileProvider,filename,builtInCollection).compile()
}