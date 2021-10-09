from typing import List, TextIO

from gcpu import utils
from gcpu.instructions import MNEMONIC_DELIMITERS, InstructionBuilderError, InstructionSet


def assemble_mnemonic(instruction_set: InstructionSet, mnemonic: str) -> List[int]:
    mnemonic = mnemonic.strip(' ')

    if mnemonic == '' or mnemonic.startswith('#'):
        return []

    def parse_variable(text: str) -> int:
        return int(text)

    for instr in instruction_set.get_instructions():

        variables = {}

        template_split = utils.split_many(instr.mnemonic, MNEMONIC_DELIMITERS)
        mnem_split = utils.split_many(mnemonic, MNEMONIC_DELIMITERS)
        # Filter extra spaces
        template_split = [word for word in template_split if len(word) > 0]
        mnem_split = [word for word in mnem_split if len(word) > 0]

        if len(template_split) != len(mnem_split):
            continue

        for template_word, mnem_word in zip(template_split, mnem_split):
            if '#' in template_word and '#' in mnem_word:

                index_of = template_word.index('#')
                if index_of != mnem_word.index('#'):
                    break
                if not template_word.lower()[:index_of] == mnem_word.lower()[:index_of]:
                    break

                variables[template_word[index_of + 1:]] = parse_variable(mnem_word[index_of + 1:])
            elif not template_word.lower() == mnem_word.lower():
                break
        else:
            # All found!
            return instr.build(**variables)

    raise InstructionBuilderError(f'No instruction matches {mnemonic!r}')


def assemble_mnemonic_file(instruction_set: InstructionSet, file: TextIO) -> List[int]:
    result = []
    for line in file:
        line = line.strip('\n')
        result.extend(assemble_mnemonic(instruction_set, line))
    return result


def disassemble(instruction_set: InstructionSet, code: List[int]) -> List[str]:
    index = 0
    result = []

    while index < len(code):
        instruction_id = code[index]
        instr = instruction_set[instruction_id]

        out = instr.mnemonic

        for param in instr.variable_order:
            val = code[index + 1 + instr.get_position_of_variable(param)]
            out = out.replace(f'#{param}', f'#{val}')

        index += instr.size
        result.append(out)

    return result
