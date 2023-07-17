package compiler.frontend

import ast.TypeDefinition

interface Datatype {
    val name: String
}

data class PrimitiveDataType(
    override val name: String
) : Datatype {
    override fun toString(): String = name
}

object Primitives {
    val Nothing = PrimitiveDataType("void")
    val Integer = PrimitiveDataType("int")
    val Boolean = PrimitiveDataType("bool")
    val Str = Integer.arrayPointerOf()
}

data class CompositeDataTypeField(
    val name: String,
    val type: Datatype,
)

data class PointerDatatype(
    val pointerType: Datatype
) : Datatype {
    override val name: String
        get() = "pointer[${pointerType.name}]"

    override fun toString(): String = name
}

data class ArrayDatatype(
    val arrayType: Datatype
) : Datatype {
    override val name: String
        get() = "array[${arrayType.name}]"

    override fun toString(): String = name
}

data class CompositeDatatype(
    override val name: String,
    val compositeFields: List<CompositeDataTypeField>
) : Datatype {
    init {
        requireNoDuplicateFields()
    }

    fun containsField(name: String): Boolean {
        return compositeFields.any { it.name == name }
    }

    fun getField(name: String): CompositeDataTypeField {
        require(containsField(name))
        return compositeFields.find { it.name == name }!!
    }

    fun fieldType(name: String): Datatype {
        return getField(name).type
    }


    private fun requireNoDuplicateFields() {
        for (f in compositeFields.map { it.name }) {
            require(compositeFields.count { it.name == f } == 1) { "Duplicate field: $f" }
        }
    }
}

fun Datatype.arrayOf(): Datatype {
    return ArrayDatatype(this)
}

fun Datatype.arrayPointerOf(): Datatype {
    return this.arrayOf().pointerOf()
}

fun Datatype.pointerOf(): Datatype {
    return PointerDatatype(this)
}


interface TypeProvider {
    fun getType(name: String): Datatype?
    fun getType(typeDefinition: TypeDefinition): Datatype? {
        val typeName = typeDefinition.typeName
        var type = getType(typeName) ?: return null
        if (typeDefinition.isArray) {
            type = type.arrayOf()
        }
        if (typeDefinition.isPointer) {
            type = type.pointerOf()
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