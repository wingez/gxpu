from io import StringIO

from gcpu import compile, ast, default_config, assembler


def test_fp_offset():
    c = compile.Compiler()

    """
    #Expected
    ldfp 10
    ldsp 10
    call 7
    exit
    
    ret
    
    
    """

    code = c.build_single_main_function([])
    fp_offset = 10

    assert code == [default_config.ldfp.id, fp_offset,
                    default_config.ldsp.id, fp_offset,
                    default_config.call_addr.id, 7,
                    default_config.exit.id,
                    default_config.addsp.id, 0,
                    default_config.ret.id]


def test_function_arguments():
    expected = """
    ldfp #22
    ldsp #22

    call #10
    exit
    
    addsp #0
    ret             
    
    addsp #0
    addsp #0

    lda #5
    pusha
    call #7
    subsp #1
    ret

    """
    compiled_should_match_assembled([ast.FunctionNode(name='test', arguments=['arg2'], body=[]),
                                     ast.FunctionNode(name='main', arguments=[],
                                                      body=[ast.CallNode('test', [ast.ConstantNode(5)])])
                                     ], expected)


def test_assembly_parameter_offset():
    f = compile.AssemblyFunction(name='func', args=['param1', 'param2'])
    assert f.frame_variables_offsets == {'param1': -4, 'param2': -3}


def compiled_should_match_assembled(nodes, expected_assembly):
    expected_compiled = assembler.assemble_mnemonic_file(default_config.instructions, StringIO(expected_assembly))
    c = compile.Compiler()
    output = c.build_program(nodes)

    assert output == expected_compiled


def test_compile_while():
    expected = """
    ldfp #15
    ldsp #15

    call #7
    exit
    
    addsp #0
    lda #5
    out
    jmp #9
    ret             
    
    
    """

    compiled_should_match_assembled([ast.FunctionNode(name='main', arguments=[], body=[
        ast.WhileNode([
            ast.PrintNode(ast.ConstantNode(5))

        ])
    ])], expected)
