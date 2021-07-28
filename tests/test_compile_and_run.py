from gcpu import ast, token, compile

from gcpu.default_config import DefaultEmulator


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
    ]
