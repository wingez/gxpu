package compiler.frontend

import compiler.BuiltInSignatures
import org.junit.jupiter.api.assertThrows
import requireNotReached
import java.io.Reader
import java.io.StringReader
import kotlin.test.Test

class Imports {

    @Test
    fun testImport() {

        val mainFile = """
            
            import other
            
            def main():
               call_me()
            
            
        """.trimIndent()

        val otherFile = """
            
            def call_me():
              print(5)
            
            
        """.trimIndent()

        val missingImport = """
            
            import nonexisting
            
        """.trimIndent()


        val fileProvider = object : FileProvider {
            override fun getReader(filename: String): Reader? {
                val file = when (filename) {
                    "main" -> mainFile
                    "other" -> otherFile
                    "missing" -> missingImport
                    else -> return null
                }
                return StringReader(file)
            }
        }


        assertThrows<FrontendCompilerError>("hello world") { ProgramCompiler(fileProvider, "missing").compile() }



    }


}