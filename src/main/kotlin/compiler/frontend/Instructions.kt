package compiler.frontend


interface ValueExpr {
    val type: Datatype
    fun debugString(): String
}

data class IntConstant(val value: Int) : ValueExpr {
    override val type = Primitives.Integer
    override fun debugString(): String {
        return value.toString()
    }
}

data class LocalValueRef(val name: String, override val type: Datatype) : ValueExpr {
    override fun debugString(): String {
        return "%$name"
    }
}

data class GlobalValueRef(val name: String, private val typ: Datatype) : ValueExpr {
    override val type = typ.pointerOf()
    override fun debugString(): String {
        return "@$name*"
    }
}

class Call(val func: FunctionDefinition, val params: List<ValueExpr>) : ValueExpr {
    override val type = func.returnType

    override fun debugString(): String {
        return "CALL {${func.name}} " + params.joinToString(" ") { it.debugString() }
    }
}

interface Instruction {
    val type: Datatype?

    fun debugString(): String
}

class Return(val value: ValueExpr) : Instruction {
    override val type = null

    override fun debugString(): String {
        return "RETURN"
    }
}

class ReturnNothing : Instruction {
    override val type = null
    override fun debugString(): String {
        return "RETURN NULL"
    }
}

class TempValue(val name: String, val value: ValueExpr) : Instruction {
    override val type: Datatype
        get() = value.type

    override fun debugString(): String {
        return "%$name = ${value.debugString()}"
    }
}


enum class ComparisonType {
    Equals,
    NotEquals
}

class Comparison(val comparison: ComparisonType, val left: ValueExpr, val right: ValueExpr) : Instruction {

    override val type = Primitives.Boolean
    override fun debugString(): String {
        return "CMP ${left.debugString()} ${right.debugString()}"
    }
}


class Store(val value: ValueExpr, val destination: ValueExpr) : Instruction {
    override val type = null
    override fun debugString(): String {
        TODO("Not yet implemented")
    }
}

class GetElementPtr() {

}


class Jump(
    val label: Label
) : Instruction {
    override val type = null
    override fun debugString(): String {
        return "JMP $label"
    }
}

class JumpOnTrue(
    val condition: ValueExpr, val label: Label
) : Instruction {
    override val type = null
    override fun debugString(): String {
        return "JMP TRUE ${condition.debugString()} $label"
    }
}

class JumpOnFalse(
    val condition: ValueExpr, val label: Label
) : Instruction {
    override val type = null
    override fun debugString(): String {
        return "JMP FALSE ${condition.debugString()} $label"
    }
}


data class Label(
    val identifier: String
){
    override fun toString(): String {
        return ".$identifier"
    }
}
