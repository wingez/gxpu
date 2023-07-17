package compiler.backends.emulator

import compiler.frontend.*
import requireNotReached

fun sizeOf(dataType: Datatype): Int {

    return when(dataType){
        Primitives.Nothing -> requireNotReached()
        is PrimitiveDataType, is PointerDatatype -> 1
        is CompositeDatatype -> dataType.compositeFields.sumOf { sizeOf(it.type) }
        else -> throw EmulatorBackendCompilerError("cannot do sizeof $dataType")
    }
}


interface LayedOutDatatype {
    val size:Int
    fun getField(fieldName: String): StructDataField
}


class LayedOutStruct(
    private val dataType: CompositeDatatype
) : LayedOutDatatype {
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
