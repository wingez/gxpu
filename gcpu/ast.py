from __future__ import annotations
from dataclasses import dataclass, field
from typing import List, Optional, Type, TypeVar, Callable

from gcpu import token

T = TypeVar('T', bound=token.Token, covariant=True)


@dataclass
class AstNode: pass


class ValueProviderNode(AstNode):
    pass


class StatementNode(AstNode):
    pass


@dataclass
class AssignNode(StatementNode):
    def __init__(self, target: str, value: ValueProviderNode):
        self.target = target
        self.value_node = value


@dataclass
class PrintNode(StatementNode):
    def __init__(self, target: ValueProviderNode):
        self.target = target


@dataclass
class IdentifierNode(ValueProviderNode):
    def __init__(self, identifier: str):
        self.identifier = identifier


@dataclass
class ConstantNode(ValueProviderNode):
    def __init__(self, value: int):
        self.value = value


@dataclass
class CallNode(StatementNode):
    target_name: str
    parameters: List[ValueProviderNode] = field(default_factory=list)


@dataclass
class OperationNode(ValueProviderNode):
    left: ValueProviderNode
    right: ValueProviderNode


@dataclass
class FunctionNode(ValueProviderNode):
    name: str
    arguments: List[str]
    body: List[StatementNode]


@dataclass
class WhileNode(StatementNode):
    condition: ValueProviderNode
    body: List[StatementNode]


class AdditionNode(OperationNode): pass


class SubtractionNode(OperationNode): pass


class ParserError(Exception):
    pass


class NoMoreTokens(ParserError): pass


class Parser:
    def __init__(self, tokens: List[token.Token]):
        self._token = tokens
        self._index = 0

    def peek(self) -> token.Token:
        if self._index >= len(self._token):
            raise NoMoreTokens("End of token-list reached")

        return self._token[self._index]

    def peek_is(self, token_type: Type[token.Token]) -> bool:
        return isinstance(self.peek(), token_type)

    def savepoint(self):
        return self._index

    def restore(self, checkpoint):
        self._index = checkpoint

    def has_more_to_parse(self) -> bool:
        return self._index < len(self._token)

    def parse(self) -> List[FunctionNode]:
        result: List[FunctionNode] = []

        while self.has_more_to_parse():

            # filter empty line
            if self.peek_is(token.TokenEOL):
                self.consume()
            else:
                try:
                    result.append(self.parse_function_definition())
                    continue
                except ParserError:
                    pass

                raise ParserError('Could not parse')

        return result

    def parse_statements_until_endblock(self) -> List[StatementNode]:
        expressions = []

        while not self.peek_is(token.TokenEndBlock):
            if self.peek_is(token.TokenEOL):
                self.consume()
                continue

            new_statement = self.parse_statement()
            expressions.append(new_statement)

        self.consume_type(token.TokenEndBlock)

        return expressions

    def parse_statement(self) -> StatementNode:

        def try_parse(func: Callable[[], StatementNode]) -> Optional[StatementNode]:
            line = self.savepoint()
            try:
                return func()
            except ParserError:
                self.restore(line)
                return None

        tok: Optional[StatementNode]

        if tok := try_parse(self.parse_assignment):
            return tok
        if tok := try_parse(self.parse_print):
            return tok
        if tok := try_parse(self.parse_function_call):
            return tok
        if tok := try_parse(self.parse_while_statement):
            return tok

        raise ParserError(f'Dont know how to parse: {self.peek()}')

    def consume(self) -> token.Token:
        current = self._token[self._index]

        self._index += 1
        return current

    def consume_type(self, token_type: Type[T]) -> T:
        current = self.consume()
        if not isinstance(current, token_type):
            raise ParserError()

        return current

    def parse_value_provider(self) -> ValueProviderNode:
        tok = self.consume()
        first_result: ValueProviderNode
        if isinstance(tok, token.TokenNumericConstant):
            first_result = ConstantNode(tok.value)
        elif isinstance(tok, token.TokenIdentifier):
            first_result = IdentifierNode(tok.target)
        else:
            raise ParserError(f"Cannot parse to value provider: {tok}")

        next_token = self.peek()

        if isinstance(next_token, token.ExpressionSeparator):
            return first_result

        if isinstance(next_token, token.TokenSingleOperation):
            self.consume()

            second_result = self.parse_value_provider()
            if not isinstance(second_result, (IdentifierNode, ConstantNode)):
                raise ParserError('Operation too complex')

            if isinstance(next_token, token.TokenPlusSign):
                return AdditionNode(left=first_result, right=second_result)
            elif isinstance(next_token, token.TokenMinusSign):
                return SubtractionNode(left=first_result, right=second_result)

            else:
                raise ParserError(f'Dont know how to parse {next_token}')

        raise ParserError(f'{next_token} was not expected')

    def parse_function_definition(self) -> FunctionNode:
        self.consume_type(token.TokenKeywordDef)
        name_node = self.consume_type(token.TokenIdentifier)
        self.consume_type(token.TokenLeftParenthesis)

        parameter_names = []

        while not self.peek_is(token.TokenRightParenthesis):

            target_node = self.consume_type(token.TokenIdentifier)
            parameter_names.append(target_node.target)

            if self.peek_is(token.TokenComma):
                self.consume()

        self.consume()

        self.consume_type(token.TokenColon)
        self.consume_type(token.TokenEOL)
        self.consume_type(token.TokenBeginBlock)

        statements = self.parse_statements_until_endblock()

        return FunctionNode(name_node.target, arguments=parameter_names, body=statements)

    def parse_while_statement(self) -> WhileNode:
        self.consume_type(token.TokenKeywordWhile)

        condition = self.parse_value_provider()

        self.consume_type(token.TokenColon)
        self.consume_type(token.TokenEOL)
        self.consume_type(token.TokenBeginBlock)

        statements = self.parse_statements_until_endblock()

        return WhileNode(condition, statements)

    def parse_assignment(self) -> AssignNode:
        target_token: token.TokenIdentifier = self.consume_type(token.TokenIdentifier)
        self.consume_type(token.TokenEquals)
        value_node = self.parse_value_provider()
        self.consume_type(token.TokenEOL)
        return AssignNode(target_token.target, value_node)

    def parse_print(self) -> PrintNode:
        self.consume_type(token.TokenKeywordPrint)
        self.consume_type(token.TokenLeftParenthesis)
        target = self.parse_value_provider()
        self.consume_type(token.TokenRightParenthesis)
        self.consume_type(token.TokenEOL)

        return PrintNode(target)

    def parse_function_call(self) -> CallNode:
        target_node = self.consume_type(token.TokenIdentifier)
        self.consume_type(token.TokenLeftParenthesis)

        parameters = []

        while not self.peek_is(token.TokenRightParenthesis):

            param_value = self.parse_value_provider()
            parameters.append(param_value)

            if self.peek_is(token.TokenComma):
                self.consume()

        self.consume()

        self.consume_type(token.TokenEOL)

        return CallNode(target_node.target, parameters=parameters)


def parse(tokens: List[token.Token]) -> List[FunctionNode]:
    p = Parser(tokens)
    return p.parse()


def parse_expressions(tokens: List[token.Token]) -> List[StatementNode]:
    p = Parser(tokens + [token.TokenEndBlock()])
    return p.parse_statements_until_endblock()
