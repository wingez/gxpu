import pytest

from gcpu.instructions import AUTO_INDEX_ASSIGMENT, RegisterInstructionError, InstructionBuilderError, Instruction, \
    InstructionSet
from gcpu.assembler import assemble_mnemonic, disassemble


def test_auto_id():
    i = InstructionSet(max_size=3)

    def create_dummy(index):
        return Instruction('dummy', id=index, emulate=lambda x: x, )

    i1 = create_dummy(1)
    i0 = create_dummy(0)
    i.add_instruction(i1)

    iauto0 = create_dummy(AUTO_INDEX_ASSIGMENT)
    iauto2 = create_dummy(AUTO_INDEX_ASSIGMENT)
    iauto3 = create_dummy(AUTO_INDEX_ASSIGMENT)
    i.add_instruction(iauto0)
    i.add_instruction(iauto2)

    assert i1.id == 1
    assert iauto0.id == 0
    assert iauto2.id == 2

    with pytest.raises(ValueError):
        i.add_instruction(iauto3)

    with pytest.raises(ValueError):
        i.add_instruction(i0)


def test_mnemonic():
    assert Instruction('mnem', emulate=None).name == 'mnem'
    assert Instruction('mnem', emulate=None, name='test').name == 'test'
    assert Instruction('lda #test', None).name == 'lda'


def test_variables():
    assert Instruction('mnem', emulate=None).variable_order == []
    assert Instruction('lda hello', emulate=None).variable_order == []
    assert Instruction('lda sp,#var', emulate=None).variable_order == ['var']
    assert Instruction('test #a, #b', emulate=None).variable_order == ['a', 'b']

    assert Instruction('test #a, #b', emulate=None, variable_order=['b', 'a']).variable_order == ['b', 'a']
    with pytest.raises(RegisterInstructionError) as e:
        assert Instruction('test #a, #b', emulate=None, variable_order=['b', 'aa']).variable_order == ['b', 'a']
    assert repr({'a', 'b'}) in str(e.value)


def test_build():
    i = Instruction('test', emulate=None, id=0)
    assert i.build() == [0]

    i = Instruction('test #test', emulate=None, id=5)
    assert i.build(test=6) == [5, 6]

    i = Instruction('test #a, #b', emulate=None, variable_order=['b', 'a'], id=7)
    assert i.build(a=2, b=3) == [7, 3, 2]

    i = Instruction('test -#a', emulate=None)
    assert i.variable_order == ['a']

    i = Instruction('test #a, #b', emulate=None, id=8)
    with pytest.raises(InstructionBuilderError):
        i.build(a=6)
    with pytest.raises(InstructionBuilderError):
        i.build(a=6, b=4, c=10)


def test_assemble_mnemonic():
    i = InstructionSet()
    i.add_instruction(Instruction('test', emulate=None, id=0))
    i.add_instruction(Instruction('test #ins #tmp', emulate=None, id=2))
    i.add_instruction(Instruction('test #ins', emulate=None, id=1))

    assert assemble_mnemonic(i, 'test') == [0]
    assert assemble_mnemonic(i, 'test #4') == [1, 4]

    with pytest.raises(InstructionBuilderError):
        assert assemble_mnemonic(i, 'test 4')

    assert assemble_mnemonic(i, 'test #5 #6') == [2, 5, 6]
    assert assemble_mnemonic(i, 'test    #5   #6   ') == [2, 5, 6]

    assert assemble_mnemonic(i, '   ') == []


def test_assemble_mnemonic_case_invariance():
    i = InstructionSet()
    i.add_instruction(Instruction('test #ins', emulate=None, id=1))
    i.add_instruction(Instruction('TEst2 #ins', emulate=None, id=2))

    assert assemble_mnemonic(i, 'test #0') == [1, 0]
    assert assemble_mnemonic(i, 'TesT #0') == [1, 0]
    assert assemble_mnemonic(i, 'test2 #0') == [2, 0]


def test_assemble_negative_symbol():
    i = InstructionSet()
    i.add_instruction(Instruction('sta fp, #offset', emulate=None, id=1))
    i.add_instruction(Instruction('sta fp, -#offset', emulate=None, id=2))

    assert assemble_mnemonic(i, 'sta fp, #5') == [1, 5]
    assert assemble_mnemonic(i, 'sta fp, -#10') == [2, 10]


def test_disassemble():
    i = InstructionSet()
    i.add_instruction(Instruction('test #ins', emulate=None, id=1))
    i.add_instruction(Instruction('TEst2 #ins #asd', emulate=None, id=2))
    i.add_instruction(Instruction('second', emulate=None, id=3))

    code = [
        1, 15,
        3,
        3,
        2, 6, 3,
        1, 14
    ]

    expected = [
        'test #15',
        'second',
        'second',
        'TEst2 #6 #3',
        'test #14',
    ]

    assert disassemble(i, code) == expected


def test_print_instructions(capsys):
    i = InstructionSet()
    i.add_instruction(Instruction('test', emulate=None, id=0, group='group1'))
    i.add_instruction(Instruction('test #ins #tmp', emulate=None, id=1, group='group2'))
    i.add_instruction(Instruction('test #ins', emulate=None, id=2, group='group1'))
    i.print_all_instructions()
    captured = capsys.readouterr()
    assert captured.out == "Group: group1\n" \
                           "  0: test\n" \
                           "  2: test #ins\n" \
                           "Group: group2\n" \
                           "  1: test #ins #tmp\n"


def test_print_instructions_no_group_provided(capsys):
    # Test no group set
    i = InstructionSet()
    i.add_instruction(Instruction('test', emulate=None, id=5, ))
    i.print_all_instructions()
    assert capsys.readouterr().out == "Group: not set\n" \
                                      "  5: test\n"
