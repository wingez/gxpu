package ast

import ast.syntaxerror.ParserSyntaxError
import ast.syntaxerror.throwSyntaxError
import requireNotReached
import tokens.TokenType

enum class TypeDefinitionModifier {
    Array,
    Pointer,
}

interface TypeBase

data class StaticBase(
    val name: String
) : TypeBase

data class FunctionBase(
    val returnType: TypeDefinition,
    val parameters: List<TypeDefinition>
) : TypeBase

data class TypeDefinition(
    val base: TypeBase,
    private val modifiers: Collection<TypeDefinitionModifier>,
) {
    fun hasModifier(modifier: TypeDefinitionModifier): Boolean {
        return modifier in modifiers
    }

    companion object {
        fun normal(name: String, modifiers: Collection<TypeDefinitionModifier> = emptyList()): TypeDefinition {
            return TypeDefinition(
                StaticBase(name),
                modifiers,
            )
        }
    }
}

fun parseTypeDefinition(tokens: TokenIterator): TypeDefinition {
    val modifiers = mutableListOf<TypeDefinitionModifier>()

    if (tokens.peekIs(TokenType.Star, consumeMatch = true)) {
        modifiers.add(TypeDefinitionModifier.Pointer)
    }

    val base = when (tokens.peek().type) {
        TokenType.Identifier -> {
            // normal
            val typeName = tokens.consumeIdentifier()
            StaticBase(typeName)
        }

        TokenType.LeftParenthesis -> {
            // function
            tokens.consume()
            // TODO: parse parameters
            val parameters = mutableListOf<TypeDefinition>()

            if (!tokens.peekIs(TokenType.RightParenthesis, consumeMatch = true)) {
                while (true) {
                    parameters.add(parseTypeDefinition(tokens))
                    if (tokens.peekIs(TokenType.RightParenthesis, consumeMatch = true)) {
                        break
                    } else if (tokens.peekIs(TokenType.Comma, consumeMatch = true)) {
                        continue
                    } else {
                        requireNotReached()
                    }
                }
            }

            tokens.consumeType(TokenType.RightArrow)
            // TODO: this dont handle arrays.
            val resultType = parseTypeDefinition(tokens)
            FunctionBase(resultType, parameters)
        }

        else -> throwSyntaxError("Expected expected Comma or Right parenthesis", tokens.peek().sourceInfo)
    }

    if (tokens.peekIs(TokenType.LeftBracket)) {
        tokens.consumeType(TokenType.LeftBracket)
        tokens.consumeType(TokenType.RightBracket)
        modifiers.add(TypeDefinitionModifier.Array)
    }
    return TypeDefinition(base, modifiers)
}
