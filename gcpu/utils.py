from typing import List


def split_many(to_split: str, delimiters: List[str]):
    delimiters_copy = delimiters.copy()

    current = [to_split]
    while delimiters_copy:
        delimiter = delimiters_copy.pop()
        new_current = []
        for c in current:
            new_current.extend(c.split(delimiter))
        current = new_current

    return current
