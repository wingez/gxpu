from io import StringIO

import pytest

from gcpu import token, ast
from tests.test_compile_and_run import run_text_check_output


def test_parse_basic():
    assert ast.Parser(token.parse_line('5+10')).parse_value_provider() == ast.AdditionNode(left=ast.ConstantNode(5),
                                                                                           right=ast.ConstantNode(10))


def test_parse_too_complex():
    with pytest.raises(ast.ParserError) as e:
        ast.Parser(token.parse_line('5+5+10')).parse_value_provider()
    assert 'too complex' in str(e)


def test_parse_with_identifier():
    assert ast.parse_expressions(token.parse_line('print(2+test)')) == [ast.PrintNode(
        ast.AdditionNode(left=ast.ConstantNode(2), right=ast.IdentifierNode('test')))]


def test_run_addition():
    file_content = """
    var1 = 5+10
    print(var1)
    """
    run_text_check_output(file_content, 15)
    run_text_check_output("""
    var1=6
    print(6+var1)
    
    """, 12)


@pytest.mark.parametrize("first,second", [("10", "5"), ("var1", "10"), ("25", "var2"), ("var2", "var1")])
@pytest.mark.parametrize("operator", ['+', '-'])
def test_mixed_operations(first, second, operator):
    content = f"""
    var1=12
    var2 = 20
    
    print({first} {operator} {second})
    """
    expected = eval(f"{first} {operator} {second}", globals(), {'var1': 12, 'var2': 20})
    run_text_check_output(content, expected)
