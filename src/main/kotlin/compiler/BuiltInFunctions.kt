package se.wingez.compiler

import se.wingez.emulator.DefaultEmulator

interface BuiltIn {

    val signature: FunctionSignature
    val name: String
    fun compile(generator: CodeGenerator)
}


class Print : BuiltIn {
    override val name = "print"
    override val signature: FunctionSignature
        //TODO: This can be extracted to some nice method
        get() = FunctionSignature(
            name,
            StructBuilder()
                .addMember("frame", stackFrameType)
                .addMember("value", byteType)
                .addMember("result", voidType)
                .getFields(),
            listOf("value"),
            voidType,
        )

    override fun compile(generator: CodeGenerator) {
        generator.generate(
            DefaultEmulator.lda_at_fp_offset.build(
                mapOf("offset" to signature.getField("value").offset)
            )
        )
        generator.generate(DefaultEmulator.print.build())
        generator.generate(DefaultEmulator.ret.build())
    }

}


fun findBuiltin(name: String, signature: List<DataType>): BuiltIn? {
    val available = listOf(
        Print()
    )

    for (builtIn in available) {
        if (builtIn.name == name && builtIn.signature.parameterSignature == signature) {
            return builtIn
        }
    }
    return null
}







