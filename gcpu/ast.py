from __future__ import annotations
from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import List, Optional, Type, TypeVar, Union

from gcpu import token

T = TypeVar('T', bound=token.Token, covariant=True)


@dataclass
class AstNode: pass


class ValueProviderNode(AstNode):
    pass


@dataclass
class AssignNode(AstNode):
    def __init__(self, target: str, value: ValueProviderNode):
        self.target = target
        self.value_node = value


@dataclass
class PrintNode(AstNode):
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
class CallNode(AstNode):
    target_name: str


@dataclass
class OperationNode(ValueProviderNode):
    left: ValueProviderNode
    right: ValueProviderNode


expressions_types = Union[AssignNode, PrintNode, CallNode]


@dataclass
class FunctionNode(ValueProviderNode):
    name: str
    body: List[expressions_types] = field(default_factory=list)


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
            if isinstance(self.peek(), token.TokenEOL):
                self.consume()
            else:

                node = self.try_parse_function()
                if node is not None:
                    result.append(node)
                    continue

                raise ParserError('Could not parse')

        return result

    def parse_expressions_until_endblock(self) -> List[expressions_types]:
        expressions = []

        while not isinstance(self.peek(), token.TokenEndBlock):
            if isinstance(self.peek(), token.TokenEOL):
                self.consume()
                continue

            new_expression = self.parse_expression()
            expressions.append(new_expression)

        return expressions

    def parse_expression(self) -> expressions_types:
        tok: Optional[expressions_types]

        tok = self.try_parse_assignment()
        if tok is not None:
            return tok
        tok = self.try_parse_print()
        if tok is not None:
            return tok
        tok = self.try_parse_function_call()
        if tok is not None:
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

    @contextmanager
    def _restore_on_error(self):
        line = self.savepoint()
        try:
            yield
        except ParserError as e:
            self.restore(line)
            raise e

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

    def try_parse_function(self) -> Optional[FunctionNode]:
        try:
            with self._restore_on_error():
                return self.parse_function_statement()
        except ParserError:
            return None

    def parse_function_statement(self) -> FunctionNode:
        self.consume_type(token.TokenKeywordDef)
        name_node = self.consume_type(token.TokenIdentifier)
        self.consume_type(token.TokenLeftParenthesis)
        self.consume_type(token.TokenRightParenthesis)
        self.consume_type(token.TokenColon)
        self.consume_type(token.TokenEOL)
        self.consume_type(token.TokenBeginBlock)

        expressions = self.parse_expressions_until_endblock()

        self.consume_type(token.TokenEndBlock)

        return FunctionNode(name_node.target, expressions)

    def try_parse_assignment(self) -> Optional[AssignNode]:

        try:
            with self._restore_on_error():
                target_token: token.TokenIdentifier = self.consume_type(token.TokenIdentifier)
                self.consume_type(token.TokenEquals)
                value_node = self.parse_value_provider()
                self.consume_type(token.TokenEOL)
                return AssignNode(target_token.target, value_node)
        except ParserError:
            return None

    def try_parse_print(self) -> Optional[PrintNode]:

        try:
            with self._restore_on_error():
                self.consume_type(token.TokenKeywordPrint)
                self.consume_type(token.TokenLeftParenthesis)
                target = self.parse_value_provider()
                self.consume_type(token.TokenRightParenthesis)
                self.consume_type(token.TokenEOL)

                return PrintNode(target)
        except ParserError:
            return None

    def try_parse_function_call(self) -> Optional[CallNode]:
        try:
            with self._restore_on_error():
                target_node = self.consume_type(token.TokenIdentifier)
                self.consume_type(token.TokenLeftParenthesis)
                self.consume_type(token.TokenRightParenthesis)
                self.consume_type(token.TokenEOL)

                return CallNode(target_node.target)
        except ParserError:
            return None


def parse(tokens: List[token.Token]) -> List[FunctionNode]:
    p = Parser(tokens)
    return p.parse()


def parse_expressions(tokens: List[token.Token]) -> List[expressions_types]:
    p = Parser(tokens + [token.TokenEndBlock()])
    return p.parse_expressions_until_endblock()
