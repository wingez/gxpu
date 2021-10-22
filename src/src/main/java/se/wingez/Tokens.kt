package se.wingez

open class Token
open class ExpressionSeparator : Token()
open class TokenSingleOperation : Token()


data class TokenIdentifier(val target: String) : Token()
data class TokenNumericConstant(val value: Int) : Token()
