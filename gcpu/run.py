import subprocess


def run():
    filename = 'output.asm'

    with open(filename, mode='w+') as f:
        f.write("""
.section .data

c: .SPACE 4

array: .SPACE 100
tmp_array: .SPACE 100
reverse_array: .SPACE 100

.section .text

.global _start

_start:

mov r0, #'H'
bl print_r0
mov r0, #'\n'
bl print_r0
bl exit
       
       
       
print_r0:
ldr r1, =tmp_array
strb r0,[r1]
/* syscall write(int fd, const void *buf, size_t count)*/
mov r0, #1
ldr r1, =tmp_array
mov r2, #1
mov r7, #4
svc #0
bx lr

exit:
/*syscall exit(int status)*/
mov r0,#0
mov r7,#1
svc #0
        """)

    output_filename = 'asm32'
    res = subprocess.run(
        f"arm-linux-gnueabihf-as {filename} -o {output_filename}.o && arm-linux-gnueabihf-ld -static {output_filename}.o -o {output_filename}",
        shell=True
    )
    print(res.stdout)

    result = subprocess.run(f'./{output_filename}', capture_output=True)

    print(repr(result.stdout))


if __name__ == '__main__':
    run()
