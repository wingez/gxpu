from gcpu import ast, token, compile, emulator
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


def test_variable_increment():
    content = """
    var = 3
    print(var)
    var=var+1
    print(var)
    
    """
    run_function_body_text_check_output(content, 3, 4)


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


def test_call_function_with_single_parameter():
    program = """
    def test(val):
      print(val)
    
    
    def main():
      test(5)
      print(1)
    
    """
    run_program_text(program, 5, 1)


def test_call_function_many_parameters():
    program = """
    def test(param1,param2):
      print(param2)
      print(param1)
      
    def main():
      print(5)
      test(10,6)
      print(7)
    
    """
    run_program_text(program, 5, 6, 10, 7)


def test_call_function_parameter_variable():
    program = """
    def test(arg):
      print(arg)
      
    def test2(arg):
      test(arg+5)
      
    def main():
      v = 5
      test(v)
      test2(10)
      test2(v)
    
    """
    run_program_text(program, 5, 15, 10)


def test_call_function_consecutive_many_parameters():
    program = """
    def test(arg1,arg2,arg3):
      v = arg1+arg2
      print(v+arg3)
      
    def main():
      test(1,2,3)
      test(1,1,2)  
    """
    run_program_text(program, 6, 4)


def test_while():
    program = """
    def main():
      while 1:
        print(5)
    
    """
    with pytest.raises(emulator.ExecutionCyclesExceededError):
        run_program_text(program)


def test_while_decrement():
    body = """
    var=5
    while var:
      print(var)
      var=var-1
    
    
    """
    run_function_body_text_check_output(body, 5, 4, 3, 2, 1)


def test_if():
    body = """
    if 0:
      print(0)
    if 1:
      print(1)
    """
    run_function_body_text_check_output(body, 1)


def test_if_nested():
    body = """
    if 1:
      print(1)
      if 0:
        print(0)
      print(2)
    """
    run_function_body_text_check_output(body, 1, 2)

    body = """
    a=3
    while a:
      if a-2:
        print(8)
      print(a)
      a=a-1
    
    """
    run_function_body_text_check_output(body, 8, 3, 2, 8, 1)


def test_if_else():
    body = """
    a=5
    while a:
      if a-3:
        print(0)
      else:
        print(1)
      a=a-1
    
    
    """
    run_function_body_text_check_output(body, 0, 0, 1, 0, 0)


def test_call_return():
    pytest.skip("not implemented")
    program = """
    def test():byte
      return 5
      
    def main():
      a=test()
      print(a)
    
    """
    run_program_text(program, 5)


def test_call_return_parameters():
    pytest.skip("not implemented")
    program = """
    def add(val1,val2):byte
      return val1+val2
      
    def main():
      print(add(1,2))
      print(add(0,0))
    
    """
    run_program_text(program, 3, 0)

    program = """
    def add2(val1,val2):byte
      return val1+val2
    
    def add3(v1,v2,v3):byte
      return add2(v1,add2(v2,v3))
    
    def main():
      print(add3(1,2,4))
      print(add3(5,6,7))
      print(add3(1,0,0))
    """
    run_program_text(program, 7, 18, 1)
