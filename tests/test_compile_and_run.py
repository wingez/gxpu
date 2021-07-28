from gcpu import ast, token, compile

from gcpu.default_config import DefaultEmulator


def test_basic():
    e = DefaultEmulator()

    node = ast.PrintNode(ast.ConstantNode(5))

    code = compile.AssemblyFunction.build_function(None, [node])

    e.set_all_memory(code)
    e.run()
    assert e.get_output() == bytes([5])
