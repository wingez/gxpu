import subprocess
from typing import List, Dict
from pathlib import Path

from . import ast


class CompileError(Exception):
    pass


class AssemblyFunction:
    def __init__(self):
        self._function_body: List[str] = []
        self._current_stack_size = 0

        self.register_stack_mapping: Dict[str, int] = {}

    def body_generate(self, line: str):
        self._function_body.append(line)

    def generate_header(self) -> List[str]:
        return [
            'mov r10, sp',
        ]

    def compile_and_run(self, nodes: List[ast.AstNode]):

        for node in nodes:

            if isinstance(node, ast.AssignmentNode):

                if node.target not in self.register_stack_mapping:
                    self._current_stack_size += 4
                    self.register_stack_mapping[node.target] = self._current_stack_size

                offset = -self.register_stack_mapping[node.target]

                self.body_generate(f'mov r0, #{node.value_node}')
                self.body_generate(f'str r0, [r10], #{offset}')

            elif isinstance(node, ast.PrintNode):
                offset = -self.register_stack_mapping[node.target]
                self.body_generate(f'ldr r0, [r10], #{offset}')
                self.body_generate(f'bl print_r0')

            else:
                raise CompileError(f"Dont know how to parse: {node}")

        self.body_generate(f'bl exit')

    def get_body(self):
        return self.generate_header() + self._function_body


def compile_and_run(nodes):
    compiler = AssemblyFunction()
    compiler.compile_and_run(nodes)

    base_dir = Path(__file__).parent

    file = base_dir / 'output.asm'
    output_file = base_dir / 'asm32'

    function_output = ''
    for line in compiler.get_body():
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
    /*ldr sp, =stack_upper*/
    
    {function_output}
    
    mov r0, #'H'
    push {{r0}}
    mov r0, #'G'
    pop {{r0}}
    bl print_r0
    
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
    
    .ALIGN 4
    stack_lower: .SPACE 1000
    stack_upper=.
    
    
            """)

    subprocess.run(
        f"arm-linux-gnueabihf-as {file} -o {output_file}.o && arm-linux-gnueabihf-ld -static {output_file}.o -o {output_file}",
        shell=True
    ).check_returncode()

    result = subprocess.run(f'{output_file}', capture_output=True)
    result.check_returncode()

    print(repr(result.stdout))

    return result.stdout
