package compiler.frontend

import ast.TypeDefinition

enum class FieldAnnotation {
    None,
    LocalVariable,
    Parameter,
    Result,
}

data class CompositeDataTypeField(
    val name: String,
    val type: Datatype,
    val annotation: FieldAnnotation=FieldAnnotation.None,
)

class Datatype private constructor(
    val name: String,
    private val type: DatatypeClass,
    private val compositeMembersNullable: List<CompositeDataTypeField>?,
    private val subTypeNullable: Datatype?,
) {

    private enum class DatatypeClass {
        Void,
        Integer,
        Composite,
        Bool,
        Array,
        Pointer,
    }

    val isComposite = type == DatatypeClass.Composite

    val isPrimitive = type == DatatypeClass.Integer || type == DatatypeClass.Bool

    val isPointer = type == DatatypeClass.Pointer

    val isVoid = type == DatatypeClass.Void

    val isArray = type == DatatypeClass.Array
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Datatype

        if (type != other.type) return false
        if (name != other.name) return false
        if (isComposite) {
            if (compositeMembersNullable != other.compositeMembersNullable) {
                return false
            }
        }
        if (isArray) {
            if (subTypeNullable != other.subTypeNullable) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (compositeMembersNullable?.hashCode() ?: 0)
        result = 31 * result + (subTypeNullable?.hashCode() ?: 0)
        return result
    }

    fun containsField(name: String): Boolean {
        require(isComposite)
        return compositeFields.any { it.name == name }
    }

    fun fieldType(name: String): Datatype {
        require(containsField(name))
        return compositeFields.find { it.name == name }!!.type
    }

    val compositeFields: List<CompositeDataTypeField>
        get() {
            require(isComposite)
            return compositeMembersNullable!!
        }
    val arrayType: Datatype
        get() {
            require(isArray)
            return subTypeNullable!!
        }

    val pointerType: Datatype
        get() {
            require(isPointer)
            return subTypeNullable!!
        }

    override fun toString(): String {
        return name
    }

    companion object {
        val Integer = Datatype("integer", DatatypeClass.Integer, null, null)
        val Void = Datatype("void", DatatypeClass.Void, null, null)
        val Boolean = Datatype("bool", DatatypeClass.Bool, null, null)

        val Str = Pointer(Array(Integer))

        fun Composite(name: String, members: List<CompositeDataTypeField>): Datatype {
            requireNoDuplicateFields(members)
            return Datatype(name, DatatypeClass.Composite, members, null)
        }

        fun Array(arrayType: Datatype): Datatype {
            require(!arrayType.isArray)
            val name = "array[$arrayType]"
            return Datatype(name, DatatypeClass.Array, null, arrayType)
        }

        fun ArrayPointer(arrayType: Datatype): Datatype {
            return Pointer(Array(arrayType))
        }

        fun Pointer(toType: Datatype): Datatype {
            require(!toType.isPointer)
            val name = "pointer[$toType]"
            return Datatype(name, DatatypeClass.Pointer, null, toType)
        }
    }
}

private fun requireNoDuplicateFields(fields: List<CompositeDataTypeField>) {
    for (f in fields.map { it.name }) {
        if (fields.count { it.name == f } > 1) {
            require(false) { "Duplicate field: $f" }
        }
    }
}

interface TypeProvider {
    fun getType(name: String): Datatype?
    fun getType(typeDefinition: TypeDefinition): Datatype? {
        val typeName = typeDefinition.typeName
        var type = getType(typeName) ?: return null
        if (typeDefinition.isArray) {
            type = Datatype.Array(type)
        }
        if (typeDefinition.isPointer) {
            type = Datatype.Pointer(type)
        }
        return type
    }

    fun requireType(name: String): Datatype {
        return getType(name)
            ?: throw FrontendCompilerError("Could not find type: $name")
    }

    fun requireType(typeDefinition: TypeDefinition): Datatype {
        return getType(typeDefinition)
            ?: throw FrontendCompilerError("Could not find type: ${typeDefinition.typeName}")
    }
}