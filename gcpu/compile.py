from __future__ import annotations
from dataclasses import dataclass
from typing import List, Dict, Sequence

from gcpu import ast, default_config, instructions

FP_STACK_SIZE = 1
SP_STACK_SIZE = 1
PC_STACK_SIZE = 1

STACK_START = 0xff


class CompileError(Exception):
    pass


@dataclass
class DataType:
    name: str
    size: int


void = DataType(ast.VOID_TYPE_NAME, 0)

primitive_types = [
    DataType('byte', 1),
    void,
]


@dataclass
class FrameLayout:
    total_size: int
    identifiers: Dict[str, int]

    size_of_parameters: int
    size_of_meta: int
    size_of_vars: int
    size_of_ret: int

    def get_description(self) -> List[str]:
        result = []
        for identifier, offset in sorted(self.identifiers.items(), key=lambda x: x[1], reverse=False):
            result.append(f'{offset}: {identifier}')

        return result


def get_frame_layout(compiler: Compiler, function_node: ast.FunctionNode) -> FrameLayout:
    offsets_from_top: Dict[str, int] = {}

    return_type = compiler.get_type(function_node.return_type)
    if return_type != void:
        offsets_from_top['result'] = 0

    size_of_ret = return_type.size
    current_size = size_of_ret

    size_of_args = 0
    for arg in function_node.arguments:
        offsets_from_top[arg] = current_size
        current_size += 1
        size_of_args += 1

    size_of_meta = SP_STACK_SIZE + PC_STACK_SIZE
    current_size += size_of_meta

    size_of_vars = 0

    def build_recursive(nodes: Sequence[ast.AstNode]):
        nonlocal current_size
        nonlocal size_of_vars
        for node in nodes:
            if isinstance(node, ast.AssignNode):
                if node.target not in offsets_from_top:
                    # TODO check type
                    offsets_from_top[node.target] = current_size
                    current_size += 1
                    size_of_vars += 1
            elif isinstance(node, ast.IfNode):
                build_recursive(node.body)
                build_recursive(node.else_body)
            elif isinstance(node, ast.WhileNode):
                build_recursive(node.body)

    build_recursive(function_node.body)

    return FrameLayout(
        total_size=current_size,
        identifiers={name: current_size - 1 - val for name, val in offsets_from_top.items()},
        size_of_vars=size_of_vars,
        size_of_parameters=size_of_args,
        size_of_meta=size_of_meta,
        size_of_ret=size_of_ret,
    )


