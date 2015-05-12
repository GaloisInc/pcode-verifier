package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

public final class RegisterAddrSpace extends AddrSpaceManager {
    static final int regSize = 8; // registers are always 8 bits

    Procedure proc;
    long regWidth;
    Type regFileType;
    Reg registerFile;
    Simulator sim;
    BigInteger regFileSize;

    public RegisterAddrSpace( PCodeArchSpec arch, Procedure proc, int regWidth, Simulator sim )
    {
        super(arch);
        this.proc = proc;
        this.regWidth = regWidth;
        this.regFileSize = BigInteger.valueOf( 2 ).pow( regWidth );
        this.sim = sim;

        regFileType = getRegisterFileType( regWidth );
        registerFile = proc.newReg( regFileType );
    }

    public Reg getRegisterFile()
    {
        return registerFile;
    }

    public static Type getRegisterFileType( long regWidth )
    {
        return Type.wordMap( regWidth, Type.bitvector( regSize ) );
    }

    public <T extends Typed> T initialRegisters( ValueCreator<T> vc )
    {
        return vc.emptyWordMap( regWidth, Type.bitvector(regSize) );
    }

    Map<Integer, FunctionHandle> storeMap = new HashMap<Integer,FunctionHandle>();
    Map<Integer, FunctionHandle> loadMap = new HashMap<Integer,FunctionHandle>();


    public <T extends Typed>
        T storeRegister( ValueCreator<T> vc, T regs, BigInteger offset, int size, T e )
        throws Exception {

        if( size < 0 ) {
            throw new UnsupportedOperationException( "invalid store size " + size  );
        }
        if( size == 0 ) { return regs; }
        if( !(e.type().isBitvector() && e.type().width() == regSize*size) ) {
            throw new UnsupportedOperationException( "type mismatch when storing to register file: " + size + " " + e.type().toString() );
        }

        if( size == 1 ) {
            return vc.insertWordMap( vc.bvLiteral( regWidth, offset ), e, regs );
        }

        FunctionHandle hdl = storeMap.get( new Integer(size) );
        if( hdl == null ) {
            hdl = sim.getMultipartStoreHandle( regWidth, regSize, size );
            storeMap.put( new Integer(size), hdl );
        }

        T b = vc.boolLiteral( arch.bigEndianP );
        return vc.callHandle( hdl, b, vc.bvLiteral( regWidth, offset ), e, regs );
    }

    public <T extends Typed>
        T loadRegister( ValueCreator<T> vc, T regs, BigInteger offset, int size )
        throws Exception {

        if( size < 0 ) {
            throw new UnsupportedOperationException( "invalid load size " + size  );
        }
        if( size == 0 ) {
            return vc.bvLiteral( 0, BigInteger.ZERO );
        }

        if( size == 1 ) {
            return vc.lookupWordMapWithDefault( vc.bvLiteral( regWidth, offset ), regs, vc.bvLiteral( regSize, 0 ) );
        }

        FunctionHandle hdl = loadMap.get( new Integer(size) );
        if( hdl == null ) {
            hdl = sim.getMultipartLoadHandle( regWidth, regSize, size );
            loadMap.put( new Integer(size), hdl );
        }

        T b = vc.boolLiteral( arch.bigEndianP );
        return vc.callHandle( hdl, b, vc.bvLiteral( regWidth, offset ), regs, vc.justValue(vc.bvLiteral(regSize,0)) );
    }

    public Expr loadDirect( Block bb, BigInteger offset, int size )
        throws Exception
    {
        if( offset.add( BigInteger.valueOf(size) ).compareTo( regFileSize ) >= 0 ) {
            throw new UnsupportedOperationException( "invalid load from register file: out of bounds: " + offset + " " + size );
        }

        Expr regs = bb.read(registerFile);
        return loadRegister( bb, regs, offset, size);
    }

    public void storeDirect( Block bb, BigInteger offset, int size, Expr e )
        throws Exception
    {
        if( offset.add( BigInteger.valueOf(size) ).compareTo( regFileSize ) >= 0 ) {
            throw new UnsupportedOperationException( "invalid store to register file: out of bounds: " + offset + " " + size );
        }

        Expr regs = bb.read(registerFile);
        regs = storeRegister( bb, regs, offset, size, e );
        bb.write(registerFile, regs);
    }

    public Expr loadIndirect( Block bb, Varnode vn, int size )
        throws Exception
    {
        throw new Exception("Cannot load indirectly from a register address space");
    }

    public void storeIndirect( Block bb, Varnode vn, int size, Expr v )
        throws Exception
    {
        throw new Exception("Cannot store indirectly to a register address space");
    }
}
