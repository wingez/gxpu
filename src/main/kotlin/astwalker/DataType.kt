package se.wingez.astwalker

class Datatype {

    constructor(){
        isPrimitive=true
        isArray=false
        isComposite=false
    }

    val isPrimitive:Boolean
    val isArray:Boolean
    val isComposite:Boolean
}

val DatatypeInteger = Datatype()
val DatatypeVoid = Datatype()

class Variable(
    val datatype: Datatype,
    val value: Int
) {





}