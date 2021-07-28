from gcpu import emulator

import pytest

from gcpu.emulator import InstructionSet, InvalidInstructionError
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
        emulator.print(emulator._a)

    @i.create_instruction('LDA #val', index=3)
    def lda(emulator):
        emulator._a = emulator._get_and_inc_pc()

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
    assert e._pc == 0
    for i in range(4):
        assert e._get_and_inc_pc() == i
    with pytest.raises(emulator.EmulatorRuntimeError):
        e._get_and_inc_pc()


@pytest.mark.skipif(True, reason='not implemented yet')
def test_pc_loop_around(instruction_set):
    e = emulator.Emulator(instruction_set)

    for i in range(emulator.MEMORY_SIZE + 10):
        e._get_and_inc_pc()


@pytest.mark.skipif(True, reason='not implemented yet')
def test_a_loop_around(instruction_set):
    e = emulator.Emulator(instruction_set)

    # broken atm
    for i in range(emulator.MEMORY_SIZE + 10):
        e._get_and_inc_pc()


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
