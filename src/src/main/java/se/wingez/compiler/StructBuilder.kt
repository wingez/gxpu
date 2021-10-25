package se.wingez.compiler

import se.wingez.ast.StructNode
import se.wingez.byte


fun buildStruct(node: StructNode, typeProvider: TypeProvider): StructType {
    val fields = mutableMapOf<String, StructDataField>()
    var currentSize = 0

    for (member in node.members) {
        val fieldType: DataType = if (member.type.isEmpty())
            DEFAULT_TYPE
        else
            typeProvider.getType(member.type)

        //TODO check duplicate

        fields[member.name] = StructDataField(member.name, byte(currentSize), fieldType)
        currentSize += fields.size
    }

    return StructType(byte(currentSize), node.name, fields)
}