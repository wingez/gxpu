package compiler.backends.emulator

import compiler.frontend.Datatype
import requireNotReached

fun sizeOf(dataType: Datatype): Int {

    if (dataType.isPrimitive) {
        return 1
    }
    if (dataType.isPointer) {
        return 1
    }
    if (dataType.isArray) {
        throw EmulatorBackendCompilerError(dataType.toString())
    }
    if (dataType.isComposite) {
        return dataType.compositeFields.sumOf { sizeOf(it.type) }
    }
    requireNotReached()
}


interface LayedOutDatatype {
    val size:Int
    fun getField(fieldName: String): StructDataField
}


class LayedOutStruct(
    private val dataType: Datatype
) : LayedOutDatatype {
    init {
        require(dataType.isComposite)
    }

    override val size: Int
        get() = dataType.compositeFields.sumOf { sizeOf(it.type) }
    override fun getField(fieldName: String): StructDataField {
        require(dataType.containsField(fieldName))
        var offset = 0
        for (field in dataType.compositeFields) {
            if (field.name == fieldName) {
                return StructDataField(field.name, field.type, offset)
            }
            offset += sizeOf(field.type)
        }
        requireNotReached()
    }
}


data class StructDataField(
    val name: String,
    val type: Datatype,
    val offset: Int,
)
