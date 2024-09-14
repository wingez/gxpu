package cli

import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerRunner
import compiler.builtInSymbolTable
import compiler.compileAndRunProgram
import compiler.frontend.compileProgram
import compiler.frontend.compileProgramFromSingleBody

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

        for ((index, instr) in func.instructions.withIndex()) {
            for ((label,labelindex) in func.labels.entries){
                if (index==labelindex){
                    println(label)
                }
            }

            println(instr.debugString())
        }


    }


}