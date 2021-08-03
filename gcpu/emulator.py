from __future__ import annotations

import sys
from io import BytesIO
from typing import List, Dict

from gcpu import utils
from gcpu.instructions import InstructionSet

MEMORY_SIZE = 2 ** 7


class EmulatorRuntimeError(Exception): pass


class ExecutionCyclesExceededError(EmulatorRuntimeError): pass


class InvalidInstructionError(EmulatorRuntimeError): pass


class Emulator:

    def __init__(self, instruction_set: InstructionSet):

        self._output_stream = BytesIO()
        self._instruction_set = instruction_set

        self._memory: List[int] = [0] * MEMORY_SIZE
        self.a = 0
        self.pc = 0
        self.fp = 0
        self.sp = 0

        self.reset()

    def reset(self):
        self.a = 0
        self.pc = 0
        self.fp = 0
        self.sp = 0

    def clear_memory(self):
        for i in range(MEMORY_SIZE):
            self._memory[i] = 0

    @property
    def a_upper(self) -> int:
        return (self.a & 0xff00) >> 8

    @property
    def a_lower(self) -> int:
        return self.a & 0x00ff

    def set_all_memory(self, contents: List[int]):
        if len(contents) > MEMORY_SIZE:
            raise EmulatorRuntimeError('Memory this large not supported yet')
        if not all(byte in range(0, 0x100) for byte in contents):
            raise EmulatorRuntimeError('Contents contains value not in 0-0xff')

        self.clear_memory()
        for index, val in enumerate(contents):
            self._memory[index] = val

    def set_memory_at(self, position: int, value: int):
        if position >= MEMORY_SIZE:
            raise EmulatorRuntimeError(f'Trying to access memory at {hex(position)} which is outside memory range')
        self._memory[position] = value

    def get_memory_at(self, position: int) -> int:
        if position >= MEMORY_SIZE:
            raise EmulatorRuntimeError(f'Trying to access memory at {hex(position)} which is outside memory range')
        return self._memory[position]

    def get_and_inc_pc(self) -> int:
        val = self.get_memory_at(self.pc)
        self.pc += 1
        return val

    def print(self, byte: int):
        self._output_stream.write(byte.to_bytes(1, sys.byteorder))
        self._output_stream.flush()

    def push(self, byte: int):
        self.set_memory_at(self.sp, byte)
        self.sp += 1

    def pop(self) -> int:
        self.sp -= 1
        return self.get_memory_at(self.sp)

    def get_output(self, clear: bool = True) -> bytes:
        result = self._output_stream.getvalue()
        if clear:
            self._output_stream.truncate(0)
            self._output_stream.seek(0)
        return result

    def step_single_instruction(self) -> bool:
        """
        Runs a single instruction, return True if the instruction indicated the program should terminate, False otherwise
        :return:
        """
        ins = self._instruction_set[self.get_and_inc_pc()]
        return ins.emulate(self)  # type: ignore

    def run(self, max_clock_cycles: int = 1_000):
        for clock_counter in range(max_clock_cycles):
            should_terminate = self.step_single_instruction()
            if should_terminate:
                break

        else:
            raise ExecutionCyclesExceededError('Maximum execution cycles exceeded, stuck in infinite loop perhaps?')
