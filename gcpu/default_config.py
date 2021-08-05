from gcpu.emulator import Emulator, InvalidInstructionError
from gcpu.instructions import InstructionSet

LOAD_STORE = 'Load / Store'
OPERATION = 'Operation on A-Register'
STACK = 'Stack / Frame operations'
CONTROL = 'Flow Control'
MISC = 'Miscellaneous'

instructions = InstructionSet()


class DefaultEmulator(Emulator):
    def __init__(self):
        super(DefaultEmulator, self).__init__(
            instruction_set=instructions,
        )


@instructions.create_instruction('invalid', index=0, group=MISC)
def invalid(emulator):
    raise InvalidInstructionError()


@instructions.create_instruction('EXIT', group=MISC)
def exit(emulator):
    return True


@instructions.create_instruction('OUT', group=MISC)
def print(emulator):
    emulator.print(emulator.a)
    return False


@instructions.create_instruction('LDA #val', group=LOAD_STORE)
def lda(emulator):
    emulator.a = emulator.get_and_inc_pc()
    return False


@instructions.create_instruction('LDFP #val', group=STACK)
def ldfp(emulator):
    emulator.fp = emulator.get_and_inc_pc()
    return False


@instructions.create_instruction('LDSP #val', group=STACK)
def ldsp(emulator):
    emulator.sp = emulator.get_and_inc_pc()
    return False


@instructions.create_instruction('LDA FP, #offset', group=LOAD_STORE)
def lda_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a = emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('LDA FP, -#offset', group=LOAD_STORE)
def lda_fp_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a = emulator.get_memory_at(emulator.fp - offset)
    return False


@instructions.create_instruction('STA FP, #offset', group=LOAD_STORE)
def sta_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.set_memory_at(emulator.fp + offset, emulator.a_lower)
    return False


@instructions.create_instruction('STA FP, -#offset', group=LOAD_STORE)
def sta_fp_offset_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.set_memory_at(emulator.fp - offset, emulator.a_lower)
    return False


@instructions.create_instruction('ADDA #val', group=OPERATION)
def adda(emulator):
    val = emulator.get_and_inc_pc()
    emulator.a += val
    return False


@instructions.create_instruction('ADDA FP, #offset', group=OPERATION)
def adda_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a += emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('ADDA FP, -#offset', group=OPERATION)
def adda_fp_offset_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a += emulator.get_memory_at(emulator.fp - offset)


@instructions.create_instruction('ADDSP #val', group=OPERATION)
def addsp(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.sp += offset
    return False


@instructions.create_instruction('SUBA #val', group=OPERATION)
def suba(emulator):
    val = emulator.get_and_inc_pc()
    emulator.a -= val
    return False


@instructions.create_instruction('SUBA FP, #offset', group=OPERATION)
def suba_fp_offset(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a -= emulator.get_memory_at(emulator.fp + offset)
    return False


@instructions.create_instruction('SUBA FP, -#offset', group=OPERATION)
def suba_fp_offset_negative(emulator):
    offset = emulator.get_and_inc_pc()
    emulator.a -= emulator.get_memory_at(emulator.fp - offset)
    return False


@instructions.create_instruction('CALL #addr', group=CONTROL)
def call_addr(emulator):
    addr = emulator.get_and_inc_pc()
    emulator.push(emulator.fp)
    emulator.push(emulator.pc)

    emulator.fp = emulator.sp

    emulator.pc = addr
    return False


@instructions.create_instruction('RET', group=CONTROL)
def ret(emulator):
    emulator.sp = emulator.fp

    emulator.pc = emulator.pop()
    emulator.fp = emulator.pop()

    return False


@instructions.create_instruction('PUSHA', group=STACK)
def push_a(emulator):
    emulator.push(emulator.a)
    return False


@instructions.create_instruction('POPA', group=STACK)
def pop_a(emulator):
    emulator.a = emulator.pop()
    return False


@instructions.create_instruction('SUBSP #val', group=STACK)
def sub_sp(emulator):
    val = emulator.get_and_inc_pc()
    emulator.sp -= val
    return False


@instructions.create_instruction('JMP #addr', group=CONTROL)
def jump(emulator):
    addr = emulator.get_memory_at(emulator.pc)
    emulator.pc = addr
    return False


@instructions.create_instruction('TSTA', group=CONTROL)
def test_a(emulator):
    emulator.zero_flag = emulator.a == 0


@instructions.create_instruction('JMPZ #addr', group=CONTROL)
def jump_if_zero(emulator):
    addr = emulator.get_and_inc_pc()
    if emulator.zero_flag:
        emulator.pc = addr


if __name__ == '__main__':
    instructions.print_all_instructions()
