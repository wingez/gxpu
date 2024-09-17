package compiler.backends.machineCode

import compiler.BackendCompiler
import compiler.frontend.CompiledIntermediateProgram
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*


fun writeToFileAndRun(lines: List<String>): String {


    val tempDir = createTempDirectory()

    val includePath = Path("", "src", "test", "stdlib", "assembly")
    for (include in includePath.listDirectoryEntries()) {
        if (!include.isRegularFile()) {
            continue
        }

        val newPath = tempDir.resolve(include.fileName)


        include.copyTo(newPath)
    }

    val mainFile = tempDir.resolve("main.s")

    mainFile.writeLines(lines)

    println()

    println(runCommand(listOf("gcc") + tempDir.listDirectoryEntries().map { it.toString() }, tempDir))


    val result = runCommand(listOf(tempDir.resolve("a.out").toString()), tempDir)

    return result
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


class MachineCodeRunner(
) : BackendCompiler {
    override fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String> {

        val lines = buildToAssembly(intermediateProgram)

        val result = writeToFileAndRun(lines)

        val resultLines = result.split("\n")
            .filter { it.isNotBlank() }


        return resultLines

    }
}