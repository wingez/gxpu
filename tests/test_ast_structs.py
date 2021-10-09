from io import StringIO

from gcpu import ast, token


def parser_from_code(text: str) -> ast.Parser:
    return ast.Parser(token.parse_file(StringIO(text)))


def test_struct():
    code = """
    struct tmp:
      member1
      member2
    """

    assert parser_from_code(code).parse_struct() == \
           ast.StructNode(name='tmp', members=[
               ast.AssignTarget(name='member1', type=None),
               ast.AssignTarget(name='member2', type=None)])

    code = """
    struct tmp:
      member1:byte
      member2:int

    """
    assert parser_from_code(code).parse_struct() == \
           ast.StructNode(name='tmp', members=[
               ast.AssignTarget(name='member1', type='byte'),
               ast.AssignTarget(name='member2', type='int')])

    code = """
    struct test:
      member1:new int
    """
    assert parser_from_code(code).parse_struct() == \
           ast.StructNode(name='test', members=[
               ast.AssignTarget(name='member1', type='int', explicit_new=True)
           ])


def test_struct_assign():
    code = """
    member.i=1
    """
    assert parser_from_code(code).parse_assignment() == \
           ast.AssignNode(target=ast.AssignTarget('name', actions=[ast.MemberAccess('i')]),
                          value=ast.ConstantNode(1))
