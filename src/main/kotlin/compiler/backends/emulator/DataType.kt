package se.wingez.compiler

import se.wingez.ast.TypeDefinition

interface DataType {
    val size: Int
    val name: String
}

interface FieldContainer {
    fun hasField(field: String): Boolean
    fun getField(field: String): StructDataField
    fun getDescription(): List<String>
}


interface TypeProvider {
    fun getType(name: String): DataType
    fun getType(typeDefinition: TypeDefinition): DataType {
        assert(!typeDefinition.isArray)
        //assert(!typeDefinition.explicitNew)
        return getType(typeDefinition.typeName)
    }

}

data class PrimitiveDatatype(
    override val size: Int,
    override val name: String
) : DataType

val byteType = PrimitiveDatatype(1, "byte")
val voidType = PrimitiveDatatype(0, "void")
val stackFrameType = PrimitiveDatatype(SP_STACK_SIZE + PC_STACK_SIZE, "stackFrame")

val DEFAULT_TYPE = byteType


data class StructDataField(
    val name: String,
    val offset: Int,
    val type: DataType,
)

private fun describeFields(fields: Map<String, StructDataField>): List<String> {

    val result = mutableListOf<String>()
    val items = fields.entries.toList()

    items.sortedBy { it.value.offset }.forEach { field ->
        val offset = field.value.offset.toString().padStart(3)
        result.add("$offset: ${field.key}: ${field.value.type.name}")
    }

    return result
}


open class StructType(
    override val name: String,
    val fields: Map<String, StructDataField>,
) : DataType, FieldContainer {

    override fun hasField(field: String): Boolean {
        return field in fields
    }

    override fun getField(field: String): StructDataField {
        return fields.getValue(field)
    }

    override val size: Int
        get() = fields.values.sumOf { it.type.size }

    override fun getDescription(): List<String> {
        return describeFields(fields)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is StructType) return false
        return size == other.size && name == other.name && fields == other.fields
    }


    override fun toString(): String {
        return "name=$name, size=$size, fields=$fields"
    }

    override fun hashCode(): Int {
        var result = size.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + fields.hashCode()
        return result
    }
}