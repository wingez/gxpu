from gcpu import token

import pytest

EOL = [token.TokenEOL()]


def test_indentation():
    assert token.get_indentation('test') == (0, 'test')
    assert token.get_indentation('\ttemp') == (1, 'temp')

    assert token.get_indentation('rest\t') == (0, 'rest\t')

    for i in range(10):
        assert token.get_indentation('\t' * i + 'test') == (i, 'test')
        assert token.get_indentation('  ' * i + 'test') == (i, 'test')

    with pytest.raises(token.InvalidSyntaxError):
        token.get_indentation(' test')

    with pytest.raises(token.InvalidSyntaxError):
        token.get_indentation('  \ttemp')


def test_to_token():
    assert token.to_token('(') == token.TokenLeftParenthesis()
    assert token.to_token(')') == token.TokenRightParenthesis()

    assert token.to_token('test') == token.TokenIdentifier('test')


def tokenize(items):
    return [token.to_token(item) for item in items] + EOL


def test_parse_line():
    # Comments
    assert token.parse_line('#') == EOL
    assert token.parse_line('test#') == [token.to_token('test')] + EOL

    # Regular worlds
    assert token.parse_line('test') == tokenize(['test'])
    assert token.parse_line('test hest') == tokenize(['test', 'hest'])

    # Function statement
    assert token.parse_line('def main(test:int, test2:bool, test3:str):') == tokenize([
        'def', 'main', '(', 'test', ':', 'int', ',', 'test2', ':', 'bool', ',', 'test3', ':', 'str', ')', ':'
    ])
    assert token.parse_line('def main(test:int, test2:bool, test3:str):') == [
        token.TokenKeywordDef(),
        token.TokenIdentifier('main'),
        token.TokenLeftParenthesis(),

        token.TokenIdentifier('test'),
        token.TokenColon(),
        token.TokenIdentifier('int'),
        token.TokenComma(),

        token.TokenIdentifier('test2'),
        token.TokenColon(),
        token.TokenIdentifier('bool'),
        token.TokenComma(),

        token.TokenIdentifier('test3'),
        token.TokenColon(),
        token.TokenIdentifier('str'),

        token.TokenRightParenthesis(),
        token.TokenColon(),

        token.TokenEOL(),
        # 'def', 'main', '(', 'test', ':', 'int', ',', 'test2', ':', 'bool', ',', 'test3', ':', 'str', ')', ':'
    ]

    assert token.parse_line('test3') == [token.TokenIdentifier('test3')] + EOL
    assert token.parse_line('456') == [token.TokenNumericConstant(456)] + EOL

    assert token.parse_line('a:int = 5') == tokenize(['a', ':', 'int', '=', '5'])
    assert token.parse_line('a = 5+10') == tokenize(['a', '=', '5', '+', '10'])
    assert token.parse_line('if a+b>10:') == tokenize(['if', 'a', '+', 'b', '>', '10', ':'])
    # assert token.parse_line('if a>=10:') == ['if', 'a', '>=', '10', ':']

    assert token.parse_line('print(test)') == [
        token.TokenKeywordPrint(), token.TokenLeftParenthesis(),
        token.TokenIdentifier('test'), token.TokenRightParenthesis(),
        token.TokenEOL(),
    ]
