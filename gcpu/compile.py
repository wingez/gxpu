import subprocess
from typing import List, Dict
from pathlib import Path

from . import ast


class CompileError(Exception):
    pass


class AssemblyFunction:
    def __init__(self):
        self.result: List[str] = []
        self.register_mapping: Dict[str, str] = {}
        self.available_registers = ['r3', 'r2']

    def generate(self, line: str):
        self.result.append(line)

    def get_register_to_use(self, indentifier):
        if len(self.available_registers) == 0:
            return CompileError('No register to use')
        register = self.available_registers.pop()
        self.register_mapping[indentifier] = register
        return register

    def compile_and_run(self, nodes: List[ast.AstNode]):
        for node in nodes:

            if isinstance(node, ast.AssignmentNode):
                register = self.get_register_to_use(node.target)
                self.generate(f'mov {register}, #{node.value}')
            elif isinstance(node, ast.PrintNode):
                register_to_print = self.register_mapping[node.target]
                self.generate(f'mov r0,{register_to_print}')
                self.generate(f'bl print_r0')

            else:
                raise CompileError(f"Dont know how to parse: {node}")


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
