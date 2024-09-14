package compiler.backends.bytecode

import compiler.BackendCompiler
import compiler.backends.astwalker.WalkConfig
import compiler.backends.astwalker.WalkerState
import compiler.frontend.CompiledIntermediateProgram


enum class Registers{
    P0,
    P1,
    P2,
    P3,



    EA,
    EB,
    EC,
    ED,


}







class BytecodeRunner(
    private val config: WalkConfig,
) : BackendCompiler {
    override fun buildAndRun(intermediateProgram: CompiledIntermediateProgram): List<String> {
        return WalkerState(intermediateProgram, config).walk().result
    }
}








