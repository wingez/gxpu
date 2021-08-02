from typing import List, Dict
from dataclasses import dataclass, field

from gcpu import ast, default_config

FP_START = 'fp_start'
MAIN_FUNCTION_ADDR = 'main_function_addr'


class CompileError(Exception):
    pass


@dataclass
class AssemblyFunction:
    code: List[int] = field(default_factory=list)
    frame_variables_offsets: Dict[str, int] = field(default_factory=dict)
    frame_size: int = 0
    name: str = ''


class Compiler:
    def __init__(self):
        self.resulting_code: List[int] = []
        self.current_function = AssemblyFunction()
        self.main_function_index = 0

        self.indices_to_replace: Dict[int, str] = {}

        self.function_indices: Dict[str, int] = {}

    @property
    def current_program_length(self):
        return len(self.resulting_code)

    def generate_current_function(self, code: List[int]):
        self.current_function.code.extend(code)

    def put_value_node_in_a_register(self, node: ast.ValueProviderNode):
        if isinstance(node, ast.ConstantNode):
            self.generate_current_function(default_config.lda.build(val=node.value))
        elif isinstance(node, ast.IdentifierNode):
            name = node.identifier
            if name not in self.current_function.frame_variables_offsets:
                raise CompileError(f'No variable with name {name}')
            self.generate_current_function(
                default_config.lda_fp_offset.build(offset=self.current_function.frame_variables_offsets[name]))
        elif isinstance(node, ast.OperationNode):
            self.put_value_node_in_a_register(node.left)

            if isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.ConstantNode):
                self.generate_current_function(default_config.adda.build(val=node.right.value))
            elif isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.IdentifierNode):
                self.generate_current_function(default_config.adda_fp_offset.build(
                    offset=self.current_function.frame_variables_offsets[node.right.identifier]))

            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.ConstantNode):
                self.generate_current_function(default_config.suba.build(val=node.right.value))
            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.IdentifierNode):
                self.generate_current_function(default_config.suba_fp_offset.build(
                    offset=self.current_function.frame_variables_offsets[node.right.identifier]))


            else:
                raise CompileError(' not supported yet')


        else:
            raise CompileError('not supported yet')

    def put_header(self):

        def generate(code: List[int]):
            for item in code:
                if isinstance(item, str):
                    self.indices_to_replace[self.current_program_length] = item
                self.resulting_code.append(item)

        generate(default_config.ldfp.build(val=FP_START))
        generate(default_config.ldsp.build(val=FP_START))
        generate(default_config.call_addr.build(addr=MAIN_FUNCTION_ADDR))
        generate(default_config.exit.build())

    def build_function(self, function_node: ast.FunctionNode) -> AssemblyFunction:
        self.current_function = AssemblyFunction(name=function_node.name)

        for expression_node in function_node.body:
            if isinstance(expression_node, ast.PrintNode):
                self.put_value_node_in_a_register(expression_node.target)
                self.generate_current_function(default_config.print.build())
            elif isinstance(expression_node, ast.AssignNode):
                name = expression_node.target
                if name not in self.current_function.frame_variables_offsets:
                    self.current_function.frame_variables_offsets[name] = self.current_function.frame_size
                    self.current_function.frame_size += 1
                self.put_value_node_in_a_register(expression_node.value_node)
                self.generate_current_function(
                    default_config.sta_fp_offset.build(offset=self.current_function.frame_variables_offsets[name]))

            elif isinstance(expression_node, ast.CallNode):
                function_name = expression_node.target_name
                if function_name not in self.function_indices:
                    raise CompileError(f'No function with name: {function_name}')
                self.generate_current_function(
                    default_config.call_addr.build(addr=self.function_indices[function_name]))


            else:
                raise CompileError(f'node of type {expression_node} not supported yet')

        self.generate_current_function(default_config.ret.build())

        return self.current_function

    def build_program(self, function_nodes: List[ast.FunctionNode]) -> List[int]:

        self.put_header()

        for node in function_nodes:
            if isinstance(node, ast.FunctionNode):
                function = self.build_function(node)

                if function.name == 'main':
                    self.main_function_index = self.current_program_length

                self.function_indices[function.name] = self.current_program_length

                self.resulting_code.extend(self.current_function.code)
                # We are done generating code here
            else:
                raise CompileError(f'Dont know how to parse {node}')

        self.replace_indices()
        return self.resulting_code

    def build_single_main_function(self, nodes: List[ast.expressions_types]) -> List[int]:
        node = ast.FunctionNode(name='main', body=nodes)
        return self.build_program([node])

    def replace_indices(self):
        for index, name in self.indices_to_replace.items():
            offset: int
            if name == MAIN_FUNCTION_ADDR:
                if self.main_function_index == 0:
                    raise CompileError('No main-function provided')
                offset = self.main_function_index
            elif name == FP_START:
                offset = self.current_program_length
            else:
                raise CompileError(f'Unknown variable {name}')

            self.resulting_code[index] = offset
