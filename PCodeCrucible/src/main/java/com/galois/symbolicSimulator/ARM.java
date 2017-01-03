package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

// === Here are a bunch of definitions relevant to the ARM ABI ===
public class ARM extends ABI {
    static final int addrWidth = 32;
    static final int addrBytes = 4;
    static final int regWidth = 8; // number of bits required to address all the registers

    PCodeArchSpec arch;
    Simulator sim;

    Map<String, AddrSpaceManager> addrSpaces;
    ConstAddrSpace consts;
    RegisterAddrSpace regs;
    TempAddrSpace temps;
    RAMAddrSpace ram;

    public ARM( PCodeArchSpec arch, Simulator sim )
    {
        this.arch = arch;
        this.sim = sim;
        int byteWidth = arch.wordSize;
        if( byteWidth * 8 != addrWidth ) {
            throw new IllegalArgumentException( "PCode program has incorrect word width for this ABI" );
        }
    }

    public int getAddrBytes() { return addrBytes; }

    public String mangle( String symbol )
    {
        return symbol;
    }

    // FIXME: actually implement this correctly!
    public void setupCallFrame( MachineState state,
                                SimulatorValue returnAddr,
                                SimulatorValue args[] )
        throws Exception
    {
        for( int i=0; i<args.length; i++ ) {
            state.writeReg( argumentRegister(i), addrBytes, args[i] );
        }

        state.writeReg( LR, addrBytes, returnAddr );
    }

    public void push( MachineState state, int bytes, SimulatorValue val )
        throws Exception
    {
        SimulatorValue stk = state.readReg( SP, addrBytes );
        stk = state.sim.bvSub( stk, state.sim.bvLiteral( addrWidth, bytes ) );
        state.writeReg( SP, addrBytes, stk );
        state.poke( stk, addrBytes, val );
    }

    public SimulatorValue pop( MachineState state, int bytes )
        throws Exception
    {
        SimulatorValue stk = state.readReg( SP, addrBytes );
        SimulatorValue x = state.peek( stk, bytes );
        stk = state.sim.bvAdd( stk, state.sim.bvLiteral( addrWidth, bytes ) );
        state.writeReg( SP, addrBytes, stk );
        return x;
    }

    // FIXME: actually implement this correctly!
    public SimulatorValue extractCallReturns( MachineState state )
        throws Exception
    {
        return state.readReg( returnRegister(0), addrBytes );
    }

    public Type[] machineStateTypes()
    {
        Type regFileType = RegisterAddrSpace.getRegisterFileType( regWidth );
        Type ramType     = RAMAddrSpace.getRAMType( addrWidth );
        Type[] types = new Type[]
            { Type.bitvector( addrWidth ),
              regFileType,
              ramType
            };
        return types;
    }

    public Map<String, AddrSpaceManager> initAddrSpaces( Procedure proc )
    {
        addrSpaces = new HashMap<String, AddrSpaceManager>();

        consts = new ConstAddrSpace( arch );
        regs = new RegisterAddrSpace( arch, proc, regWidth, sim );
        temps = new TempAddrSpace( arch, proc );
        ram = new RAMAddrSpace( arch, proc, addrWidth, addrSpaces, sim );

        addrSpaces.put("const"     , consts );
        addrSpaces.put("register"  , regs );
        addrSpaces.put("unique"    , temps );
        addrSpaces.put("ram"       , ram );

        return addrSpaces;
    }

    public TempAddrSpace getTemps() { return temps; }
    public RAMAddrSpace getRAM() { return ram; }
    public RegisterAddrSpace getRegisters() { return regs; }

    public BigInteger argumentRegister( int i )
        throws Exception
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

        throw new Exception("Ran out of argument registers: " + i);
    }

    public BigInteger returnRegister( int i )
        throws Exception
    {
        switch( i  ) {
        case 0:
            return r0;
        case 1:
            return r1;
        }

        throw new Exception("Ran out of return registers: " + i);
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
