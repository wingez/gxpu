from gcpu import token

import pytest


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


def test_parse_line():
    # Comments
    assert token.parse_line('#') == []
    assert token.parse_line('test#') == ['test']

    # Regular worlds
    assert token.parse_line('test') == ['test']
    assert token.parse_line('test hest') == ['test', 'hest']

    # Function statement
    assert token.parse_line('def main(test:int, test2:bool, test3:str):') == [

        'def', 'main', '(', 'test', ':', 'int', ',', 'test2', ':', 'bool', ',', 'test3', ':', 'str', ')', ':'
    ]

    assert token.parse_line('a:int = 5') == ['a', ':', 'int', '=', '5']
    assert token.parse_line('a = 5+10') == ['a', '=', '5', '+', '10']
    assert token.parse_line('if a+b>10:') == ['if', 'a', '+', 'b', '>', '10', ':']
    assert token.parse_line('if a>=10:') == ['if', 'a', '>=', '10', ':']
