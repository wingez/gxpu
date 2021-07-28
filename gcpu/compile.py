from typing import List

from gcpu import ast, default_config


class CompileError(Exception):
    pass


class AssemblyFunction:
    def __init__(self):
        self.code: List[int] = []


class Compiler:
    def __init__(self):

        self.current_function = AssemblyFunction()

    def generate(self, code: List[int]):
        self.current_function.code.extend(code)

    def put_value_node_in_a_register(self, node: ast.ValueProviderNode):
        if isinstance(node, ast.ConstantNode):
            self.generate(default_config.lda.build(val=node.value))
        else:
            raise CompileError('not supported yet')

    def build_function(self, nodes: List[ast.AstNode]) -> List[int]:
        for node in nodes:
            if isinstance(node, ast.PrintNode):
                self.put_value_node_in_a_register(node.target)
                self.generate(default_config.print.build())
            else:
                raise CompileError(f'node of type {node} not supported yet')

        self.generate(default_config.exit.build())
        return self.current_function.code
