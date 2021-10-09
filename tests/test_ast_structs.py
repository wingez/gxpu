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
               ast.AssignTarget(ast.MemberAccess('member1'), type=None),
               ast.AssignTarget(ast.MemberAccess('member2'), type=None)])

    code = """
    struct tmp:
      member1:byte
      member2:int

    """
    assert parser_from_code(code).parse_struct() == \
           ast.StructNode(name='tmp', members=[
               ast.AssignTarget(ast.MemberAccess('member1'), type='byte'),
               ast.AssignTarget(ast.MemberAccess('member2'), type='int')])

    code = """
    struct test:
      member1:new int
    """
    assert parser_from_code(code).parse_struct() == \
           ast.StructNode(name='test', members=[
               ast.AssignTarget(ast.MemberAccess('member1'), type='int', explicit_new=True)
           ])


def test_struct_assign():
    code = """
    member.i=1
    """
    assert parser_from_code(code).parse_assignment() == \
           ast.AssignNode(target=ast.AssignTarget(ast.MemberAccess('member', actions=[ast.MemberAccessAction('i')])),
                          value_node=ast.ConstantNode(1))


def test_struct_assign_in_function():
    code = """
    def main():
      a: type1
      
      a.member1=2
      a.member2=1
    
    """
    assert parser_from_code(code).parse_function_definition() == \
           ast.FunctionNode('main', [], body=[
               ast.AssignTarget(ast.MemberAccess('a'), 'type1'),
               ast.AssignNode(ast.AssignTarget(ast.MemberAccess('a', [ast.MemberAccessAction('member1')])),
                              value_node=ast.ConstantNode(2)),
               ast.AssignNode(ast.AssignTarget(ast.MemberAccess('a', [ast.MemberAccessAction('member2')])),
                              value_node=ast.ConstantNode(1)),
           ])


def test_struct_member_read():
    code = """
        a=s.member
        """
    assert parser_from_code(code).parse_assignment() == \
           ast.AssignNode(target=ast.AssignTarget(ast.MemberAccess('a')),
                          value_node=ast.MemberAccess('s', [ast.MemberAccessAction('member')]))
