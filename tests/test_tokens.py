from io import StringIO

from gcpu import token

import pytest

from gcpu.token import InvalidSyntaxError

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


def test_indentation_token():
    var = token.TokenIdentifier('var')
    print = token.TokenKeywordPrint()
    newblock = token.TokenBeginBlock()
    endblock = token.TokenEndBlock()
    const = token.TokenNumericConstant(5)
    eol = EOL[0]
    assert token.parse_file(StringIO("""
    var
    print
    
    """)) == [var, eol, print, eol]

    assert token.parse_file(StringIO("""
    var
      print
    
    """)) == [var, eol, newblock, print, eol, endblock]

    assert token.parse_file(StringIO("""
        var
          print
        5
        """)) == [var, eol, newblock, print, eol, endblock, const, eol]

    with pytest.raises(InvalidSyntaxError) as e:
        assert token.parse_line(StringIO("""
        var
            print
        """))

    assert token.parse_file(StringIO("""
    print
      print
        print
    
    var
    """)) == [
        print, eol,
        newblock, print, eol,
        newblock, print, eol,

        endblock, endblock,
        var, eol
    ]


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


def test_parse_from_file(tmp_path):
    baseline = [
        token.TokenIdentifier('var'), token.TokenEquals(), token.TokenNumericConstant(5), token.TokenEOL(),
        token.TokenKeywordPrint(), token.TokenLeftParenthesis(), token.TokenNumericConstant(5),
        token.TokenRightParenthesis(), token.TokenEOL(),
    ]

    file_content = """
    var = 5
    print(5)
    """

    assert token.parse_file(StringIO(file_content)) == baseline

    p = tmp_path / 'test_me.txt'
    p.write_text(file_content)
    with open(p, mode='r') as f:
        assert token.parse_file(f) == baseline


def test_add_leave_block_on_eof():
    content = """
    test
      print
   
   """

    expected = [
        token.TokenIdentifier('test'), token.TokenEOL(),
        token.TokenBeginBlock(), token.TokenKeywordPrint(), token.TokenEOL(),
        token.TokenEndBlock()
    ]

    actual = token.parse_file(StringIO(content))
    assert actual == expected
