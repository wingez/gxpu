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


@instructions.create_instruction('EXIT')
def exit(emulator):
    return True


@instructions.create_instruction('OUT')
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


@instructions.create_instruction('LDSP #val')
def ldsp(emulator):
    emulator.sp = emulator.get_and_inc_pc()
    return False


@instructions.create_instruction('LDA FP, #offset')
def lda_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a = emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('LDA FP, -#offset')
def lda_fp_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a = emulator.get_memory_at(emulator.fp - offset)
    return False


@instructions.create_instruction('STA FP, #offset')
def sta_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.set_memory_at(emulator.fp + offset, emulator.a_lower)
    return False


@instructions.create_instruction('ADDA #val')
def adda(emulator):
    val = emulator.get_and_inc_pc()
    emulator.a += val
    return False


@instructions.create_instruction('ADDA FP, #offset')
def adda_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a += emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('ADDA FP, -#offset')
def adda_fp_offset_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a += emulator.get_memory_at(emulator.fp - offset)


@instructions.create_instruction('ADDSP #val')
def addsp(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.sp += offset
    return False


@instructions.create_instruction('SUBA #val')
def suba(emulator):
    val = emulator.get_and_inc_pc()
    emulator.a -= val
    return False


@instructions.create_instruction('SUBA FP, #offset')
def suba_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a -= emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('SUBA FP, -#offset')
def suba_fp_offset_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a -= emulator.get_memory_at(emulator.fp - offset)
    return False


@instructions.create_instruction('CALL #addr')
def call_addr(emulator):
    addr = emulator.get_and_inc_pc()
    emulator.push(emulator.fp)
    emulator.push(emulator.pc)

    emulator.fp = emulator.sp

    emulator.pc = addr
    return False


@instructions.create_instruction('RET')
def ret(emulator):
    emulator.sp = emulator.fp

    emulator.pc = emulator.pop()
    emulator.fp = emulator.pop()

    return False


@instructions.create_instruction('PUSHA')
def push_a(emulator):
    emulator.push(emulator.a)
    return False


@instructions.create_instruction('SUBSP #val')
def sub_sp(emulator):
    val = emulator.get_and_inc_pc()
    emulator.sp -= val
    return False


@instructions.create_instruction('JMP #addr')
def jump(emulator):
    addr = emulator.get_memory_at(emulator.pc)
    emulator.pc = addr
    return False
