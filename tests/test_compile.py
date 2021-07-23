from gcpu import compile, ast


def test_compile_basic():
    assert compile.compile_and_run([ast.AssignmentNode('test', 68)]) is not None

#
# def test_print():
#     assert compile.compile_and_run([
#         ast.AssignmentNode('test', 68),
#         ast.PrintNode('test')
#     ]) == b'D'
