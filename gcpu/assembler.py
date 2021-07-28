from typing import List, TextIO

from gcpu import utils
from gcpu.emulator import InstructionSet, MNEMONIC_DELIMITERS, InstructionBuilderError


def assemble_mnemonic(instruction_set: InstructionSet, mnemonic: str) -> List[int]:
    def parse_variable(text: str) -> int:
        return int(text)

    for instr in instruction_set.instruction_by_index.values():

        variables = {}

        template_split = utils.split_many(instr.mnemonic, MNEMONIC_DELIMITERS)
        mnem_split = utils.split_many(mnemonic, MNEMONIC_DELIMITERS)
        # Filter extra spaces
        mnem_split = [word for word in mnem_split if len(word) > 0]

        if len(template_split) != len(mnem_split):
            continue

        for template_word, mnem_word in zip(template_split, mnem_split):
            if '#' in template_word and '#' in mnem_word:
                variables[template_word[1:]] = parse_variable(mnem_word[1:])
            elif not template_word == mnem_word:
                break
        else:
            # All found!
            return instr.build(**variables)

    raise InstructionBuilderError(f'No instruction matches {mnemonic!r}')


def assemble_mnemonic_file(instruction_set: InstructionSet, file: TextIO) -> List[int]:
    result = []
    for line in file:
        result.extend(assemble_mnemonic(instruction_set, line))
    return result
