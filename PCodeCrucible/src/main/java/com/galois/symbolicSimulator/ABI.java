package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

// Implementations of some basic ABI stuff
public abstract class ABI {
    public abstract int getAddrBytes();
    public long getAddrWidth() { return getAddrBytes() * 8; }

    public abstract BigInteger argumentRegister( int i );
    public abstract BigInteger returnRegister( int i );
    public abstract BigInteger stackRegister();
    public abstract BigInteger frameRegister();

    public abstract TempAddrSpace getTemps();
    public abstract RAMAddrSpace getRAM();
    public abstract RegisterAddrSpace getRegisters();

    public abstract Map<String, AddrSpaceManager> initAddrSpaces( Procedure proc );

    public abstract String mangle( String symbol );

    public abstract void push( MachineState state,
                               int bytes,
                               SimulatorValue args )
        throws Exception;

    public abstract SimulatorValue pop( MachineState state,
                                        int bytes )
        throws Exception;

    public abstract void setupCallFrame( MachineState state,
                                         SimulatorValue retAddr,
                                         SimulatorValue... args )
        throws Exception;

    public abstract SimulatorValue extractCallReturns( MachineState state )
        throws Exception;
}
