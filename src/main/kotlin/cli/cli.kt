package cli

import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.backends.machineCode.MachineCodeRunner
import compiler.backends.machineCode.buildToAssembly
import compiler.backends.machineCode.writeToFileAndRun
import compiler.builtInSymbolTable
import compiler.frontend.FileProvider
import compiler.frontend.ProgramCompiler
import compiler.frontend.compileProgram
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.reader


fun main(args: Array<String>) {

    val help = """Syntax: <run|run-interpreter|assembly|dump|symbols> <filename>""""

    if (args.size != 2) {
        println(help)
        return
    }

    val cmd = args[0]
    val path = Path(args[1])

    if (!path.exists()) {
        println("$path does not exist")
        return
    }

    val fileProvider = FileProvider {
        if (it == path.name){
            path.reader()
        }        else{
            path.resolveSibling(it).reader()
        }
    }

    val symbolTable = builtInSymbolTable()
    val intermediate = ProgramCompiler(fileProvider, path.name, symbolTable).compile()


    when (cmd) {
        "run" -> {
            val runner = MachineCodeRunner()
            runner.buildAndRun(intermediate)
                .forEach { println(it) }
        }

        "run-interpreter" -> {
            val runner = WalkerRunner(WalkConfig.default)
            runner.buildAndRun(intermediate)
                .forEach { println(it) }
        }

        "assembly" -> {
            buildToAssembly(intermediate)
                .forEach { println(it) }
        }

        "dump" -> {
            for (func in intermediate.functions) {
                println(func.definition)

                for ((instr, labels) in func.instructions) {
                    for (label in labels) {
                        println(label)
                    }

                    println(instr.debugString())
                }
            }
        }

        "symbols" -> {
            TODO()
        }

        else -> {
            println(help)
        }
    }
}