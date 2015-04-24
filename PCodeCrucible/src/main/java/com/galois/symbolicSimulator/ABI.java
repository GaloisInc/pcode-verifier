package com.galois.symbolicSimulator;

import java.math.BigInteger;

public abstract class ABI {
    // how many bytes are in the register file
    // (pretty sure this is a lot more than we actually need)
    static final int regFileSize = 1024;

    public abstract BigInteger argumentRegister( int i );
    public abstract BigInteger returnRegister( int i );
    public abstract BigInteger stackRegister();
    public abstract BigInteger frameRegister();
}
