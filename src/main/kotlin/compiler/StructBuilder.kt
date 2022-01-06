package se.wingez.compiler

import se.wingez.ast.ParserError
import se.wingez.ast.PrimitiveMemberDeclaration
import se.wingez.ast.StructNode
import se.wingez.byte

class StructBuilder(
    private val typeProvider: TypeProvider,
) {
    private var currentSize = byte(0)
    private val fields = mutableMapOf<String, StructDataField>()

    fun addMember(member: PrimitiveMemberDeclaration): StructBuilder {
        val typeTemplate: DataType = if (member.memberData.type.isEmpty())
            DEFAULT_TYPE
        else
            typeProvider.getType(member.memberData.type)

        val fieldType = typeTemplate.instantiate(member.memberData.explicitNew)
        return addMember(member.memberData.name, fieldType)
    }

    fun addMember(name: String, type: DataType): StructBuilder {

        if (name in fields) {
            throw CompileError("Duplicate field: $name")
        }

        fields[name] = StructDataField(name, currentSize, type)
        currentSize = byte(currentSize + type.size)
        return this
    }

    fun hasField(name: String): Boolean {
        return name in fields
    }

    fun getStruct(name: String): StructType {
        return StructType(name, fields)
    }

    fun getFields(): Map<String, StructDataField> {
        return fields.toMap()
    }

    fun getCurrentSize(): Int {
        return currentSize.toInt()
    }
}


fun buildStruct(node: StructNode, typeProvider: TypeProvider): StructType {
    val builder = StructBuilder(typeProvider)
    for (member in node.childNodes) {
        if (member !is PrimitiveMemberDeclaration)
            throw ParserError("Only primitive declarations allowed for structs. Not $member")
        builder.addMember(member)
    }

    return builder.getStruct(node.name)
}