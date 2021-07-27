from typing import List, Optional, Type, TypeVar

from . import token
from .constant import Constant
from .intepreter import InterpreterError
from .runner import Runner
from .scope import Scope

T = TypeVar('T', bound=token.Token, covariant=True)

execution_result = Optional[Constant]


class AstNode:
    def execute(self, scope: Scope, runner: Runner) -> execution_result:
        pass


V = TypeVar('V', bound=AstNode, covariant=True)


class ValueProviderNode(AstNode):
    def execute(self, scope: Scope, runner: Runner) -> Constant:
        pass


class AssignmentNode(AstNode):
    def __init__(self, target: str, value: ValueProviderNode):
        self.target = target
        self.value_node = value

    def execute(self, scope: Scope, runner: Runner) -> execution_result:
        constant = self.value_node.execute(scope, runner)
        scope.set(self.target, constant)
        return None


class PrintNode(AstNode):
    def __init__(self, target: str):
        self.target = target

    def execute(self, scope: Scope, runner: Runner) -> execution_result:
        value = scope.get(self.target)
        runner.print(value)
        return None


class IdentifierNode(AstNode):
    def __init__(self, identifier: str):
        self.identifier = identifier


class ConstantNode(ValueProviderNode):
    def __init__(self, value: Constant):
        self.value = value

    def execute(self, scope: Scope, runner: Runner) -> Constant:
        return self.value


class AdditionNode(ValueProviderNode):
    def __init__(self, left: AstNode, right: AstNode):
        self.left = left
        self.right = right

    def execute(self, scope: Scope, runner: Runner):
        left_executed, right_executed = self.left.execute(scope, runner), self.right.execute(scope, runner)
        if not (isinstance(left_executed, Constant) and isinstance(right_executed, Constant)):
            raise InterpreterError('Operation not supported')

        return Constant(left_executed.value + right_executed.value)


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
            node:AstNode = self.parse_next()
            result.append(node)

        return result

    def parse_next(self, node_type: Type[V] = None) -> V:

        def node_filter(node: AstNode) -> bool:
            if node_type is None:
                return True
            return isinstance(node, node_type)

        first_token = self.peek()

        if isinstance(first_token, token.TokenNumericConstant):
            return ConstantNode(Constant(first_token.value))

        if isinstance(first_token, token.TokenIdentifier):
            if (tok := self.try_parse_assignment()) and node_filter(tok):
                return tok
            if (tok := self.try_parse_print()) and node_filter(tok):
                return tok

        if (tok := self.try_parse_addition()) and node_filter(tok):
            return tok

        raise ParserError(f'Dont know how to parse: {first_token}')

    def consume(self) -> token.Token:
        current = self._token[self._index]

        self._index += 1
        return current

    def consume_type(self, token_type: Type[T]) -> T:
        current = self.consume()
        if not isinstance(current, token_type):
            raise ParserError()

        return current

    def try_parse_constant(self) -> Optional[AstNode]:
        checkpoint = self.savepoint()

        try:
            numeric_token = self.consume_type(token.TokenNumericConstant)
            return ConstantNode(Constant(numeric_token.value))
        except ParserError:
            self.restore(checkpoint)
            return None

    def try_parse_assignment(self) -> Optional[AstNode]:
        checkpoint = self.savepoint()

        try:
            target_token: token.TokenIdentifier = self.consume_type(token.TokenIdentifier)
            self.consume_type(token.TokenEquals)
            value_node = self.parse_next(ValueProviderNode)
        except ParserError:
            self.restore(checkpoint)
            return None

        return AssignmentNode(target_token.target, value_node)

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


def execute(nodes: List[AstNode], runner: Runner, scope: Scope):
    for node in nodes:
        node.execute(scope, runner)
