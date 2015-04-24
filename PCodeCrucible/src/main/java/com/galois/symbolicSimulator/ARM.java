package com.galois.symbolicSimulator;

import java.math.BigInteger;

// === Here are a bunch of definitions relevant to the ARM ABI ===
public class ARM extends ABI {
    public BigInteger argumentRegister( int i )
    {
	switch( i  ) {
	case 0:
	    return r0;
	case 1:
	    return r1;
	case 2:
	    return r2;
	case 3:
	    return r3;
	}

	return null;
    }

    public BigInteger returnRegister( int i )
    {
	switch( i  ) {
	case 0:
	    return r0;
	case 1:
	    return r1;
	}

	return null;
    }

    public BigInteger stackRegister()
    {
	return SP;
    }

    public BigInteger frameRegister()
    {
	return null;
    }

    static final BigInteger r0  = BigInteger.valueOf( 0x20l );
    static final BigInteger r1  = BigInteger.valueOf( 0x24l );
    static final BigInteger r2  = BigInteger.valueOf( 0x28l );
    static final BigInteger r3  = BigInteger.valueOf( 0x2cl );

    static final BigInteger r4  = BigInteger.valueOf( 0x30l );
    static final BigInteger r5  = BigInteger.valueOf( 0x34l );
    static final BigInteger r6  = BigInteger.valueOf( 0x38l );
    static final BigInteger r7  = BigInteger.valueOf( 0x3cl );

    static final BigInteger r8  = BigInteger.valueOf( 0x40l );
    static final BigInteger r9  = BigInteger.valueOf( 0x44l );
    static final BigInteger r10 = BigInteger.valueOf( 0x48l );
    static final BigInteger r11 = BigInteger.valueOf( 0x4cl );

    static final BigInteger r12 = BigInteger.valueOf( 0x50l );
    static final BigInteger r13 = BigInteger.valueOf( 0x54l );
    static final BigInteger r14 = BigInteger.valueOf( 0x58l );
    static final BigInteger r15 = BigInteger.valueOf( 0x5cl );

    static final BigInteger a1 = r0;
    static final BigInteger a2 = r1;
    static final BigInteger a3 = r2;
    static final BigInteger a4 = r3;

    static final BigInteger v1 = r4;
    static final BigInteger v2 = r5;
    static final BigInteger v3 = r6;
    static final BigInteger v4 = r7;
    static final BigInteger v5 = r8;
    static final BigInteger v6 = r9;
    static final BigInteger v7 = r10;
    static final BigInteger v8 = r11;

    static final BigInteger SB = r9;
    static final BigInteger TR = r9;

    static final BigInteger IP = r12;
    static final BigInteger SP = r13;
    static final BigInteger LR = r14;
    static final BigInteger PC = r15;

    static final BigInteger N_flag = BigInteger.valueOf( 0x64l );
    static final BigInteger Z_flag = BigInteger.valueOf( 0x65l );
    static final BigInteger C_flag = BigInteger.valueOf( 0x66l );
    static final BigInteger V_flag = BigInteger.valueOf( 0x67l );
}
