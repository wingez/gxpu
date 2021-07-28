from gcpu import ast, token


def test_basic():
    tokens = [token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4), token.TokenEOL()]

    nodes = ast.Parser(tokens).parse()
    assert len(nodes) == 1
    node = nodes[0]
    assert isinstance(node, ast.AssignNode)
    assert node.value_node == ast.ConstantNode(4)
    assert node.target == 'test'

    node, *_ = ast.Parser(token.parse_line('print(5)')).parse()
    assert isinstance(node, ast.PrintNode)
    assert node.target == ast.ConstantNode(5)
