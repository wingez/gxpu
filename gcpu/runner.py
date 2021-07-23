
from gcpu.constant import Constant


class Runner:

    def __init__(self):
        self._output = ''

    def print(self, constant: Constant):
        self._output += f'{constant.value}\n'

    def output(self) -> str:
        return self._output
