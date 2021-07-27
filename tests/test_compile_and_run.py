from gcpu import ast, token, compile
from gcpu.runner import Runner
from gcpu.scope import Scope


def compile_and_run_get_output(*lines):
    tokens = []
    for line in lines:
        tokens.extend(token.parse_line(line))

    nodes = ast.Parser(tokens).parse()

    runner = Runner()
    scope = Scope()

    ast.execute(nodes, runner, scope, )

    return runner.output()


def test_basic_print():
    assert compile_and_run_get_output('test=68',
                                      'print(test)') == '68\n'

#
# def test_addition():
#     assert compile_and_run_get_output('test=10+5', 'print(test)') == '15\n'
