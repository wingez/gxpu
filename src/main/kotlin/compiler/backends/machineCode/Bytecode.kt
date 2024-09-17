package compiler.backends.machineCode

import compiler.BackendCompiler
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerState
import compiler.frontend.CompiledIntermediateProgram
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*


enum class Registers {
    P0,
    P1,
    P2,
    P3,


    EA,
    EB,
    EC,
    ED,


}


class MachineCodeRunner(
) : BackendCompiler {
    override fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String> {


        val function = intermediateProgram.mainFunction


        val tempDir = createTempDirectory()

        val includePath = Path("", "src", "test", "stdlib", "assembly")
        for (include in includePath.listDirectoryEntries()) {
            if (!include.isRegularFile()) {
                continue
            }

            val newPath = tempDir.resolve(include.fileName)


            include.copyTo(newPath)
        }

        println(tempDir.listDirectoryEntries())

        runCommand(listOf("gcc") + tempDir.listDirectoryEntries().map { it.toString() }, tempDir)

        println(tempDir.listDirectoryEntries())


        val result = runCommand(listOf(tempDir.resolve("a.out").toString()), tempDir)

        println("HELLO")
        println(result)



        TODO()
    }
}

private fun runCommand(command: List<String>, location: Path): String {
    return runCatching {
        ProcessBuilder(command)
            .directory(location.toFile())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
            .also { it.waitFor(1, TimeUnit.SECONDS) }
            .inputStream.bufferedReader().readText()
    }.onFailure { it.printStackTrace() }.getOrThrow()

}






