from typing import Any, Mapping, Dict

from gcpu.constant import Constant


class Scope:
    def __init__(self):
        self._items: Dict[str, Constant] = {}

    def set(self, name: str, value: Constant):
        self._items[name] = value

    def get(self, name: str) -> Constant:
        return self._items[name]
