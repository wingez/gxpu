import pytest

from gcpu import ast, token


def test_basic():
    pass


def test_many_eol():
    tokens = [token.TokenEOL(), token.TokenEOL(), token.TokenIdentifier('test'), token.TokenEquals(),
              token.TokenNumericConstant(5), token.TokenEOL()]

    assert ast.parse_expressions(tokens) == [ast.AssignNode('test', ast.ConstantNode(5))]


def test_expression():
    tokens = [token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4), token.TokenEOL()]

    node = ast.Parser(tokens).parse_expression()
    assert isinstance(node, ast.AssignNode)
    assert node.value_node == ast.ConstantNode(4)
    assert node.target == 'test'

    node = ast.Parser(token.parse_line('print(5)')).parse_expression()
    assert isinstance(node, ast.PrintNode)
    assert node.target == ast.ConstantNode(5)

    with pytest.raises(ast.ParserError):
        # test no eol
        ast.Parser([token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4)]) \
            .parse_expression()


def test_parse_function():
    tokens = [token.TokenKeywordDef(), token.TokenIdentifier('test'), token.TokenLeftParenthesis(),
              token.TokenRightParenthesis(), token.TokenColon(), token.TokenEOL(), token.TokenBeginBlock(),
              token.TokenKeywordPrint(), token.TokenLeftParenthesis(), token.TokenNumericConstant(5),
              token.TokenRightParenthesis(), token.TokenEOL(),
              token.TokenEndBlock(),

              ]

    node = ast.Parser(tokens).parse_function_statement()
    assert node == ast.FunctionNode(name='test', body=[ast.PrintNode(ast.ConstantNode(5))])


@pytest.mark.skipif(True, reason='not implemented yet')
def test_parse_function_arguments():
    pass
