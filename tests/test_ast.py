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


def get_func_tokens(*parameters):
    tokens = [token.TokenKeywordDef(), token.TokenIdentifier('test'), token.TokenLeftParenthesis(), ]

    num_params = len(parameters)
    for index, param in enumerate(parameters):
        tokens.append(token.TokenIdentifier(param))
        if index != num_params:
            tokens.append(token.TokenComma())

    tokens.extend([token.TokenRightParenthesis(), token.TokenColon(), token.TokenEOL(), token.TokenBeginBlock(),
                   token.TokenKeywordPrint(), token.TokenLeftParenthesis(), token.TokenNumericConstant(5),
                   token.TokenRightParenthesis(), token.TokenEOL(),
                   token.TokenEndBlock(),

                   ])
    return tokens


def test_parse_function():
    tokens = get_func_tokens()
    node = ast.Parser(tokens).parse_function_statement()
    assert node == ast.FunctionNode(name='test', body=[ast.PrintNode(ast.ConstantNode(5))])


def test_parse_function_with_single_parameter():
    assert ast.Parser(get_func_tokens("param1")).parse_function_statement() == \
           ast.FunctionNode(name='test',
                            arguments=['param1'],
                            body=[ast.PrintNode(
                                ast.ConstantNode(
                                    5))])


def test_parse_function_with_multiple_parameters():
    assert ast.Parser(get_func_tokens("param1", "param2", "param3")).parse_function_statement() == \
           ast.FunctionNode(
               name='test',
               arguments=["param1", "param2",
                          "param3"], body=[
                   ast.PrintNode(ast.ConstantNode(5))])


def test_call_no_parameters():
    assert ast.Parser(token.parse_line("func()")).try_parse_function_call() == ast.CallNode('func', parameters=[])


def test_call_parameters():
    assert ast.Parser(token.parse_line("func(5)")).try_parse_function_call() == ast.CallNode('func', parameters=[
        ast.ConstantNode(5)])

    assert ast.Parser(token.parse_line("func(5,10,test)")).try_parse_function_call() == \
           ast.CallNode('func', parameters=[
               ast.ConstantNode(5),
               ast.ConstantNode(10),
               ast.IdentifierNode('test')
           ])
