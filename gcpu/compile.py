import subprocess
from typing import List, Dict
from pathlib import Path

from gcpu import ast, default_config


class CompileError(Exception):
    pass


class AssemblyFunction:
    def __init__(self):
        self._function_body: List[str] = []
        self._current_stack_size = 0

        self.register_stack_mapping: Dict[str, int] = {}

    def build_function(self, nodes: List[ast.AstNode]) -> List[int]:
        result = []
        for node in nodes:
            if isinstance(node, ast.PrintNode):
                result.extend(default_config.lda.build(val=node.target.value))
                result.extend(default_config._print.build())
            else:
                raise CompileError(f'node of type {node} not supported yet')

        result.extend(default_config._exit.build())
        return result
