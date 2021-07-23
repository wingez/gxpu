from typing import List, Optional, Type, Tuple, TypeVar, Generic

from . import token

T = TypeVar('T', bound=token.Token, covariant=True)


class AstNode:
    pass


class AssignmentNode(AstNode):
    def __init__(self, target: str, value: int):
        self.target = target
        self.value = value


class PrintNode(AstNode):
    def __init__(self, target: str):
        self.target = target


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
            first_token = self.peek()
            if isinstance(first_token, token.TokenIdentifier):
                if tok := self.try_parse_assignment():
                    result.append(tok)
                    continue
                if tok := self.try_parse_print():
                    result.append(tok)
                    continue

            raise ParserError(f'Dont know how to parse: {first_token}')
        return result

    def consume(self) -> token.Token:
        current = self._token[self._index]

        self._index += 1
        return current

    def consume_type(self, token_type: Type[T]) -> T:
        current = self.consume()
        if not isinstance(current, token_type):
            raise ParserError()

        return current

    def try_parse_assignment(self) -> Optional[AstNode]:
        checkpoint = self.savepoint()

        try:
            target_token: token.TokenIdentifier = self.consume_type(token.TokenIdentifier)
            self.consume_type(token.TokenEquals)
            value_token = self.consume_type(token.TokenNumericConstant)
        except ParserError:
            self.restore(checkpoint)
            return None

        return AssignmentNode(target_token.target, value_token.value)

    def try_parse_print(self) -> Optional[AstNode]:
        checkpoint = self.savepoint()

        try:
            should_be_print_token = self.consume_type(token.TokenIdentifier)
            if not should_be_print_token.target == 'print':
                self.restore(checkpoint)
                return None
            self.consume_type(token.TokenLeftParenthesis)
            target = self.consume_type(token.TokenIdentifier)
            self.consume_type(token.TokenRightParenthesis)
        except ParserError:
            self.restore(checkpoint)
            return None

        return PrintNode(target.target)
