from io import StringIO

import pytest

from gcpu import ast, token


def test_basic():
    pass


def test_many_eol():
    tokens = [token.TokenEOL(), token.TokenEOL(), token.TokenIdentifier('test'), token.TokenEquals(),
              token.TokenNumericConstant(5), token.TokenEOL()]

    assert ast.parse_expressions(tokens) == [
        ast.AssignNode(ast.AssignTarget(ast.MemberAccess('test')), ast.ConstantNode(5))]


def test_expression():
    tokens = [token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4), token.TokenEOL()]

    node = ast.Parser(tokens).parse_statement()
    assert isinstance(node, ast.AssignNode)
    assert node.value_node == ast.ConstantNode(4)
    assert node.target == ast.AssignTarget(ast.MemberAccess('test'))

    node = ast.Parser(token.parse_line('print(5)')).parse_statement()
    assert isinstance(node, ast.PrintNode)
    assert node.target == ast.ConstantNode(5)

    with pytest.raises(ast.ParserError):
        # test no eol
        ast.Parser([token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4)]) \
            .parse_statement()


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
    node = ast.Parser(tokens).parse_function_definition()
    assert node == ast.FunctionNode(name='test', body=[ast.PrintNode(ast.ConstantNode(5))], arguments=[])


def test_parse_function_with_single_parameter():
    assert ast.Parser(get_func_tokens("param1")).parse_function_definition() == \
           ast.FunctionNode(name='test',
                            arguments=[ast.AssignTarget(ast.MemberAccess('param1'))],
                            body=[ast.PrintNode(
                                ast.ConstantNode(
                                    5))])


def test_parse_function_with_multiple_parameters():
    assert ast.Parser(get_func_tokens("param1", "param2", "param3")).parse_function_definition() == \
           ast.FunctionNode(
               name='test',
               arguments=[
                   ast.AssignTarget(ast.MemberAccess("param1")),
                   ast.AssignTarget(ast.MemberAccess("param2")),
                   ast.AssignTarget(ast.MemberAccess("param3"))
               ], body=[
                   ast.PrintNode(ast.ConstantNode(5))])


def test_parse_function_parameter_type():
    assert ast.Parser(token.parse_file(StringIO("""
    def test(param:type):
      print(5)
    """))).parse_function_definition() == \
           ast.FunctionNode(name='test',
                            arguments=[
                                ast.AssignTarget(ast.MemberAccess('param'), type='type')
                            ], body=[ast.PrintNode(ast.ConstantNode(5))])


def test_function_return_type():
    program = """
    def test():byte
      print(5)
    """
    assert ast.Parser(token.parse_file(StringIO(program))).parse_function_definition() == \
           ast.FunctionNode(
               name='test',
               arguments=[], return_type='byte', body=[
                   ast.PrintNode(ast.ConstantNode(5))])


def test_return():
    assert ast.Parser(token.parse_line('return')).parse_return_statement() == \
           ast.ReturnNode()

    assert ast.Parser(token.parse_line('return 5+a')).parse_return_statement() == \
           ast.ReturnNode(ast.AdditionNode(left=ast.ConstantNode(5), right=ast.MemberAccess('a')))


def test_call_no_parameters():
    assert ast.Parser(token.parse_line("func()")).parse_function_call() == ast.CallNode('func', parameters=[])


def test_call_parameters():
    assert ast.Parser(token.parse_line("func(5)")).parse_function_call() == ast.CallNode('func', parameters=[
        ast.ConstantNode(5)])

    assert ast.Parser(token.parse_line("func(5,10,test)")).parse_function_call() == \
           ast.CallNode('func', parameters=[
               ast.ConstantNode(5),
               ast.ConstantNode(10),
               ast.MemberAccess('test')
           ])


def test_assign_call():
    assert ast.Parser(token.parse_line("a=test()")).parse_assignment() == ast.AssignNode(
        target=ast.AssignTarget(ast.MemberAccess('a')), value_node=ast.CallNode('test', [])
    )


def test_while():
    code = """
    while 1:
      print(5)
    
    """

    assert ast.Parser(token.parse_file(StringIO(code))).parse_while_statement() == \
           ast.WhileNode(condition=ast.ConstantNode(1), body=[ast.PrintNode(ast.ConstantNode(5))])


def test_if():
    code = """
        if 1:
          print(5)

        """

    assert ast.Parser(token.parse_file(StringIO(code))).parse_if_statement() == \
           ast.IfNode(condition=ast.ConstantNode(1), body=[ast.PrintNode(ast.ConstantNode(5))])


def test_if_else():
    code = """
    if a:
      print(1)
    else:
      print(0)
    
    """
    assert ast.Parser(token.parse_file(StringIO(code))).parse_if_statement() == \
           ast.IfNode(condition=ast.MemberAccess('a'),
                      body=[ast.PrintNode(ast.ConstantNode(1))],
                      else_body=[ast.PrintNode(ast.ConstantNode(0))])
