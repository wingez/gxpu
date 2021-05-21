from gcpu import ast, token


def test_basic():
    tokens = [token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4)]

    nodes = ast.Parser(tokens).parse()
    assert len(nodes) == 1
    node = nodes[0]
    assert isinstance(node, ast.AssignmentNode)
    assert node.value == 4
    assert node.target == 'test'

