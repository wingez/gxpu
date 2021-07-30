from gcpu import compile, ast, default_config


def test_fp_offset():
    c = compile.Compiler()

    """
    #Expected
    ldfp 8
    ldsp 8
    call 7
    exit
    
    ret
    
    
    """

    code = c.build_single_main_function([])
    fp_offset = 8  # 1 for ldfp instr, 1 for ldfp offset, 1 for exit()

    assert code == [default_config.ldfp.id, fp_offset,
                    default_config.ldsp.id, fp_offset,
                    default_config.call_addr.id, 7,
                    default_config.exit.id,
                    default_config.ret.id]
