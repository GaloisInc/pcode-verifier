	.section	__TEXT,__text,regular,pure_instructions
	.globl	_fib
	.align	4, 0x90
_fib:                                   ## @fib
	.cfi_startproc
## BB#0:
	pushq	%rbp
Ltmp3:
	.cfi_def_cfa_offset 16
Ltmp4:
	.cfi_offset %rbp, -16
	movq	%rsp, %rbp
Ltmp5:
	.cfi_def_cfa_register %rbp
	pushq	%r14
	pushq	%rbx
Ltmp6:
	.cfi_offset %rbx, -32
Ltmp7:
	.cfi_offset %r14, -24
	movl	%edi, %ebx
	xorl	%eax, %eax
	testl	%ebx, %ebx
	jle	LBB0_3
## BB#1:
	movl	$1, %eax
	cmpl	$1, %ebx
	je	LBB0_3
## BB#2:
	leal	-2(%rbx), %edi
	callq	_fib
	movl	%eax, %r14d
	decl	%ebx
	movl	%ebx, %edi
	callq	_fib
	addl	%r14d, %eax
LBB0_3:
	popq	%rbx
	popq	%r14
	popq	%rbp
	retq
	.cfi_endproc

	.globl	_main
	.align	4, 0x90
_main:                                  ## @main
	.cfi_startproc
## BB#0:
	pushq	%rbp
Ltmp10:
	.cfi_def_cfa_offset 16
Ltmp11:
	.cfi_offset %rbp, -16
	movq	%rsp, %rbp
Ltmp12:
	.cfi_def_cfa_register %rbp
	movl	$45, %edi
	callq	_fib
	movl	%eax, %ecx
	leaq	L_.str(%rip), %rdi
	movl	$45, %esi
	xorl	%eax, %eax
	movl	%ecx, %edx
	callq	_printf
	xorl	%eax, %eax
	popq	%rbp
	retq
	.cfi_endproc

	.section	__TEXT,__cstring,cstring_literals
L_.str:                                 ## @.str
	.asciz	"Fibonacci of %d is:  %d.\n"


.subsections_via_symbols
