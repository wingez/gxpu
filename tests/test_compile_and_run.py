from gcpu import ast, token, compile
from gcpu.compile import CompileError

from gcpu.default_config import DefaultEmulator

from io import StringIO

import pytest


def run_text_check_output(text, *output):
    file = StringIO(text)

    tokens = token.parse_file(file)

    nodes = ast.Parser(tokens).parse()
    run_nodes_check_output(nodes, *output)


def run_nodes_check_output(nodes, *output):
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
    run_nodes_check_output([], b'')


def test_print_constant():
    nodes = [ast.PrintNode(ast.ConstantNode(5))]

    run_nodes_check_output(nodes, 5)


def test_print_variable():
    nodes = [
        ast.AssignNode('var', ast.ConstantNode(4)),
        ast.PrintNode(ast.IdentifierNode('var'))
    ]
    run_nodes_check_output(nodes, 4)


def test_print_many_variables():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(5)),
        ast.AssignNode('var2', ast.ConstantNode(8)),
        ast.PrintNode(ast.IdentifierNode('var1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    run_nodes_check_output(nodes, 5, 8)


def test_reassign_variable():
    content = """
    var1 = 3
    print(var1)
    var1 = 5
    print(var1)
    """
    run_text_check_output(content, 3, 5)


def test_variable_move():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(2)),
        ast.AssignNode('var2', ast.IdentifierNode('var1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    run_nodes_check_output(nodes, 2)

    content = """
    var1 = 2
    var2 = var1
    var1 = 1
    
    print(var2)
    print(var1)
    """
    run_text_check_output(content, 2, 1)


def test_invalid_variable_name():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(2)),
        ast.AssignNode('var2', ast.IdentifierNode('var 1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    with pytest.raises(CompileError):
        run_nodes_check_output(nodes, 2)