@dataclass
class AssemblyFunction:
    compiler: Compiler
    return_type: DataType
    frame_layout: FrameLayout
    name: str = ''
    memory_address: int = 0

    def has_variable(self, name: str) -> bool:
        return name in self.frame_layout.identifiers

    def build_statement_node(self, statement_node: ast.StatementNode):
        if isinstance(statement_node, ast.PrintNode):
            self.put_value_node_in_a_register(statement_node.target)
            self.compiler.put_code(default_config.print.build())
        elif isinstance(statement_node, ast.AssignNode):
            name = statement_node.target
            if name not in self.frame_layout.identifiers:
                raise AssertionError
            self.put_value_node_in_a_register(statement_node.value_node)
            self.compiler.put_code(
                default_config.sta_fp_offset.build(offset=self.frame_layout.identifiers[name]))

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

            # TODO!
            # pop return value if exists
            if function.frame_layout.size_of_ret != 0:
                self.compiler.put_code(
                    default_config.addsp.build(val=function.frame_layout.size_of_ret))

        elif isinstance(statement_node, ast.ReturnNode):
            self.compiler.put_code(default_config.ret.build())

        else:
            raise CompileError(f'node of type {statement_node} not supported yet')

    def call_func(self, node: ast.CallNode) -> AssemblyFunction:
        function_name = node.target_name
        if function_name not in self.compiler.functions:
            raise CompileError(f'No function with name: {function_name}')
        function = self.compiler.functions[function_name]
        # TODO: add back check
        # if not len(node.parameters) == function.num_arguments:
        #     raise CompileError(f'Function {function_name} expected {function.num_arguments} args')

        # place return value
        if function.return_type.size != 0:
            self.compiler.put_code(default_config.sub_sp.build(val=function.return_type.size))

        # place arguments
        for value_node in node.parameters:
            self.put_value_node_in_a_register(value_node)
            self.compiler.put_code(default_config.push_a.build())

        self.compiler.put_code(
            default_config.call_addr.build(addr=function.memory_address))

        params_size = function.frame_layout.size_of_parameters
        if params_size != 0:
            self.compiler.put_code(default_config.addsp.build(val=params_size))

        return function

    def put_value_node_in_a_register(self, node: ast.ValueProviderNode):
        if isinstance(node, ast.ConstantNode):
            self.compiler.put_code(default_config.lda.build(val=node.value))
        elif isinstance(node, ast.IdentifierNode):
            name = node.identifier
            if name not in self.frame_layout.identifiers:
                raise CompileError(f'No variable with name {name}')

            offset = self.frame_layout.identifiers[name]
            self.compiler.put_code(default_config.lda_fp_offset.build(offset=offset))
        elif isinstance(node, ast.OperationNode):
            self.put_value_node_in_a_register(node.left)

            if isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.ConstantNode):
                self.compiler.put_code(default_config.adda.build(val=node.right.value))
            elif isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.IdentifierNode):
                offset = self.frame_layout.identifiers[node.right.identifier]
                self.compiler.put_code(default_config.adda_fp_offset.build(offset=offset))

            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.ConstantNode):
                self.compiler.put_code(default_config.suba.build(val=node.right.value))
            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.IdentifierNode):
                offset = self.frame_layout.identifiers[node.right.identifier]
                self.compiler.put_code(default_config.suba_fp_offset.build(offset=offset))

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

        if self.frame_layout.size_of_vars != 0:
            self.compiler.put_code(default_config.sub_sp.build(val=self.frame_layout.size_of_vars))
        self.compiler.put_code(default_config.ldfp_sp.build())

        for statement_node in node.body:
            self.build_statement_node(statement_node)

        if self.frame_layout.size_of_vars != 0:
            self.compiler.put_code(default_config.ret_with_frame_size.build(frame_size=self.frame_layout.size_of_vars))
        else:
            self.compiler.put_code(default_config.ret.build())


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

    def get_type(self, identifier: str):
        if identifier not in self.types:
            raise CompileError(f'No type with name {identifier!r} found')
        return self.types[identifier]

    def build_new_function(self, function_node: ast.FunctionNode) -> AssemblyFunction:

        return_type_name = function_node.return_type if function_node.return_type else 'void'
        if return_type_name not in self.types:
            raise CompileError(f'No known type {function_node.return_type}')

        function = AssemblyFunction(compiler=self, return_type=self.types[return_type_name], name=function_node.name,
                                    memory_address=self.current_size,
                                    frame_layout=get_frame_layout(self, function_node))

        if function.name in self.functions:
            raise CompileError(f'Function {function.name} already declared')
        self.functions[function.name] = function

        function.build(function_node)

        return function

    def build_program(self, function_nodes: List[ast.FunctionNode]) -> List[int]:

        self.put_code(default_config.ldfp.build(val=STACK_START))
        self.put_code(default_config.ldsp.build(val=STACK_START))
        to_place_call_main = self.make_space_for(default_config.call_addr)
        to_place_exit = self.make_space_for(default_config.exit)

        for node in function_nodes:
            if isinstance(node, ast.FunctionNode):
                self.build_new_function(node)

            else:
                raise CompileError(f'Dont know how to parse {node}')

        if 'main' not in self.functions:
            raise CompileError('No main-function provided')

        main_function = self.functions['main']
        if main_function.frame_layout.size_of_parameters != 0 or main_function.frame_layout.size_of_ret != 0:
            raise CompileError("No suitable main-function found")

        self.put_code_at(default_config.call_addr.build(addr=main_function.memory_address), to_place_call_main)
        self.put_code_at(default_config.exit.build(), to_place_exit)

        return self.resulting_code

    def build_single_main_function(self, nodes: List[ast.StatementNode]) -> List[int]:
        node = ast.FunctionNode(name='main', body=nodes, arguments=[])
        return self.build_program([node])
