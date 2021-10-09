from io import StringIO

import pytest

from gcpu import emulator, assembler
from gcpu.emulator import InvalidInstructionError
from gcpu.instructions import InstructionSet
from gcpu import default_config


@pytest.fixture
def instruction_set():
    i = InstructionSet()

    @i.create_instruction('invalid', index=0)
    def invalid(emulator):
        raise InvalidInstructionError

    @i.create_instruction('exit', index=1)
    def _exit(emulator):
        return True

    @i.create_instruction('print', index=2)
    def _print(emulator):
        emulator.print(emulator.a)

    @i.create_instruction('LDA #val', index=3)
    def lda(emulator):
        emulator.a = emulator.get_and_inc_pc()

    return i


def test_memory_access(monkeypatch, instruction_set):
    monkeypatch.setattr(emulator, 'MEMORY_SIZE', 4)

    e = emulator.Emulator(instruction_set)
    assert len(e._memory) == 4

    # check memory to large
    with pytest.raises(emulator.EmulatorRuntimeError):
        e.set_all_memory(list(range(5)))
    # check not byte
    with pytest.raises(emulator.EmulatorRuntimeError):
        e.set_all_memory([256])
    with pytest.raises(emulator.EmulatorRuntimeError):
        e.set_all_memory([-1])

    # check valid memory contents
    e.set_all_memory([0, 255])

    # check read outside bounds
    with pytest.raises(emulator.EmulatorRuntimeError):
        e.get_memory_at(4)


def test_pc(monkeypatch, instruction_set):
    monkeypatch.setattr(emulator, 'MEMORY_SIZE', 4)
    e = emulator.Emulator(instruction_set)
    e.set_all_memory([0, 1, 2, 3])
    assert e.pc == 0
    for i in range(4):
        assert e.get_and_inc_pc() == i
    with pytest.raises(emulator.EmulatorRuntimeError):
        e.get_and_inc_pc()


@pytest.mark.skipif(True, reason='not implemented yet')
def test_pc_loop_around(instruction_set):
    e = emulator.Emulator(instruction_set)

    for i in range(emulator.MEMORY_SIZE + 10):
        e.get_and_inc_pc()


@pytest.mark.skipif(True, reason='not implemented yet')
def test_a_loop_around(instruction_set):
    e = emulator.Emulator(instruction_set)

    # broken atm
    for i in range(emulator.MEMORY_SIZE + 10):
        e.get_and_inc_pc()


def test_execution_cycles_exceeded(instruction_set):
    e = emulator.Emulator(instruction_set)

    # Exit
    e.set_all_memory([
        # 4x Load A, #0
        3, 0, 3, 0, 3, 0, 3, 0,
        # Exit
        1,
    ])
    e.run()
    e.reset()

    e.run(max_clock_cycles=5)
    e.reset()

    with pytest.raises(emulator.ExecutionCyclesExceededError):
        e.run(max_clock_cycles=4)


def test_execution():
    e = default_config.DefaultEmulator()

    def build_and_run(instructions):
        r = []
        for i in instructions:
            r.extend(i)
        e.reset()
        e.get_output()
        e.set_all_memory(r)
        e.run()
        return e.get_output()

    assert build_and_run([default_config.print.build(),
                          default_config.exit.build()]) == bytes([0])

    assert build_and_run([default_config.print.build(),
                          default_config.lda.build(val=10),
                          default_config.print.build(),
                          default_config.exit.build()]) == bytes([0, 10])


def assemble_load_emulator(code):
    assembled = assembler.assemble_mnemonic_file(default_config.instructions, StringIO(code))

    e = default_config.DefaultEmulator()
    e.set_all_memory(assembled)
    return e


def test_call():
    program = """
    LDSP #25
    LDFP #25
    
    CALL #7
    invalid
    EXIT
    
    """
    e = assemble_load_emulator(program)
    e.run()

    assert e.get_memory_at(25 - 1) == 25
    assert e.get_memory_at(25 - 2) == 6
    assert e.fp == 25
    assert e.sp == 23


def test_call_and_ret():
    program = """
    LDSP #25
    LDFP #25
    
    LDA #1
    OUT
    
    CALL #13
    LDA #3
    OUT
    EXIT
    
    ldfp sp
    LDA #2
    OUT
    RET
    
    """

    e = assemble_load_emulator(program)
    e.run()

    assert e.get_output() == bytes([1, 2, 3])
    assert e.fp == 25
    assert e.sp == 25


def test_jump():
    program = """
    
    lda #5
    out
    jmp #7
    lda #7
    
    out
    exit
    
    """
    e = assemble_load_emulator(program)

    e.run()
    assert e.get_output() == bytes([5, 5])


def test_jump_loop_infinite():
    program = """
    lda #5
    
    jmp #0
    exit
    """
    e = assemble_load_emulator(program)

    with pytest.raises(emulator.ExecutionCyclesExceededError):
        e.run()


def test_zero_flag():
    program = """
    lda #1
    tsta
    exit
    """
    e = assemble_load_emulator(program)
    e.run()
    assert e.zero_flag == False
    e = assemble_load_emulator("""
    lda #0
    tsta
    exit
    """)
    e.run()
    assert e.zero_flag == True


def test_jump_if_zero():
    program = """
    lda #1
    tsta
    jmpz #6
    out
    lda #0
    tsta
    jmpz #12
    out
    
    exit
    """
    e = assemble_load_emulator(program)
    e.run()
    assert e.get_output() == bytes([1])
