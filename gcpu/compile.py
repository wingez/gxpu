from typing import List, Dict
from dataclasses import dataclass, field

from gcpu import ast, default_config

FP_STACK_SIZE = 1
SP_STACK_SIZE = 1

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
    args: List[str] = field(default_factory=list)
    memory_address: int = 0

    def __post_init__(self):
        for index, arg in enumerate(self.args):
            self.frame_variables_offsets[arg] = 0 - SP_STACK_SIZE - FP_STACK_SIZE - self.num_arguments + index

    def has_variable(self, name: str) -> bool:
        return name in self.frame_variables_offsets or name in self.args

    @property
    def num_arguments(self) -> int:
        return len(self.args)

    @property
    def current_size(self) -> int:
        return len(self.code)


class Compiler:
    def __init__(self):
        self.resulting_code: List[int] = []
        self.current_function = AssemblyFunction()

        self.indices_to_replace: Dict[int, str] = {}

        self.functions: Dict[str, AssemblyFunction] = {}

    @property
    def current_program_length(self) -> int:
        return len(self.resulting_code)

    @property
    def current_generating_position(self) -> int:
        return self.current_program_length + len(self.current_function.code)

    def generate_current_function(self, code: List[int]):
        self.current_function.code.extend(code)

    def put_value_node_in_a_register(self, node: ast.ValueProviderNode):
        if isinstance(node, ast.ConstantNode):
            self.generate_current_function(default_config.lda.build(val=node.value))
        elif isinstance(node, ast.IdentifierNode):
            name = node.identifier
            if name not in self.current_function.frame_variables_offsets:
                raise CompileError(f'No variable with name {name}')

            offset = self.current_function.frame_variables_offsets[name]
            if offset >= 0:
                self.generate_current_function(default_config.lda_fp_offset.build(offset=offset))
            else:
                self.generate_current_function(default_config.lda_fp_negative.build(offset=-offset))
        elif isinstance(node, ast.OperationNode):
            self.put_value_node_in_a_register(node.left)

            if isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.ConstantNode):
                self.generate_current_function(default_config.adda.build(val=node.right.value))
            elif isinstance(node, ast.AdditionNode) and isinstance(node.right, ast.IdentifierNode):
                offset = self.current_function.frame_variables_offsets[node.right.identifier]
                if offset >= 0:
                    self.generate_current_function(default_config.adda_fp_offset.build(offset=offset))
                else:
                    self.generate_current_function(default_config.adda_fp_offset_negative.build(offset=-offset))

            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.ConstantNode):
                self.generate_current_function(default_config.suba.build(val=node.right.value))
            elif isinstance(node, ast.SubtractionNode) and isinstance(node.right, ast.IdentifierNode):
                offset = self.current_function.frame_variables_offsets[node.right.identifier]
                if offset >= 0:
                    self.generate_current_function(default_config.suba_fp_offset.build(offset=offset))
                else:
                    self.generate_current_function(default_config.suba_fp_offset_negative.build(offset=-offset))

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

    def build_statement_node(self, statement_node: ast.StatementNode):
        if isinstance(statement_node, ast.PrintNode):
            self.put_value_node_in_a_register(statement_node.target)
            self.generate_current_function(default_config.print.build())
        elif isinstance(statement_node, ast.AssignNode):
            name = statement_node.target
            if name not in self.current_function.frame_variables_offsets:
                self.current_function.frame_variables_offsets[name] = self.current_function.frame_size
                self.current_function.frame_size += 1
            self.put_value_node_in_a_register(statement_node.value_node)
            self.generate_current_function(
                default_config.sta_fp_offset.build(offset=self.current_function.frame_variables_offsets[name]))

        elif isinstance(statement_node, ast.IfNode):
            has_else = bool(statement_node.else_body)
            self.put_value_node_in_a_register(statement_node.condition)
            self.generate_current_function(default_config.test_a.build())
            to_put_false_condition_address = self.current_function.current_size + 1 + default_config.jump_if_zero.get_position_of_variable(
                'addr')
            self.generate_current_function(default_config.jump_if_zero.build(addr=0))
            for node in statement_node.body:
                self.build_statement_node(node)

            to_put_end_address = 0
            if has_else:
                to_put_end_address = self.current_function.current_size + 1 + default_config.jump.get_position_of_variable(
                    'addr')
                self.generate_current_function(default_config.jump.build(addr=0))

            self.current_function.code[to_put_false_condition_address] = self.current_generating_position

            if has_else:
                for node in statement_node.else_body:
                    self.build_statement_node(node)
                self.current_function.code[to_put_end_address] = self.current_generating_position

        elif isinstance(statement_node, ast.WhileNode):
            start_of_block_index = self.current_generating_position
            self.put_value_node_in_a_register(statement_node.condition)
            self.generate_current_function(default_config.test_a.build())
            to_put_exit_address = self.current_function.current_size + 1 + default_config.jump_if_zero.get_position_of_variable(
                'addr')
            self.generate_current_function(default_config.jump_if_zero.build(addr=0))

            for node in statement_node.body:
                self.build_statement_node(node)
            self.generate_current_function(default_config.jump.build(addr=start_of_block_index))
            self.current_function.code[to_put_exit_address] = self.current_generating_position

        elif isinstance(statement_node, ast.CallNode):
            function_name = statement_node.target_name
            if function_name not in self.functions:
                raise CompileError(f'No function with name: {function_name}')
            function = self.functions[function_name]
            if not len(statement_node.parameters) == function.num_arguments:
                raise CompileError(f'Function {function_name} expected {function.num_arguments} args')

            # TODO: function return size
            self.generate_current_function(default_config.addsp.build(val=0))

            # place arguments
            for value_node in statement_node.parameters:
                self.put_value_node_in_a_register(value_node)
                self.generate_current_function(default_config.push_a.build())

            self.generate_current_function(
                default_config.call_addr.build(addr=self.functions[function_name].memory_address))

            # TODO: + function return size
            self.generate_current_function(default_config.sub_sp.build(val=0 + function.num_arguments))

        else:
            raise CompileError(f'node of type {statement_node} not supported yet')

    def build_function(self, function_node: ast.FunctionNode) -> AssemblyFunction:

        self.current_function = AssemblyFunction(name=function_node.name, args=function_node.arguments,
                                                 memory_address=self.current_program_length)
        index_of_frame_size = len(self.current_function.code) + 1 + default_config.addsp.get_position_of_variable('val')
        self.generate_current_function(default_config.addsp.build(val=0))

        for statement_node in function_node.body:
            self.build_statement_node(statement_node)

        self.generate_current_function(default_config.ret.build())

        self.current_function.code[index_of_frame_size] = self.current_function.frame_size

        return self.current_function

    def build_program(self, function_nodes: List[ast.FunctionNode]) -> List[int]:

        self.put_header()

        for node in function_nodes:
            if isinstance(node, ast.FunctionNode):
                function = self.build_function(node)

                if function.name in self.functions:
                    raise CompileError(f'Function {function.name} already declared')
                self.functions[function.name] = function

                self.resulting_code.extend(self.current_function.code)
                # We are done generating code here
            else:
                raise CompileError(f'Dont know how to parse {node}')

        self.replace_indices()
        return self.resulting_code

    def build_single_main_function(self, nodes: List[ast.StatementNode]) -> List[int]:
        node = ast.FunctionNode(name='main', body=nodes, arguments=[])
        return self.build_program([node])

    def replace_indices(self):

        if 'main' not in self.functions:
            raise CompileError('No main-function provided')

        for index, name in self.indices_to_replace.items():
            offset: int
            if name == MAIN_FUNCTION_ADDR:
                offset = self.functions['main'].memory_address
            elif name == FP_START:
                offset = self.current_program_length
            else:
                raise CompileError(f'Unknown variable {name}')

            self.resulting_code[index] = offset
