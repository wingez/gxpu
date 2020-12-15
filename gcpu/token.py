from typing import Tuple, List

DELIMITERS = [' ', '#', '(', ')', ',', ':', '+', '-', '=', '<', '>']
TOKENS = ['(', ')', ',', ':', '=', '-', '+', '<', '>']


class InvalidSyntaxError(Exception):
    pass


class Token:
    pass


class Block:
    pass


def tokenize():
    pass


def parse_line(line) -> List[Token]:
    """
    Assumes line has no indentation
    :param line:
    :return:
    """

    result = []

    current = ''

    for index, symbol in enumerate(line):

        if symbol in DELIMITERS:

            if current:
                result.append(current)

            current = ''

            if symbol == ' ':
                # spaces only delimiters
                continue

            if symbol == '#':
                # Comment, ignore the rest of the line
                break

        current += symbol

        if current in TOKENS:
            result.append(current)
            current = ''

    if len(current) > 0:
        # current could be empty if last symbol was an delimiter
        result.append(current)

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
