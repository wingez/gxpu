package se.wingez

open class Token
open class ExpressionSeparator : Token()


data class TokenIdentifier(val target: String) : Token()
data class TokenNumericConstant(val value: Int) : Token()
