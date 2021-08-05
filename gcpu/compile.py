from __future__ import annotations
from typing import List, Dict
from dataclasses import dataclass, field

from gcpu import ast, default_config, instructions

FP_STACK_SIZE = 1
SP_STACK_SIZE = 1


class CompileError(Exception):
    pass


@dataclass
class DataType:
    name: str
    size: int


void = DataType('void', 0)

primitive_types = [
    DataType('byte', 1),
    void,
]


@dataclass
class AssemblyFunction:
    compiler: Compiler
    return_type: DataType
    frame_variables_offsets: Dict[str, int] = field(default_factory=dict)
    frame_size: int = 0
    name: str = ''
    args: List[str] = field(default_factory=list)
    memory_address: int = 0

    def __post_init__(self):
        self.frame_variables_offsets[
            'return'] = 0 - SP_STACK_SIZE - FP_STACK_SIZE - self.num_arguments - self.return_type.size
        for index, arg in enumerate(self.args):
            self.frame_variables_offsets[arg] = 0 - SP_STACK_SIZE - FP_STACK_SIZE - self.num_arguments + index

    def has_variable(self, name: str) -> bool:
        return name in self.frame_variables_offsets or name in self.args

    @property
    def num_arguments(self) -> int:
        return len(self.args)

    def build_statement_node(self, statement_node: ast.StatementNode):
        if isinstance(statement_node, ast.PrintNode):
            self.put_value_node_in_a_register(statement_node.target)
            self.compiler.put_code(default_config.print.build())
        elif isinstance(statement_node, ast.AssignNode):
            name = statement_node.target
            if name not in self.frame_variables_offsets:
                self.frame_variables_offsets[name] = self.frame_size
                self.frame_size += 1
            self.put_value_node_in_a_register(statement_node.value_node)
            self.compiler.put_code(
                default_config.sta_fp_offset.build(offset=self.frame_variables_offsets[name]))

        elif isinstance(statement_node, ast.IfNode):
            has_else = bool(statement_node.else_body)
            self.put_value_node_in_a_register(statement_node.condition)
            self.compiler.put_code(default_config.test_a.build())
            to_put_jump_to_false_condition = self.compiler.make_space_for(default_config.jump_if_zero)
            for node in statement_node.body:
                self.build_statement_node(node)

            to_put_jump_to_end = 0
            if has_else:
                to_put_jump_to_end = self.compiler.make_space_for(default_config.jump)

            self.compiler.put_code_at(default_config.jump_if_zero.build(addr=self.compiler.current_size),
                                      to_put_jump_to_false_condition)

            if has_else:
                for node in statement_node.else_body:
                    self.build_statement_node(node)
                self.compiler.put_code_at(default_config.jump.build(addr=self.compiler.current_size),
                                          to_put_jump_to_end)

        elif isinstance(statement_node, ast.WhileNode):
            start_of_block_index = self.compiler.current_size
            self.put_value_node_in_a_register(statement_node.condition)
            self.compiler.put_code(default_config.test_a.build())
            to_put_jump_to_exit = self.compiler.make_space_for(default_config.jump_if_zero)
            for node in statement_node.body:
                self.build_statement_node(node)

            self.compiler.put_code(default_config.jump.build(addr=start_of_block_index))
            self.compiler.put_code_at(default_config.jump_if_zero.build(addr=self.compiler.current_size),
                                      to_put_jump_to_exit)

        elif isinstance(statement_node, ast.CallNode):
            function = self.call_func(statement_node)

            self.compiler.put_code(default_config.sub_sp.build(val=function.return_type.size + function.num_arguments))

        elif isinstance(statement_node, ast.ReturnNode):

            if statement_node.value:
                assert self.return_type.size == 1
                self.put_value_node_in_a_register(statement_node.value)
                self.compiler.put_code(
                    default_config.sta_fp_offset_negative.build(offset=-self.frame_variables_offsets['return']))

            self.compiler.put_code(default_config.ret.build())

        else:
            raise CompileError(f'node of type {statement_node} not supported yet')

    def call_func(self, node: ast.CallNode) -> AssemblyFunction:
        function_name = node.target_name
        if function_name not in self.compiler.functions:
            raise CompileError(f'No function with name: {function_name}')
        function = self.compiler.functions[function_name]
        if not len(node.parameters) == function.num_arguments:
            raise CompileError(f'Function {function_name} expected {function.num_arguments} args')

        self.compiler.put_code(default_config.addsp.build(val=function.return_type.size))

        # place arguments
        for value_node in node.parameters:
            self.put_value_node_in_a_register(value_node)
            self.compiler.put_code(default_config.push_a.build())

        self.compiler.put_code(
            default_config.call_addr.build(addr=self.compiler.functions[function_name].memory_address))

        return function

    def put_value_node_in_a_register(self, node: ast.ValueProviderNode):
        if isinstance(node, ast.ConstantNode):
            self.compiler.put_code(default_config.lda.build(val=node.value))
        elif isinstance(node, ast.IdentifierNode):
            name = node.identifier
            if name not in self.frame_variables_offsets:
                raise CompileError(f'No variable with name {name}')

            offset = self.frame_variables_offsets[name]
            if offset >= 0:
                self.compiler.put_code(default_config.lda_fp_offset.build(offset=offset))
            else:
                self.compiler.put_code(default_config.lda_fp_negative.build(offset=-offset))
        elif isinstance(node, ast.OperationNode):
            self.put_value_node_in_a_register(node.left)

            if isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.ConstantNode):
                self.compiler.put_code(default_config.adda.build(val=node.right.value))
            elif isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.IdentifierNode):
                offset = self.frame_variables_offsets[node.right.identifier]
                if offset >= 0:
                    self.compiler.put_code(default_config.adda_fp_offset.build(offset=offset))
                else:
                    self.compiler.put_code(default_config.adda_fp_offset_negative.build(offset=-offset))

            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.ConstantNode):
                self.compiler.put_code(default_config.suba.build(val=node.right.value))
            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.IdentifierNode):
                offset = self.frame_variables_offsets[node.right.identifier]
                if offset >= 0:
                    self.compiler.put_code(default_config.suba_fp_offset.build(offset=offset))
                else:
                    self.compiler.put_code(default_config.suba_fp_offset_negative.build(offset=-offset))

            else:
                raise CompileError(' not supported yet')

        elif isinstance(node, ast.CallNode):
            func = self.call_func(node)
            assert func.return_type == self.compiler.types['byte']
            self.compiler.put_code(default_config.sub_sp.build(val=func.num_arguments))
            self.compiler.put_code(default_config.pop_a.build())

        else:
            raise CompileError('not supported yet')

    def build(self, node: ast.FunctionNode):
        to_place_increase_sp = self.compiler.make_space_for(default_config.addsp)

        for statement_node in node.body:
            self.build_statement_node(statement_node)

        self.compiler.put_code(default_config.ret.build())
        self.compiler.put_code_at(default_config.addsp.build(val=self.frame_size), to_place_increase_sp)


