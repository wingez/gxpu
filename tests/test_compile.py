from io import StringIO

import pytest

from gcpu import compile, ast, default_config, assembler


def test_fp_offset():
    c = compile.Compiler()

    """
    #Expected
    ldfp #255
    ldsp #255
    call #7
    exit
    
    ldfp sp
    ret
    
    
    """

    code = c.build_single_main_function([])
    fp_offset = 255

    assert code == [
        # Init stack and frame
        default_config.ldfp.id, fp_offset,
        default_config.ldsp.id, fp_offset,
        # Call
        default_config.call_addr.id, 7,
        # on return
        default_config.exit.id,

        # start of function
        default_config.ldfp_sp.id,
        # frame size is 0
        default_config.ret.id,
    ]


def test_function_arguments():
    expected = """
    ldfp #255
    ldsp #255

    call #9
    exit
    
    # empty function test
    ldfp sp
    ret             
    
    # main function
    ldfp sp
     
    # make space for ret, not needed since == 0
    # addsp #0

    lda #5
    pusha
    call #7
    addsp #1
    ret

    """
    compiled_should_match_assembled(
        [ast.FunctionNode(name='test', arguments=[ast.PrimitiveAssignTarget('arg2')], body=[]),
         ast.FunctionNode(name='main', arguments=[],
                          body=[ast.CallNode('test', [ast.ConstantNode(5)])])
         ], expected)


def compiled_should_match_assembled(nodes, expected_assembly):
    expected_compiled = assembler.assemble_mnemonic_file(default_config.instructions, StringIO(expected_assembly))
    c = compile.Compiler()
    output = c.build_program(nodes)

    assert output == expected_compiled


def test_compile_while():
    expected = """
    ldfp #255
    ldsp #255

    call #7
    exit
    
    # main function
    ldfp sp
    lda #1
    tsta
    jmpz #18
    lda #5
    out
    jmp #8
    ret             
    
    
    """

    compiled_should_match_assembled([ast.FunctionNode(name='main', arguments=[], body=[
        ast.WhileNode(
            condition=ast.ConstantNode(1),
            body=[ast.PrintNode(ast.ConstantNode(5))])
    ])], expected)


def test_compile_if_else():
    expected = """
    ldfp #255
    ldsp #255

    call #7
    exit
    
    # main function
    ldfp sp
    lda #1
    tsta
    jmpz #18
    lda #5
    out
    jmp #21
    lda #4
    out
    
    ret
    """

    compiled_should_match_assembled([ast.FunctionNode(name='main', arguments=[], body=[
        ast.IfNode(
            condition=ast.ConstantNode(1),
            body=[ast.PrintNode(ast.ConstantNode(5))],
            else_body=[ast.PrintNode(ast.ConstantNode(4))]
        )

    ])], expected)


def test_if_no_else():
    expected = """
        ldfp #255
        ldsp #255

        call #7
        exit
        
        #main function
        ldfp sp
        lda #1
        tsta
        jmpz #16
        lda #5
        out

        ret
        """

    compiled_should_match_assembled([ast.FunctionNode(name='main', arguments=[], body=[
        ast.IfNode(
            condition=ast.ConstantNode(1),
            body=[ast.PrintNode(ast.ConstantNode(5))],
        )

    ])], expected)


def test_return_byte():
    expected = """
           ldfp #255
           ldsp #255

           call #17
           exit
           
           
           # test function
           ldfp sp
           lda #5
           out
           lda #10
           STA FP, #2
           ret
           ret
           
           
           # main function
           ldfp sp
           # make space for ret
           subsp #1
           call #7
           
           # pop return
           addsp #1
           
           ret
           """

    compiled_should_match_assembled([
        ast.FunctionNode(name='test', arguments=[], return_type='byte', body=[
            ast.PrintNode(ast.ConstantNode(5)),
            ast.AssignNode(target=ast.PrimitiveAssignTarget('result'), value=ast.ConstantNode(10)),
            ast.ReturnNode(),
        ]),
        ast.FunctionNode(name='main', arguments=[], body=[
            ast.CallNode(target_name='test', parameters=[])
        ]),
    ], expected)


def test_return_byte_and_assign():
    expected = """
           ldfp #255
           ldsp #255

           call #14
           exit


           # test function
           ldfp sp
           lda #10
           STA FP, #2
           ret
           ret

           # main function
           subsp #1
           ldfp sp
           # make space for ret
           subsp #1
           call #7

           # pop return
           popa
           
           STA FP, #0
           LDA FP, #0
           out

           ret #1
           """

    compiled_should_match_assembled([
        ast.FunctionNode(name='test', arguments=[], return_type='byte', body=[
            ast.AssignNode(target=ast.PrimitiveAssignTarget('result'), value=ast.ConstantNode(10)),
            ast.ReturnNode(),
        ]),
        ast.FunctionNode(name='main', arguments=[], body=[
            ast.AssignNode(target=ast.PrimitiveAssignTarget('test'), value=ast.CallNode('test', parameters=[])),
            ast.PrintNode(target=ast.IdentifierNode('test'))
        ]),
    ], expected)


def test_build_struct():
    with pytest.raises(compile.CompileError) as e:
        c = compile.Compiler()
        c.build_struct(ast.StructNode('test', members=[ast.PrimitiveAssignTarget('target', type='invalid')]))
    assert 'No type with name' in str(e)

    s = c.build_struct(ast.StructNode('test', members=[ast.PrimitiveAssignTarget('field1', type='byte')]))
    assert s.name == 'test'
    assert s.size == 1

    s = c.build_struct(ast.StructNode('test', members=[ast.PrimitiveAssignTarget('field1', type='byte'),
                                                       ast.PrimitiveAssignTarget('field2', type='byte')]))
    assert s.name == 'test'
    assert s.size == 2
