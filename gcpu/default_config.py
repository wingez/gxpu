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
    emulator.print(emulator.a)
    return False


@instructions.create_instruction('LDA #val')
def lda(emulator):
    emulator.a = emulator.get_and_inc_pc()
    return False


@instructions.create_instruction('LDFP #val')
def ldfp(emulator):
    emulator.fp = emulator.get_and_inc_pc()
    return False


@instructions.create_instruction('LDA FP, #offset')
def lda_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a = emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('STA FP, #offset')
def sta_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.set_memory_at(emulator.fp + offset, emulator.a_lower)


@instructions.create_instruction('ADDA #val')
def adda(emulator):
    val = emulator.get_and_inc_pc()
    emulator.a += val


@instructions.create_instruction('ADDA FP, #offset')
def adda_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a += emulator.get_memory_at(emulator.fp + offset)


@instructions.create_instruction('SUBA #val')
def suba(emulator):
    val = emulator.get_and_inc_pc()
    emulator.a -= val


@instructions.create_instruction('SUBA FP, #offset')
def suba_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a -= emulator.get_memory_at(emulator.fp + offset)
