package com.galois.symbolicSimulator;

import java.math.BigInteger;

// === Here are a bunch of definitions relevant to the X86_64 ABI ===
public class X86_64 extends ABI {

    public BigInteger argumentRegister( int i )
    {
	switch( i  ) {
	case 0:
	    return rdi;
	case 1:
	    return rsi;
	case 2:
	    return rdx;
	    /* I don't acutally know where these registers live...
	case 3:
	    return rcx;
	case 4:
	    return r8;
	case 5:
	    return r9;
	    */
	}

	return null;
    }

    public BigInteger returnRegister( int i )
    {
	switch( i  ) {
	case 0:
	    return rax;
	case 1:
	    return rdx;
	}

	return null;
    }

    public BigInteger stackRegister()
    {
	return rsp;
    }

    public BigInteger frameRegister()
    {
	return rbp;
    }

    static final BigInteger rax = BigInteger.valueOf( 0x00l ); // offset value for %rax
    static final BigInteger rsp = BigInteger.valueOf( 0x20l ); // offset value for %rsp
    static final BigInteger rbp = BigInteger.valueOf( 0x28l ); // offset value for %rbp
    static final BigInteger rsi = BigInteger.valueOf( 0x30l ); // offset value for %rsi
    static final BigInteger rdi = BigInteger.valueOf( 0x38l ); // offset value for %rdi

    // Hard to be sure, but I think this is where %rdx lives
    static final BigInteger rdx = BigInteger.valueOf( 0x10l ); // offset value for %rdx

    // ugh, no real idea
    //static final BigInteger rbx = BigInteger.valueOf( ??? );
    //static final BigInteger rcx = BigInteger.valueOf( ??? );

    // maybe these are right?
    static final BigInteger r8  = BigInteger.valueOf( 0x40l );
    static final BigInteger r9  = BigInteger.valueOf( 0x48l );
    static final BigInteger r10 = BigInteger.valueOf( 0x50l );
    static final BigInteger r11 = BigInteger.valueOf( 0x58l );
    static final BigInteger r12 = BigInteger.valueOf( 0x60l );
    static final BigInteger r13 = BigInteger.valueOf( 0x68l );
    static final BigInteger r14 = BigInteger.valueOf( 0x70l );
    static final BigInteger r15 = BigInteger.valueOf( 0x78l );

    // There's a big ol' gap here.  Probably for floating-point
    // registers.  x87 and SSE2 registers will fit in this space,
    // with a little left over... maybe enough for their associated
    // control registers....?

    // Status flags appear to be packed in starting at offset 0x200,
    // each flag in its own byte.  I'm pretty confidant about the flags
    // up to overflow (0x20B).  Everything past that is a total guess.
    static final BigInteger carry_flag     = BigInteger.valueOf( 0x200 );
    // 0x201 reserved
    static final BigInteger parity_flag    = BigInteger.valueOf( 0x202 );
    // 0x203 reserved
    static final BigInteger adjust_flag    = BigInteger.valueOf( 0x203 );
    // 0x205 reserved
    static final BigInteger zero_flag      = BigInteger.valueOf( 0x206 );
    static final BigInteger sign_flag      = BigInteger.valueOf( 0x207 );
    static final BigInteger trap_flag      = BigInteger.valueOf( 0x208 );
    static final BigInteger interrupt_flag = BigInteger.valueOf( 0x209 );
    static final BigInteger direction_flag = BigInteger.valueOf( 0x20A );
    static final BigInteger overflow_flag  = BigInteger.valueOf( 0x20B );
    // I/O privlege level... 0x20C, 0x20D, 0x20E
    // 0x20F reserved

    static final BigInteger resume_flag    = BigInteger.valueOf( 0x210 );
    static final BigInteger virtual_mode_flag = BigInteger.valueOf( 0x211 );
    static final BigInteger alignment_check_flag = BigInteger.valueOf( 0x212 );
    static final BigInteger virtual_interrupt_flag = BigInteger.valueOf( 0x213 );
    static final BigInteger virtual_interrupt_pending_flag = BigInteger.valueOf( 0x214 );
    static final BigInteger cpuid_instruction_flag = BigInteger.valueOf( 0x215 );
    // 0x216 - 0x21F reserved ?
    // 0x220 - 0x22F reserved ?
    // 0x230 - 0x23F reserved ?

    // Is there anything here in this gap???

    // code samples seem to indicate that the PC lives here
    static final BigInteger pc = BigInteger.valueOf( 0x288 );
}
