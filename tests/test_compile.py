from gcpu import compile, ast


def test_compile_basic():
    assert compile.compile_and_run([ast.AssignmentNode('test', 68)]) == b'D'
    assert compile.compile_and_run([ast.AssignmentNode('test', 69)]) == chr(69).encode()

#
# def test_print():
#     assert compile.compile_and_run([
#         ast.AssignmentNode('test', 68),
#         ast.PrintNode('test')
#     ]) == chr(68).encode()
