package cli

import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.backends.machineCode.buildToAssembly
import compiler.backends.machineCode.writeToFileAndRun
import compiler.builtInSymbolTable
import compiler.compileAndRunProgram
import compiler.frontend.compileProgram
import compiler.frontend.compileProgramFromSingleBody
import kotlin.io.path.Path

fun main(args: Array<String>) {

    val help = """Syntax: <run|run-interpreter|debug> <filename>""""

//    if (args.size != 2) {
//        println(help)
//        return
//    }


    val symbolTable = builtInSymbolTable()
    val result = compileProgram("test.g", symbolTable)


    for (func in result.functions) {
        println(func.definition)

        for ((instr, labels) in func.instructions) {
            for (label in labels) {
                println(label)
            }

            println(instr.debugString())
        }
    }


    println("ASSEMBLY")

    val lines = buildToAssembly(result)

    lines.forEach { println(it) }

    val output = writeToFileAndRun(lines)
    println("OUTPUT")
    println(output)


}