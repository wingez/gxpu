from io import StringIO

from gcpu.ast import Parser
from gcpu.compile import Compiler
from gcpu.token import parse_file

from gcpu import compile


def get_layout(program: str) -> compile.FrameLayout:
    function_node = Parser(parse_file(StringIO(program))).parse_function_definition()
    compiler = Compiler()

    return compile.get_frame_layout(compiler, function_node)


def test_empty():
    layout = get_layout("""
    def test1():
      print(5)
    """)

    assert layout.total_size == 2
    assert layout.size_of_vars == layout.size_of_parameters == 0
    assert layout.identifiers == {}


def test_param():
    layout = get_layout("""
    def test1(test):
      print(5)
    """)

    assert layout.total_size == 3
    assert layout.size_of_vars == 0
    assert layout.size_of_parameters == 1

    assert layout.identifiers == {'test': 2}


def test_var():
    layout = get_layout("""
    def test1():
      var=5
    """)

    assert layout.total_size == 3
    assert layout.size_of_parameters == 0
    assert layout.size_of_vars == 1
    assert layout.identifiers == {'var': 0}


def test_if():
    layout = get_layout("""
    def test1():
      if 5:
        var=1
    """)

    assert layout.total_size == 3
    assert layout.identifiers == {'var': 0}


def test_print():
    layout = get_layout("""
    def test1(var2):
      if 5:
        var=1
    """)

    assert layout.total_size == 4
    assert layout.size_of_vars == layout.size_of_parameters == 1
    assert layout.identifiers == {'var': 0, 'var2': 3}
    assert layout.get_description() == [
        "0: var",
        "3: var2"
    ]
