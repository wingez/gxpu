	.file	"code.c"
# GNU C17 (Ubuntu 9.4.0-1ubuntu1~20.04.2) version 9.4.0 (x86_64-linux-gnu)
#	compiled by GNU C version 9.4.0, GMP version 6.2.0, MPFR version 4.0.2, MPC version 1.1.0, isl version isl-0.22.1-GMP

# GGC heuristics: --param ggc-min-expand=100 --param ggc-min-heapsize=131072
# options passed:  -imultiarch x86_64-linux-gnu code.c -mtune=generic
# -march=x86-64 -O0 -fverbose-asm -fno-asynchronous-unwind-tables
# -fstack-protector-strong -Wformat -Wformat-security
# -fstack-clash-protection -fcf-protection
# options enabled:  -fPIC -fPIE -faggressive-loop-optimizations
# -fassume-phsa -fauto-inc-dec -fcommon -fdelete-null-pointer-checks
# -fdwarf2-cfi-asm -fearly-inlining -feliminate-unused-debug-types
# -ffp-int-builtin-inexact -ffunction-cse -fgcse-lm -fgnu-runtime
# -fgnu-unique -fident -finline-atomics -fipa-stack-alignment
# -fira-hoist-pressure -fira-share-save-slots -fira-share-spill-slots
# -fivopts -fkeep-static-consts -fleading-underscore -flifetime-dse
# -flto-odr-type-merging -fmath-errno -fmerge-debug-strings -fpeephole
# -fplt -fprefetch-loop-arrays -freg-struct-return
# -fsched-critical-path-heuristic -fsched-dep-count-heuristic
# -fsched-group-heuristic -fsched-interblock -fsched-last-insn-heuristic
# -fsched-rank-heuristic -fsched-spec -fsched-spec-insn-heuristic
# -fsched-stalled-insns-dep -fschedule-fusion -fsemantic-interposition
# -fshow-column -fshrink-wrap-separate -fsigned-zeros
# -fsplit-ivs-in-unroller -fssa-backprop -fstack-clash-protection
# -fstack-protector-strong -fstdarg-opt -fstrict-volatile-bitfields
# -fsync-libcalls -ftrapping-math -ftree-cselim -ftree-forwprop
# -ftree-loop-if-convert -ftree-loop-im -ftree-loop-ivcanon
# -ftree-loop-optimize -ftree-parallelize-loops= -ftree-phiprop
# -ftree-reassoc -ftree-scev-cprop -funit-at-a-time -fverbose-asm
# -fzero-initialized-in-bss -m128bit-long-double -m64 -m80387
# -malign-stringops -mavx256-split-unaligned-load
# -mavx256-split-unaligned-store -mfancy-math-387 -mfp-ret-in-387 -mfxsr
# -mglibc -mieee-fp -mlong-double-80 -mmmx -mno-sse4 -mpush-args -mred-zone
# -msse -msse2 -mstv -mtls-direct-seg-refs -mvzeroupper

	.text
	.globl	print
	.type	print, @function
print:
	endbr64	
	pushq	%rbp	#
	movq	%rsp, %rbp	#,
	subq	$32, %rsp	#,
	movl	%edi, -20(%rbp)	# i, i
# code.c:6: void print(int i){
	movq	%fs:40, %rax	# MEM[(<address-space-1> long unsigned int *)40B], tmp87
	movq	%rax, -8(%rbp)	# tmp87, D.2595
	xorl	%eax, %eax	# tmp87
# code.c:7: 	char msg[] = "0";
	movw	$48, -10(%rbp)	#, msg
# code.c:8: 	msg[0] = '0'+i;
	movl	-20(%rbp), %eax	# i, tmp85
	addl	$48, %eax	#, _2
# code.c:8: 	msg[0] = '0'+i;
	movb	%al, -10(%rbp)	# _3, msg
# code.c:9: 	write(STDOUT_FILENO,msg,1);
	leaq	-10(%rbp), %rax	#, tmp86
	movl	$1, %edx	#,
	movq	%rax, %rsi	# tmp86,
	movl	$1, %edi	#,
	call	write@PLT	#
# code.c:10: }
	nop	
	movq	-8(%rbp), %rax	# D.2595, tmp88
	xorq	%fs:40, %rax	# MEM[(<address-space-1> long unsigned int *)40B], tmp88
	je	.L2	#,
	call	__stack_chk_fail@PLT	#
.L2:
	leave	
	ret	
	.size	print, .-print
0:
	.string	 "GNU"
1:
	.align 8
	.long	 0xc0000002
	.long	 3f - 2f
2:
	.long	 0x3
3:
	.align 8
4:
