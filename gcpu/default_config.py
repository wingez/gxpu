from io import BytesIO

from gcpu.emulator import InstructionSet, Emulator, InvalidInstructionError

instructions = InstructionSet()


class DefaultEmulator(Emulator):
    def __init__(self):
        super(DefaultEmulator, self).__init__(
            instruction_set=instructions,
        )


@instructions.create_instruction('invalid', index=0)
def invalid(emulator):
    raise InvalidInstructionError()


@instructions.create_instruction('exit')
def exit(emulator):
    return True


@instructions.create_instruction('print')
def print(emulator):
    emulator.print(emulator._a)
    return False


@instructions.create_instruction('LDA #val')
def lda(emulator):
    emulator._a = emulator._get_and_inc_pc()
    return False
