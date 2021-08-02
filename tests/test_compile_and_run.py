from gcpu import ast, token, compile
from gcpu.compile import CompileError

from gcpu.default_config import DefaultEmulator

from io import StringIO

import pytest


def run_function_body_text_check_output(text, *output):
    file = StringIO(text)

    tokens = token.parse_file(file)

    nodes = ast.parse_expressions(tokens)
    run_expression_nodes_check_output(nodes, *output)


def run_expression_nodes_check_output(nodes, *output):
    c = compile.Compiler()

    code = c.build_single_main_function(nodes)

    run_code_check_output(code, *output)


def run_code_check_output(code, *output):
    if len(output) > 0 and isinstance(output[0], bytes):
        expected = output[0]
    else:
        expected = bytes(output)

    e = DefaultEmulator()
    e.set_all_memory(code)
    e.run()
    assert e.get_output() == expected


def run_program_text(text, *output):
    file = StringIO(text)
    tokens = token.parse_file(file)

    nodes = ast.parse(tokens)

    c = compile.Compiler()
    code = c.build_program(nodes)

    run_code_check_output(code, *output)


def test_empty_program():
    run_expression_nodes_check_output([], b'')


def test_print_constant():
    nodes = [ast.PrintNode(ast.ConstantNode(5))]

    run_expression_nodes_check_output(nodes, 5)


def test_print_variable():
    nodes = [
        ast.AssignNode('var', ast.ConstantNode(4)),
        ast.PrintNode(ast.IdentifierNode('var'))
    ]
    run_expression_nodes_check_output(nodes, 4)


def test_print_many_variables():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(5)),
        ast.AssignNode('var2', ast.ConstantNode(8)),
        ast.PrintNode(ast.IdentifierNode('var1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    run_expression_nodes_check_output(nodes, 5, 8)


def test_reassign_variable():
    content = """
    var1 = 3
    print(var1)
    var1 = 5
    print(var1)
    """
    run_function_body_text_check_output(content, 3, 5)


def test_variable_move():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(2)),
        ast.AssignNode('var2', ast.IdentifierNode('var1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    run_expression_nodes_check_output(nodes, 2)

    content = """
    var1 = 2
    var2 = var1
    var1 = 1
    
    print(var2)
    print(var1)
    """
    run_function_body_text_check_output(content, 2, 1)


def test_invalid_variable_name():
    nodes = [
        ast.AssignNode('var1', ast.ConstantNode(2)),
        ast.AssignNode('var2', ast.IdentifierNode('var 1')),
        ast.PrintNode(ast.IdentifierNode('var2')),
    ]
    with pytest.raises(CompileError):
        run_expression_nodes_check_output(nodes, 2)


def test_call_function():
    program = """
    def test1():
      print(5)
      
    def test2():
      print(10)
      
    def main():
      test1()
      test2()
      print(3)
    
    """

    run_program_text(program, 5, 10, 3)
