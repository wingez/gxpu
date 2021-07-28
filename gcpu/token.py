from dataclasses import dataclass
from typing import Tuple, List

DELIMITERS = [' ', '#', '(', ')', ',', ':', '+', '-', '=', '<', '>']
TOKENS = ['(', ')', ',', ':', '=', '-', '+', '<', '>']


class InvalidSyntaxError(Exception):
    pass


@dataclass
class Token:
    pass


class ExpressionSeparator(Token): pass


class TokenEOL(ExpressionSeparator): pass


class TokenLeftParenthesis(Token):    pass


class TokenRightParenthesis(Token):    pass


class TokenComma(ExpressionSeparator):
    pass


class TokenColon(Token):
    pass


class TokenEquals(Token):
    pass


class TokenKeywordDef(Token):
    pass


@dataclass
class TokenIdentifier(Token):
    def __init__(self, target):
        self.target: str = target


@dataclass
class TokenNumericConstant(Token):
    def __init__(self, value: int):
        self.value: int = value


class TokenPlusSign(Token):
    pass


class TokenGreaterSign(Token):
    pass


def tokenize():
    pass


def to_token(text: str) -> Token:
    # sanity check
    if ' ' in text:
        raise InvalidSyntaxError()

    if text == '(':
        return TokenLeftParenthesis()
    if text == ')':
        return TokenRightParenthesis()
    if text == ':':
        return TokenColon()
    if text == ',':
        return TokenComma()
    if text == '=':
        return TokenEquals()
    if text == '+':
        return TokenPlusSign()
    if text == '>':
        return TokenGreaterSign()

    if text in TOKENS:
        raise InvalidSyntaxError("We should not reach this")

    # sanity check
    if not text.isalnum():
        assert False

    if text.isnumeric():
        return TokenNumericConstant(int(text))

    if text == 'def':
        return TokenKeywordDef()

    return TokenIdentifier(text)

    # we should not reach this
    # assert False


def parse_line(line) -> List[Token]:
    """
    Assumes line has no indentation
    :param line:
    :return:
    """

    result: List[Token] = []

    current = ''

    for index, symbol in enumerate(line):

        if symbol in DELIMITERS:

            if current:
                result.append(to_token(current))

            current = ''

            if symbol == ' ':
                # spaces only delimiters
                continue

            if symbol == '#':
                # Comment, ignore the rest of the line
                break

        current += symbol

        if current in TOKENS:
            result.append(to_token(current))
            current = ''

    if len(current) > 0:
        # current could be empty if last symbol was an delimiter
        result.append(to_token(current))

    result.append(TokenEOL())

    return result


def get_indentation(line: str) -> Tuple[int, str]:
    indentation = 0

    has_tabs = False
    has_spaces = False

    while True:

        indent_letters = 0

        if line.startswith('\t'):
            if has_spaces:
                raise InvalidSyntaxError('Cannot mix spaces and tabs')

            has_tabs = True
            indent_letters = 1

        if line.startswith('  '):
            if has_tabs:
                raise InvalidSyntaxError('Cannot mix spaces and tabs')
            has_spaces = True
            indent_letters = 2

        if line[0] == ' ' and line[1] != ' ':
            raise InvalidSyntaxError('Mismatched spaces')

        if indent_letters:

            indentation += 1
            line = line[indent_letters:]

        else:
            return indentation, line
