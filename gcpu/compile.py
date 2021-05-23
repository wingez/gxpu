import subprocess
from typing import List
from pathlib import Path

from . import ast


class AssemblyFunction:
    def __init__(self):
        self.result: List[str] = []

    def generate(self, line: str):
        self.result.append(line)

    def compile_and_run(self, nodes: List[ast.AstNode]):
        assert len(nodes) == 1

        node = nodes[0]

        assert isinstance(node, ast.AssignmentNode)

        available_registers = ['r3', 'r2']

        register = available_registers.pop()

        self.generate(f'mov {register}, #{node.value}')

        self.generate(f'mov r0, {register}')
        self.generate(f'bl print_r0')


def compile_and_run(nodes):
    compiler = AssemblyFunction()
    compiler.compile_and_run(nodes)

    base_dir = Path(__file__).parent

    file = base_dir / 'output.asm'
    output_file = base_dir / 'asm32'

    function_output = ''
    for line in compiler.result:
        function_output += line + '\n'

    with open(file, mode='w+') as f:
        f.write(f"""
    .section .data

    c: .SPACE 4

    array: .SPACE 100
    tmp_array: .SPACE 100
    reverse_array: .SPACE 100

    .section .text

    .global _start

    _start:
    {function_output}
    bl exit

    mov r0, #'H'
    bl print_r0
    mov r0, #'\n'
    bl print_r0
    bl exit



    print_r0:
    ldr r1, =tmp_array
    strb r0,[r1]
    /* syscall write(int fd, const void *buf, size_t count)*/
    mov r0, #1
    ldr r1, =tmp_array
    mov r2, #1
    mov r7, #4
    svc #0
    bx lr

    exit:
    /*syscall exit(int status)*/
    mov r0,#0
    mov r7,#1
    svc #0
            """)

    subprocess.run(
        f"arm-linux-gnueabihf-as {file} -o {output_file}.o && arm-linux-gnueabihf-ld -static {output_file}.o -o {output_file}",
        shell=True
    ).check_returncode()

    result = subprocess.run(f'{output_file}', capture_output=True)
    result.check_returncode()

    print(repr(result.stdout))

    return result.stdout
