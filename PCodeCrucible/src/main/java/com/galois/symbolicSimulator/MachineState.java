package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class MachineState {
    Simulator sim;
    Procedure proc;
    PCodeProgram prog;
    ABI abi;
    int addrWidth;
    int addrBytes;

    RegisterAddrSpace regs;
    RAMAddrSpace ram;

    public SimulatorValue currentPC;
    public SimulatorValue currentRegs;
    public SimulatorValue currentRam;

    public MachineState( Simulator sim, Procedure proc,
                         PCodeProgram prog, ABI abi )
        throws Exception
    {
        this.sim  = sim;
        this.proc = proc;
        this.prog = prog;
        this.abi  = abi;
        this.addrBytes = prog.archSpec.wordSize;
        this.addrWidth = addrBytes * 8;

        ram = abi.getRAM();
        regs = abi.getRegisters();

        currentPC   = sim.bvLiteral( addrWidth, 0 );
        currentRegs = regs.initialRegisters( sim );
        currentRam  = ram.initialRam( sim, prog.dataSegment );
    }

    public ABI getABI()
    {
        return abi;
    }

    public SimulatorValue makeWord( BigInteger val ) {
        return sim.bvLiteral( addrWidth, val );
    }

    public SimulatorValue makeWord( long val ) {
        return makeWord( BigInteger.valueOf( val ) );
    }

    public SimulatorValue getEntryPoint( String symbol )
    {
        String mangled = abi.mangle( symbol );
        PCodeFunction fn = prog.lookupFunction( mangled );
        if( fn == null ) {
            throw new IllegalArgumentException( "Symbol not found: " + symbol );
        }

        return sim.bvLiteral( addrWidth, fn.macroEntryPoint.offset );
    }

    public SimulatorValue callFunction( SimulatorValue returnAddr, String name, SimulatorValue... args )
        throws Exception
    {
        SimulatorValue entryPoint = getEntryPoint( name );
        abi.setupCallFrame( this, returnAddr, args );
        currentPC = entryPoint;
        fetchAndExecute();
        return abi.extractCallReturns( this );
    }

    public void fetchAndExecute( )
    {
        SimulatorValue v = sim.runCall( proc.getHandle(),
                                        currentPC,
                                        currentRegs,
                                        currentRam );

        currentPC   = sim.structGet( 0, v );
        currentRegs = sim.structGet( 1, v );
        currentRam  = sim.structGet( 2, v );
    }

    public void initStack( BigInteger bottomOfStack )
        throws Exception
    {
        SimulatorValue bottomOfStackVal =
            sim.bvLiteral( addrWidth, bottomOfStack );

        writeReg( abi.stackRegister(), addrBytes, bottomOfStackVal );
        if( abi.frameRegister() != null ) {
            writeReg( abi.frameRegister(), addrBytes, bottomOfStackVal );
        }
    }

    public SimulatorValue readReg( BigInteger regOffset, int bytes )
        throws Exception
    {
        return regs.loadRegister( sim, currentRegs, regOffset, bytes );
    }

    public void writeReg( BigInteger regOffset, int bytes, SimulatorValue val )
        throws Exception
    {
        currentRegs = regs.storeRegister( sim, currentRegs, regOffset, bytes, val );
    }

    public SimulatorValue peek( SimulatorValue addr, int bytes )
        throws Exception
    {
        return ram.loadRAM( sim, currentRam, addr, bytes );
    }

    public void poke( SimulatorValue addr, int bytes, SimulatorValue val )
        throws Exception
    {
        currentRam = ram.storeRAM( sim, currentRam, addr, bytes, val );
    }
}
