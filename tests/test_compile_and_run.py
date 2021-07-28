from gcpu import ast, token, compile
from gcpu.compile import CompileError

from gcpu.default_config import DefaultEmulator

import pytest


def compile_and_run_check_output(nodes, *output):
    if len(output) > 0 and isinstance(output[0], bytes):
        expected = output[0]
    else:
        expected = bytes(output)

    e = DefaultEmulator()
    c = compile.Compiler()

    code = c.build_function(nodes)

    e.set_all_memory(code)
    e.run()
    assert e.get_output() == expected


def test_empty_program():
    compile_and_run_check_output([], b'')


def test_print_constant():
    nodes = [ast.PrintNode(ast.ConstantNode(5))]

    compile_and_run_check_output(nodes, 5)


def test_print_variable():
    nodes = [
        ast.AssignNode('var', ast.ConstantNode(4)),
        ast.PrintNode(ast.IdentifierNode('var'))
    ]
    compile_and_run_check_output(nodes, 4)


def test_print_many_variables():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(5)),
        ast.AssignNode('var2', ast.ConstantNode(8)),
        ast.PrintNode(ast.IdentifierNode('var1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    compile_and_run_check_output(nodes, 5, 8)


def test_variable_move():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(2)),
        ast.AssignNode('var2', ast.IdentifierNode('var1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    compile_and_run_check_output(nodes, 2)


def test_invalid_variable_name():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(2)),
        ast.AssignNode('var2', ast.IdentifierNode('var 1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    with pytest.raises(CompileError):
        compile_and_run_check_output(nodes, 2)
