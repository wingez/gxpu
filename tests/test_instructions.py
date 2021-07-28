import pytest

from gcpu.emulator import InstructionSet, Instruction, AUTO_INDEX_ASSIGMENT, RegisterInstructionError, \
    InstructionBuilderError


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

    i = Instruction('test #a, #b', emulate=None, id=8)
    with pytest.raises(InstructionBuilderError):
        i.build(a=6)
    with pytest.raises(InstructionBuilderError):
        i.build(a=6, b=4, c=10)
