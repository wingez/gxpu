import sys
from gcpu import assembler, default_config

numbers = eval(' '.join(sys.argv[1:]))

for line in assembler.disassemble(default_config.instructions, numbers):
    print(line)
