from __future__ import annotations
from contextlib import contextmanager
from dataclasses import dataclass
from typing import List, Optional, Type, TypeVar

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
class AdditionNode(ValueProviderNode):
    def __init__(self, left: AstNode, right: AstNode):
        self.left = left
        self.right = right


class ParserError(Exception):
    pass


class Parser:
    def __init__(self, tokens: List[token.Token]):
        self._token = tokens
        self._index = 0

    def peek(self) -> token.Token:
        return self._token[self._index]

    def savepoint(self):
        return self._index

    def restore(self, checkpoint):
        self._index = checkpoint

    def has_more_to_parse(self) -> bool:
        return self._index < len(self._token)

    def parse(self) -> List[AstNode]:
        result: List[AstNode] = []

        while self.has_more_to_parse():

            # filter empty line
            if isinstance(self.peek(), token.TokenEOL):
                self.consume()
            else:
                node: AstNode = self.parse_next()
                result.append(node)

        return result

    def parse_next(self) -> AstNode:

        if tok := self.try_parse_assignment():
            return tok
        if tok := self.try_parse_print():
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
        if isinstance(tok, token.TokenNumericConstant):
            result = ConstantNode(tok.value)
        elif isinstance(tok, token.TokenIdentifier):
            result = IdentifierNode(tok.target)
        else:
            raise ParserError(f"Cannot parse to value provider: {tok}")

        if not isinstance(self.peek(), token.ExpressionSeparator):
            raise ParserError('Expected expression separator')

        return result

    def try_parse_assignment(self) -> Optional[AstNode]:

        try:
            with self._restore_on_error():
                target_token: token.TokenIdentifier = self.consume_type(token.TokenIdentifier)
                self.consume_type(token.TokenEquals)
                value_node = self.parse_value_provider()
                self.consume_type(token.TokenEOL)
                return AssignNode(target_token.target, value_node)
        except ParserError:
            return None

    def try_parse_print(self) -> Optional[AstNode]:

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
