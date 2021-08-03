from dataclasses import dataclass
from typing import Tuple, List
from typing import TextIO

DELIMITERS = [' ', '#', '(', ')', ',', ':', '+', '-', '=', '<', '>']
TOKENS = ['(', ')', ',', ':', '=', '-', '+', '<', '>']


class InvalidSyntaxError(Exception):
    pass


@dataclass
class Token:
    pass


class ExpressionSeparator(Token): pass


class TokenEOL(ExpressionSeparator): pass


class TokenLeftParenthesis(ExpressionSeparator):    pass


class TokenRightParenthesis(ExpressionSeparator):    pass


class TokenComma(ExpressionSeparator):
    pass


class TokenColon(Token):
    pass


class TokenEquals(Token):
    pass


class TokenKeywordDef(Token):
    pass


class TokenKeywordPrint(Token):
    pass


class TokenKeywordWhile(Token):
    pass


class TokenBeginBlock(Token): pass


class TokenEndBlock(Token): pass


@dataclass
class TokenIdentifier(Token):
    def __init__(self, target):
        self.target: str = target


@dataclass
class TokenNumericConstant(Token):
    def __init__(self, value: int):
        self.value: int = value


class TokenSingleOperation(Token): pass


class TokenPlusSign(TokenSingleOperation): pass


class TokenMinusSign(TokenSingleOperation): pass


class TokenGreaterSign(Token):
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
    if text == '-':
        return TokenMinusSign()
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
    if text == 'print':
        return TokenKeywordPrint()
    if text == 'while':
        return TokenKeywordWhile()

    return TokenIdentifier(text)


def parse_file(file: TextIO) -> List[Token]:
    result: List[Token] = []

    current_indentation: int = -1
    base_indentation = 0

    for line_to_parse in file:
        line_to_parse = line_to_parse.strip('\n')
        if not line_to_parse.strip(' \t'):
            continue

        indentation, line = get_indentation(line_to_parse)

        if current_indentation == -1:
            base_indentation = indentation
        else:

            if indentation > current_indentation:
                if indentation != current_indentation + 1:
                    raise InvalidSyntaxError('Cannot increment indentation by more than 1 step')
                result.append(TokenBeginBlock())
            elif indentation < current_indentation:
                for i in range(indentation, current_indentation):
                    result.append(TokenEndBlock())

        current_indentation = indentation

        result.extend(parse_line(line))

    if current_indentation != -1:
        for _ in range(current_indentation - base_indentation):
            result.append(TokenEndBlock())
    return result


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
