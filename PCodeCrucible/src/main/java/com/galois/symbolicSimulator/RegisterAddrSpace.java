package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

public final class RegisterAddrSpace extends AddrSpaceManager {
    static final int regSize = 8; // registers are always 8 bits

    Procedure proc;
    long regFileSize;
    Type regFileType;
    Reg registerFile;


    public RegisterAddrSpace( PCodeArchSpec arch, Procedure proc, long regFileSize )
    {
        super(arch);
        this.proc = proc;
        this.regFileSize = regFileSize;
        regFileType = Type.vector( Type.bitvector(regSize) );
        registerFile = proc.newReg( regFileType );
    }

    public Reg getRegisterFile()
    {
        return registerFile;
    }


    public <T extends Typed>
        T initialRegisters( ValueCreator<T> vc )
        throws Exception
    {
        return vc.vectorReplicate( vc.natLiteral( regFileSize ), vc.bvLiteral( regSize, 0 ) );
    }

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

        // Enumerate from MSB first (at i = 0) to LSB (at i = size - 1)
        int i = 0;
        for( BigInteger idx : indexEnumerator( offset, size ) ) {
            T er = vc.bvSelect( regSize * (size - i - 1), regSize, e );
            regs = vc.vectorSetEntry( regs, vc.natLiteral(idx), er );
            i++;
        }

        return regs;
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

        // Some ugly casting stuff here to work around the weakness of Java's generics system
        T[] bytes = (T[]) new Typed[size];

        // Enumerate from MSB first (at i = 0) to LSB (at i = size - 1)
        int i = 0;
        for( BigInteger idx : indexEnumerator( offset, size ) ) {
            bytes[i++] = vc.vectorGetEntry( regs, vc.natLiteral(idx) );
        }

        return vc.bvConcat(bytes);
    }


    public Expr loadDirect( Block bb, BigInteger offset, int size )
        throws Exception
    {
        if( offset.intValue() + size >= regFileSize ) {
            throw new UnsupportedOperationException( "invalid load from register file: out of bounds: " + offset + " " + size );
        }

        Expr regs = bb.read(registerFile);
        return loadRegister( bb, regs, offset, size);
    }

    public void storeDirect( Block bb, BigInteger offset, int size, Expr e )
        throws Exception
    {
        if( offset.intValue() + size >= regFileSize ) {
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
