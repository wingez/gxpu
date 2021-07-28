from __future__ import annotations
from contextlib import contextmanager
from dataclasses import dataclass
from typing import List, Optional, Type, TypeVar

from . import token

T = TypeVar('T', bound=token.Token, covariant=True)


@dataclass
class AstNode: pass


class ValueProviderNode(AstNode):
    pass


@dataclass
class AssignConstantNode(AstNode):
    def __init__(self, target: str, value: ConstantNode):
        self.target = target
        self.value_node = value


@dataclass
class PrintNode(AstNode):
    def __init__(self, target: ConstantNode):
        self.target = target


@dataclass
class IdentifierNode(AstNode):
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
            node: AstNode = self.parse_next()
            result.append(node)

        return result

    def parse_next(self) -> AstNode:

        if tok := self.try_parse_assignment():
            return tok
        if tok := self.try_parse_print():
            return tok
        #
        # if (tok := self.try_parse_addition()) and node_filter(tok):
        #     return tok

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
        constant = self.consume_type(token.TokenNumericConstant)
        if not isinstance(self.peek(), token.ExpressionSeparator):
            raise ParserError()
        return ConstantNode(constant.value)

    def try_parse_constant(self) -> Optional[AstNode]:

        try:
            with self._restore_on_error():
                numeric_token = self.consume_type(token.TokenNumericConstant)
                return ConstantNode(numeric_token.value)
        except ParserError:
            return None

    def try_parse_assignment(self) -> Optional[AstNode]:

        try:
            with self._restore_on_error():
                target_token: token.TokenIdentifier = self.consume_type(token.TokenIdentifier)
                self.consume_type(token.TokenEquals)
                value_node = self.parse_value_provider()
                self.consume_type(token.TokenEOL)
                if isinstance(value_node, ConstantNode):
                    return AssignConstantNode(target_token.target, value_node)
                raise ParserError
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

                if isinstance(target, ConstantNode):
                    return PrintNode(target)
                raise ParserError()
        except ParserError:
            return None

    def try_parse_addition(self) -> Optional[AstNode]:
        checkpoint = self.savepoint()

        try:
            left_operand = self.parse_next(ConstantNode)
            self.consume_type(token.TokenPlusSign)
            right_operand = self.parse_next(ConstantNode)

            return AdditionNode(left_operand, right_operand)

        except ParserError:
            self.restore(checkpoint)
            return None
