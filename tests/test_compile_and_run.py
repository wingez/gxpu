from gcpu import ast, token, compile


def compile_and_run(*lines):
    tokens = []
    for line in lines:
        tokens.extend(token.parse_line(line))

    nodes = ast.Parser(tokens).parse()
    return compile.compile_and_run(nodes)


def test_basic_print():
    assert compile_and_run('test=68',
                           'print(test)') == b'D'
