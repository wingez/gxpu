from gcpu import compile, ast


def test_compile_basic():
    assert compile.compile_ast([ast.AssignmentNode('test', 68)]) == b'D'
    assert compile.compile_ast([ast.AssignmentNode('test', 69)]) == chr(69).encode()

