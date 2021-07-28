from __future__ import annotations

import sys
from dataclasses import dataclass, field
from io import BytesIO
from typing import List, Callable, Dict

from gcpu import utils

MEMORY_SIZE = 2 ** 7
AUTO_INDEX_ASSIGMENT = -1


class EmulatorRuntimeError(Exception): pass


class ExecutionCyclesExceededError(EmulatorRuntimeError): pass


class InvalidInstructionError(EmulatorRuntimeError): pass


class RegisterInstructionError(Exception): pass


class InstructionBuilderError(Exception): pass


@dataclass
class Instruction:
    mnemonic: str

    emulate: Callable[[Emulator], bool]
    variable_order: List[str] = field(default_factory=list)
    id: int = AUTO_INDEX_ASSIGMENT
    name: str = ''
    doc_str: str = ''

    def __post_init__(self):
        words = utils.split_many(self.mnemonic, [' ', ','])

        if not self.name:
            self.name = words[0]

        variables = [var.lstrip('#') for var in words if var.startswith('#')]

        if self.variable_order:
            # Check so the user provided all necessary variables
            if set(self.variable_order) != set(variables):
                raise RegisterInstructionError(f'Variables_order should contain: {repr(set(variables))}')
        else:
            self.variable_order = variables

    @property
    def size(self):
        # 1 for id and add one for each variable
        return 1 + len(self.variable_order)

    def build(self, **kwargs: int):
        result = [self.id]
        for variable_name in self.variable_order:
            if variable_name not in kwargs:
                raise InstructionBuilderError(f'A variable with name {variable_name} is required')
            var = kwargs.pop(variable_name)
            result.append(var)
        if kwargs:
            raise InstructionBuilderError(f'Instruction {self.mnemonic} does not take variables {kwargs}')
        return result


class InstructionSet:

    def __init__(self, max_size: int = 256):
        self._max_size = max_size
        self._instruction_by_index: Dict[int, Instruction] = {}

    def _next_vacant_index(self):
        for i in range(self._max_size):
            if i not in self._instruction_by_index:
                return i

        raise ValueError('Maximum number of instructions reached')

    def add_instruction(self, instruction: Instruction):
        # assign id
        if instruction.id == AUTO_INDEX_ASSIGMENT:
            instruction.id = self._next_vacant_index()

        if instruction.id in self._instruction_by_index:
            raise ValueError(f'An instruction with if {instruction.id} already exists')
        if instruction.id >= self._max_size:
            raise ValueError(f'Instruction already at max capacity')

        self._instruction_by_index[instruction.id] = instruction

    def create_instruction(self, mnemonic: str, index: int = AUTO_INDEX_ASSIGMENT, name: str = None) -> Callable[
        [Callable], Instruction]:
        def decorator(func: Callable[[Emulator], bool]) -> Instruction:
            instruction_name = name if name is not None else func.__name__
            instruction_doc = func.__doc__ if func.__doc__ else 'missing'

            result = Instruction(
                mnemonic,
                name=instruction_name,
                doc_str=instruction_doc,
                id=index,
                emulate=func
            )

            self.add_instruction(result)
            return result

        return decorator

    def __getitem__(self, item: int) -> Instruction:
        if item not in self._instruction_by_index:
            raise InvalidInstructionError(item)
        return self._instruction_by_index[item]


class Emulator:

    def __init__(self, instruction_set: InstructionSet):

        self._output_stream = BytesIO()
        self._instruction_set = instruction_set

        self._memory: List[int] = [0] * MEMORY_SIZE
        self._a = 0
        self._pc = 0

        self.reset()

    def reset(self):
        self._a = 0
        self._pc = 0

    def clear_memory(self):
        for i in range(MEMORY_SIZE):
            self._memory[i] = 0

    @property
    def _a_upper(self) -> int:
        return (self._a & 0xff00) >> 8

    @property
    def _a_lower(self) -> int:
        return self._a & 0x00ff

    def set_all_memory(self, contents: List[int]):
        if len(contents) > MEMORY_SIZE:
            raise EmulatorRuntimeError('Memory this large not supported yet')
        if not all(byte in range(0, 0x100) for byte in contents):
            raise EmulatorRuntimeError('Contents contains value not in 0-0xff')

        self.clear_memory()
        for index, val in enumerate(contents):
            self._memory[index] = val

    def get_memory_at(self, position: int) -> int:
        if position >= MEMORY_SIZE:
            raise EmulatorRuntimeError(f'Trying to access memory at {hex(position)} which is outside memory range')
        return self._memory[position]

    def _get_and_inc_pc(self) -> int:
        val = self.get_memory_at(self._pc)
        self._pc += 1
        return val

    def print(self, byte: int):
        self._output_stream.write(byte.to_bytes(1, sys.byteorder))
        self._output_stream.flush()

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
        ins = self._instruction_set[self._get_and_inc_pc()]
        return ins.emulate(self)  # type: ignore

    def run(self, max_clock_cycles: int = 1_000):
        for clock_counter in range(max_clock_cycles):
            should_terminate = self.step_single_instruction()
            if should_terminate:
                break

        else:
            raise ExecutionCyclesExceededError('Maximum execution cycles exceeded, stuck in infinite loop perhaps?')
