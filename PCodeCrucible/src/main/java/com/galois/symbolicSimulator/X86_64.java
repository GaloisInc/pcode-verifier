package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

// === Here are a bunch of definitions relevant to the X86_64 ABI ===
// These are reverse-engineered from the PCode samples we have.
public class X86_64 extends ABI {
    public static final int addrWidth = 64;
    public static final int addrBytes = 8;
    public static final int regWidth = 14; // number of bits required to address all the registers
    //static final int regWidth = 64; // number of bits required to address all the registers

    PCodeArchSpec arch;
    Simulator sim;

    Map<String, AddrSpaceManager> addrSpaces;
    ConstAddrSpace consts;
    RegisterAddrSpace regs;
    TempAddrSpace temps;
    RAMAddrSpace ram;

    public X86_64( PCodeArchSpec arch, Simulator sim )
    {
        this.arch = arch;
        this.sim = sim;
        int byteWidth = arch.wordSize;
        if( byteWidth * 8 != addrWidth ) {
            throw new IllegalArgumentException( "PCode program has incorrect word width for this ABI" );
        }
    }

    public int getAddrBytes() { return addrBytes; }

    // FIXME: Windows and OSX C compilers add leading underscores to "cdecl"
    // symbols; but other systems do not (apparently).
    public String mangle( String symbol )
    {
        return "_" + symbol;
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

        push( state, addrBytes, returnAddr );
    }

    // FIXME: actually implement this correctly!
    public SimulatorValue extractCallReturns( MachineState state )
        throws Exception
    {
        return state.readReg( returnRegister(0), addrBytes );
    }

    public void push( MachineState state, int bytes, SimulatorValue val )
        throws Exception
    {
        SimulatorValue stk = state.readReg( rsp, addrBytes );
        stk = state.sim.bvSub( stk, state.sim.bvLiteral( addrWidth, bytes ) );
        state.writeReg( rsp, addrBytes, stk );

        // System.out.println( "Pushing to " + stk.toString() + " " + addrBytes );
        state.poke( stk, addrBytes, val );
    }

    public SimulatorValue pop( MachineState state, int bytes )
        throws Exception
    {
        SimulatorValue stk = state.readReg( rsp, addrBytes );
        SimulatorValue x = state.peek( stk, bytes );
        stk = state.sim.bvAdd( stk, state.sim.bvLiteral( addrWidth, bytes ) );
        state.writeReg( rsp, addrBytes, stk );
        return x;
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
            return rdi;
        case 1:
            return rsi;
        case 2:
            return rdx;
        case 3:
            return rcx;
        case 4:
            return r8;
        case 5:
            return r9;
        }

        throw new Exception("Ran out of argument registers: " + i);
    }

    public BigInteger returnRegister( int i )
        throws Exception
    {
        switch( i  ) {
        case 0:
            return rax;
        case 1:
            return rdx;
        }

        throw new Exception("Ran out of return registers: " + i);
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
    static final BigInteger rcx = BigInteger.valueOf( 0x08l ); // offset value for %rcx
    static final BigInteger rdx = BigInteger.valueOf( 0x10l ); // offset value for %rdx
    static final BigInteger rbx = BigInteger.valueOf( 0x18l ); // offset value for %rbx
    static final BigInteger rsp = BigInteger.valueOf( 0x20l ); // offset value for %rsp
    static final BigInteger rbp = BigInteger.valueOf( 0x28l ); // offset value for %rbp
    static final BigInteger rsi = BigInteger.valueOf( 0x30l ); // offset value for %rsi
    static final BigInteger rdi = BigInteger.valueOf( 0x38l ); // offset value for %rdi

    static final BigInteger r8  = BigInteger.valueOf( 0x80l );
    static final BigInteger r9  = BigInteger.valueOf( 0x88l );
    static final BigInteger r10 = BigInteger.valueOf( 0x90l );
    static final BigInteger r11 = BigInteger.valueOf( 0x98l );
    static final BigInteger r12 = BigInteger.valueOf( 0xa0l );
    static final BigInteger r13 = BigInteger.valueOf( 0xa8l );
    static final BigInteger r14 = BigInteger.valueOf( 0xb0l );
    static final BigInteger r15 = BigInteger.valueOf( 0xb8l );

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
