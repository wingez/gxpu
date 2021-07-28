from gcpu import compile, ast, default_config


def test_fp_offset():
    c = compile.Compiler()

    code = c.build_function([])
    fp_offset = 3  # 1 for ldfp instr, 1 for ldfp offset, 1 for exit()

    assert code == [default_config.ldfp.id, fp_offset, default_config.exit.id]
