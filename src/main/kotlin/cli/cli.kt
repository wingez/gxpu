package cli

import compiler.BuiltInSignatures
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.backends.emulator.BuiltInFunctions
import compiler.backends.emulator.EmulatorRunner
import compiler.backends.emulator.emulator.InteractiveDebugger
import compiler.compileAndRunProgram

fun main(args: Array<String>) {

    val help = """Syntax: <run|run-interpreter|debug> <filename>""""

    if (args.size != 2) {
        println(help)
        return
    }

    val filename = args[1]

    val compiler = when (args[0]) {

        "run" -> {
            EmulatorRunner(BuiltInFunctions())
        }
        "run-interpreter" -> {
            WalkerRunner(WalkConfig.default)
        }
        "debug" -> InteractiveDebugger()

        else -> {
            println(help)
            return
        }
    }

    val result = compileAndRunProgram(filename, compiler, BuiltInSignatures())

    for (line in result){
        println(line)
    }
}