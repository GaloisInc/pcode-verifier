package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

// Implementations of some basic ABI stuff
public abstract class ABI {
    public static ABI getInstance( String name, PCodeArchSpec arch, Simulator sim ) {
        if( name.equals( "AMD64" ) ) { return new X86_64( arch, sim ); }
        if( name.equals( "ARM" )   ) { return new ARM( arch, sim ); }

        throw new IllegalArgumentException( "Unknown ABI: " + name );
    }

    public abstract Type[] machineStateTypes();

    public abstract int getAddrBytes();
    public long getAddrWidth() { return getAddrBytes() * 8; }

    public abstract BigInteger argumentRegister( int i )
        throws Exception;
    public abstract BigInteger returnRegister( int i )
        throws Exception;
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
