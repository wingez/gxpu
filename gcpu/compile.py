from typing import List, Dict
from dataclasses import dataclass, field

from gcpu import ast, default_config

FP_START = 'fp_start'


class CompileError(Exception):
    pass


@dataclass
class AssemblyFunction:
    code: List[int] = field(default_factory=list)


class Compiler:
    def __init__(self):

        self.resulting_code: List[int] = []

        self.global_variables = {FP_START: 0}
        self.indices_to_replace: Dict[int, str] = {}
        self.frame_variables_offsets: Dict[str, int] = {}
        self.frame_size = 0

    @property
    def current_program_length(self):
        return len(self.resulting_code)

    def generate(self, code: List[int]):
        for item in code:
            if isinstance(item, str):
                self.indices_to_replace[self.current_program_length] = item
            self.resulting_code.append(item)

    def put_value_node_in_a_register(self, node: ast.ValueProviderNode):
        if isinstance(node, ast.ConstantNode):
            self.generate(default_config.lda.build(val=node.value))
        elif isinstance(node, ast.IdentifierNode):
            name = node.identifier
            if name not in self.frame_variables_offsets:
                raise CompileError(f'No variable with name {name}')
            self.generate(default_config.lda_fp_offset.build(offset=self.frame_variables_offsets[name]))
        else:
            raise CompileError('not supported yet')

    def put_header(self):
        self.generate(
            default_config.ldfp.build(val=FP_START)
        )

    def build_function(self, nodes: List[ast.AstNode]) -> List[int]:

        self.put_header()

        for node in nodes:
            if isinstance(node, ast.PrintNode):
                self.put_value_node_in_a_register(node.target)
                self.generate(default_config.print.build())
            elif isinstance(node, ast.AssignNode):
                name = node.target
                if name not in self.frame_variables_offsets:
                    self.frame_variables_offsets[name] = self.frame_size
                    self.frame_size += 1
                self.put_value_node_in_a_register(node.value_node)
                self.generate(default_config.sta_fp_offset.build(offset=self.frame_variables_offsets[name]))
            else:
                raise CompileError(f'node of type {node} not supported yet')

        self.generate(default_config.exit.build())

        # We are done generating code here
        self.global_variables[FP_START] = self.current_program_length

        self.replace_indices()
        return self.resulting_code

    def replace_indices(self):
        for index, name in self.indices_to_replace.items():
            offset = self.global_variables.get(name, None)
            if offset is None:
                raise CompileError(f'No variable {name!r} found')
            self.resulting_code[index] = offset
