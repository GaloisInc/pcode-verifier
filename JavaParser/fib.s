	.section	__TEXT,__text,regular,pure_instructions
	.globl	_fib
	.align	4, 0x90
_fib:                                   ## @fib
	.cfi_startproc
## BB#0:
	pushq	%rbp
Ltmp2:
	.cfi_def_cfa_offset 16
Ltmp3:
	.cfi_offset %rbp, -16
	movq	%rsp, %rbp
Ltmp4:
	.cfi_def_cfa_register %rbp
	subq	$16, %rsp
	movl	%edi, -8(%rbp)
	cmpl	$1, -8(%rbp)
	jge	LBB0_2
## BB#1:
	movl	$0, -4(%rbp)
	jmp	LBB0_5
LBB0_2:
	cmpl	$1, -8(%rbp)
	jne	LBB0_4
## BB#3:
	movl	$1, -4(%rbp)
	jmp	LBB0_5
LBB0_4:
	movl	-8(%rbp), %eax
	subl	$2, %eax
	movl	%eax, %edi
	callq	_fib
	movl	-8(%rbp), %edi
	subl	$1, %edi
	movl	%eax, -12(%rbp)         ## 4-byte Spill
	callq	_fib
	movl	-12(%rbp), %edi         ## 4-byte Reload
	addl	%eax, %edi
	movl	%edi, -4(%rbp)
LBB0_5:
	movl	-4(%rbp), %eax
	addq	$16, %rsp
	popq	%rbp
	retq
	.cfi_endproc

	.globl	_main
	.align	4, 0x90
_main:                                  ## @main
	.cfi_startproc
## BB#0:
	pushq	%rbp
Ltmp7:
	.cfi_def_cfa_offset 16
Ltmp8:
	.cfi_offset %rbp, -16
	movq	%rsp, %rbp
Ltmp9:
	.cfi_def_cfa_register %rbp
	subq	$16, %rsp
	movl	$45, %edi
	callq	_fib
	leaq	L_.str(%rip), %rdi
	movl	$45, %esi
	movl	%eax, %edx
	movb	$0, %al
	callq	_printf
	movl	$0, %edx
	movl	%eax, -4(%rbp)          ## 4-byte Spill
	movl	%edx, %eax
	addq	$16, %rsp
	popq	%rbp
	retq
	.cfi_endproc

	.section	__TEXT,__cstring,cstring_literals
L_.str:                                 ## @.str
	.asciz	"Fibonacci of %d is:  %d.\n"


.subsections_via_symbols
