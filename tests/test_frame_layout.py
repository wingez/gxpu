from io import StringIO
from typing import List

from gcpu.ast import Parser
from gcpu.compile import Compiler
from gcpu.token import parse_file

from gcpu import compile


def get_layout(program: str, structs: List[str] = []) -> compile.FrameLayout:
    compiler = Compiler()

    for struct in structs:
        compiler.build_struct(Parser(parse_file(StringIO(struct))).parse_struct())

    function_node = Parser(parse_file(StringIO(program))).parse_function_definition()

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

    assert layout.identifiers == {'test': compile.StructDataField('test', 2, compile.byte)}


def test_var():
    layout = get_layout("""
    def test1():
      var=5
    """)

    assert layout.total_size == 3
    assert layout.size_of_parameters == 0
    assert layout.size_of_vars == 1
    assert layout.identifiers == {'var': compile.StructDataField('var', 0, compile.byte)}


def test_if():
    layout = get_layout("""
    def test1():
      if 5:
        var=1
    """)

    assert layout.total_size == 3
    assert layout.identifiers == {'var': compile.StructDataField('var', 0, compile.byte)}


def test_print():
    layout = get_layout("""
    def test1(var2):
      if 5:
        var=1
    """)

    assert layout.total_size == 4
    assert layout.size_of_vars == layout.size_of_parameters == 1
    assert layout.identifiers == {
        'var': compile.StructDataField('var', 0, compile.byte),
        'var2': compile.StructDataField('var2', 3, compile.byte),
    }
    assert layout.get_description() == [
        "0: var: byte",
        "3: var2: byte"
    ]


def test_return_data():
    layout = get_layout("""
    def test1(var2): byte
      if 5:
        var=1
    """)

    assert layout.total_size == 5
    assert layout.size_of_vars == layout.size_of_parameters == 1
    assert layout.size_of_ret == 1
    assert layout.size_of_parameters == 1
    assert layout.identifiers['result'] == compile.StructDataField('result', 4, compile.byte)


def test_struct_correct_size():
    layout = get_layout("""
    def test1(p:type1): type2
      a:type2
    """, ["""
    struct type1:
      member1:byte    
      member2:byte    
        """, """
    struct type2:
      member3:byte
      member4:byte
      member5:byte
        """
          ])

    assert layout.total_size == 10
    assert layout.size_of_vars == 3
    assert layout.size_of_parameters == 2
    assert layout.size_of_ret == 3


def test_struct_correct_size2():
    layout = get_layout("""
      def main():
        a:type1
      
        a.member1=2
        a.member2=1
    """, ["""
      struct type1:
        member1:byte
        member2:byte 
     """])

    assert layout.total_size == 4
    assert layout.size_of_vars == 2
    assert layout.size_of_parameters == 0
    assert layout.size_of_ret == 0
    assert layout.identifiers == {
        'a': compile.StructDataField('a', 0, compile.StructData('type1', fields=[
            compile.StructDataField('member1', 0, compile.byte)], size=2, stack_creation=False))}