class Compiler:
    def __init__(self):
        self.resulting_code: List[int] = []
        self.functions: Dict[str, AssemblyFunction] = {}
        self.types: Dict[str, DataType] = {d.name: d for d in primitive_types}

    @property
    def current_size(self) -> int:
        return len(self.resulting_code)

    def put_code(self, code: List[int]):
        self.resulting_code.extend(code)

    def put_code_at(self, code: List[int], position: int):
        for index, c in enumerate(code):
            self.resulting_code[position + index] = c

    def make_space_for(self, instruction: instructions.Instruction) -> int:
        pos = self.current_size
        self.put_code([0] * instruction.size)
        return pos

    def build_new_function(self, function_node: ast.FunctionNode) -> AssemblyFunction:

        return_type_name = function_node.return_type if function_node.return_type else 'void'
        if return_type_name not in self.types:
            raise CompileError(f'No known type {function_node.return_type}')

        function = AssemblyFunction(compiler=self, return_type=self.types[return_type_name], name=function_node.name,
                                    args=function_node.arguments,
                                    memory_address=self.current_size)

        if function.name in self.functions:
            raise CompileError(f'Function {function.name} already declared')
        self.functions[function.name] = function

        function.build(function_node)

        return function

    def build_program(self, function_nodes: List[ast.FunctionNode]) -> List[int]:

        to_place_ldfp = self.make_space_for(default_config.ldfp)
        to_place_ldsp = self.make_space_for(default_config.ldsp)
        to_place_call_main = self.make_space_for(default_config.call_addr)
        to_place_exit = self.make_space_for(default_config.exit)

        for node in function_nodes:
            if isinstance(node, ast.FunctionNode):
                self.build_new_function(node)

            else:
                raise CompileError(f'Dont know how to parse {node}')

        fp_start = self.current_size

        if 'main' not in self.functions:
            raise CompileError('No main-function provided')

        main_function_address = self.functions['main'].memory_address

        self.put_code_at(default_config.ldfp.build(val=fp_start), to_place_ldfp)
        self.put_code_at(default_config.ldsp.build(val=fp_start), to_place_ldsp)
        self.put_code_at(default_config.call_addr.build(addr=main_function_address), to_place_call_main)
        self.put_code_at(default_config.exit.build(), to_place_exit)

        return self.resulting_code

    def build_single_main_function(self, nodes: List[ast.StatementNode]) -> List[int]:
        node = ast.FunctionNode(name='main', body=nodes, arguments=[])
        return self.build_program([node])
