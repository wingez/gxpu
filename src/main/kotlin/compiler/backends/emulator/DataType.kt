package compiler.backends.emulator

import compiler.frontend.Datatype


interface DatatypeLayoutProvider {
    fun sizeOf(dataType: Datatype): Int
}

data class StructDataField(
    val name: String,
    val type: Datatype,
    val offset: Int,
    val size:Int,
)
