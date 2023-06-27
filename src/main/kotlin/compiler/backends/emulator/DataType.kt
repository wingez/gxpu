package compiler.backends.emulator

import compiler.frontend.Datatype
import requireNotReached

fun sizeOf(dataType: Datatype): Int{

    if (dataType.isPrimitive){
        return 1
    }
    if (dataType.isPointer){
        return 1
    }
    if (dataType.isArray){
        throw EmulatorBackendCompilerError(dataType.toString())
    }
    if (dataType.isComposite){
        return dataType.compositeFields.sumOf { sizeOf(it.type) }
    }
    requireNotReached()
}

data class StructDataField(
    val name: String,
    val type: Datatype,
    val offset: Int,
    val size:Int,
)
