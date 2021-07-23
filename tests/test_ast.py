from gcpu import ast, token
from gcpu.constant import Constant


def test_basic():
    tokens = [token.TokenIdentifier('test'), token.TokenEquals(), token.TokenNumericConstant(4)]

    nodes = ast.Parser(tokens).parse()
    assert len(nodes) == 1
    node = nodes[0]
    assert isinstance(node, ast.AssignmentNode)
    assert node.value == Constant(4)
    assert node.target == 'test'

    node, *_ = ast.Parser(token.parse_line('print(name)')).parse()
    assert isinstance(node, ast.PrintNode)
    assert node.target == 'name'
