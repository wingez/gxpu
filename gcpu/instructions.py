from typing import Callable, List, Dict, Any
from dataclasses import dataclass, field
import itertools as it

from gcpu import utils

AUTO_INDEX_ASSIGMENT = -1
MNEMONIC_DELIMITERS = [' ', ',']

# Should take an emulator as argument but to avoid circular import
FUNCTION_EMULATE = Callable[[Any], bool]


class RegisterInstructionError(Exception): pass


class InstructionBuilderError(Exception): pass


@dataclass
class Instruction:
    mnemonic: str

    emulate: FUNCTION_EMULATE
    variable_order: List[str] = field(default_factory=list)
    id: int = AUTO_INDEX_ASSIGMENT
    name: str = ''
    doc_str: str = ''
    group: str = ''

    def __post_init__(self):
        words = utils.split_many(self.mnemonic, MNEMONIC_DELIMITERS)

        if not self.name:
            self.name = words[0]

        variables = [var.lstrip('#-') for var in words if '#' in var]

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

    def get_position_of_variable(self, variable: str) -> int:
        if variable not in self.variable_order:
            raise InstructionBuilderError(f'Variable {variable} not part of this instruction')
        return self.variable_order.index(variable)


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
            raise ValueError('Instruction already at max capacity')

        self._instruction_by_index[instruction.id] = instruction

    def create_instruction(self, mnemonic: str, index: int = AUTO_INDEX_ASSIGMENT, name: str = None, group: str = '') \
            -> Callable[[FUNCTION_EMULATE], Instruction]:
        def decorator(func: FUNCTION_EMULATE) -> Instruction:
            instruction_name = name if name is not None else func.__name__
            instruction_doc = func.__doc__ if func.__doc__ else 'missing'

            result = Instruction(
                mnemonic,
                name=instruction_name,
                doc_str=instruction_doc,
                id=index,
                emulate=func,
                group=group,
            )

            self.add_instruction(result)
            return result

        return decorator

    def get_instructions(self) -> List[Instruction]:
        return list(self._instruction_by_index.values())

    def __getitem__(self, item: int) -> Instruction:
        if item not in self._instruction_by_index:
            raise InstructionBuilderError(item)
        return self._instruction_by_index[item]

    def print_all_instructions(self):
        instructions = self.get_instructions()

        group_key = lambda instruction: instruction.group

        instructions.sort(key=group_key)

        for group, instrs in it.groupby(instructions, group_key):
            if not group:
                group = "not set"

            print(f'Group: {group}')

            for i in sorted(instrs, key=lambda i: i.id):
                print(f'{i.id:3d}: {i.mnemonic}')
