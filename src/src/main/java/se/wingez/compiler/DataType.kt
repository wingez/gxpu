package se.wingez.compiler

import se.wingez.byte


interface DataType {
    val size: UByte
    val name: String
}


interface TypeProvider {
    fun getType(name: String): DataType
}

data class PrimitiveDatatype(
    override val size: UByte,
    override val name: String
) : DataType

val byteType = PrimitiveDatatype(1u, "byte")
val voidType = PrimitiveDatatype(0u, "void")
val stackFrameType = PrimitiveDatatype(byte(SP_STACK_SIZE + PC_STACK_SIZE), "stackFrame")
val compareType = PrimitiveDatatype(0u, "compare")


val DEFAULT_TYPE = byteType

data class StructDataField(
    val name: String,
    val offset: UByte,
    val type: DataType,
)

open class StructType(
    override val size: UByte,
    override val name: String,
    val fields: Map<String, StructDataField>,
) : DataType {

    fun hasField(field: String): Boolean {
        return field in fields
    }

    fun getField(field: String): StructDataField {
        return fields.getValue(field)
    }

    fun getDescription(): List<String> {
        val result = mutableListOf<String>()
        val items = fields.entries.toList()

        items.sortedBy { it.value.offset }.forEach { field ->
            val offset = field.value.offset.toString().padStart(3)
            result.add("$offset: ${field.key}: ${field.value.type.name}")
        }

        return result
    }
}