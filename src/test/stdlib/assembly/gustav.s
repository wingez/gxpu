
    .text
	.globl	main
	#.type	main, @function
main:
	endbr64	
	pushq	%rbp	#
	movq	%rsp, %rbp	#,
# code.c:14: 	gustavprint(5);
	movl	$6, %edi	#,
	call	gustavprint	#
# code.c:15: }
	nop	
	popq	%rbp	#
	ret	
