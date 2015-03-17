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

    public Expr loadDirect( Block bb, BigInteger offset, int size )
	throws Exception
    {
	if( size < 0 ) { 
	    throw new UnsupportedOperationException( "invalid load size " + size  );
	}
	if( size == 0 ) {
	    return bb.bvLiteral( 0, BigInteger.ZERO );
	}
	if( offset.intValue() + size >= regFileSize ) {
	    throw new UnsupportedOperationException( "invalid load from register file: out of bounds: " + offset + " " + size );
	}

	Expr regs = bb.read (registerFile);
	Expr[] bytes = new Expr[size];
	int i = 0;
	for( BigInteger idx : indexEnumerator( offset, size ) ) {
	    bytes[i++] = bb.vectorGetEntry( regs, bb.natLiteral(idx) );
	}
	
	return bb.bvConcat(bytes);
    }

    public void storeDirect( Block bb, BigInteger offset, int size, Expr e )
	throws Exception
    {
	if( size < 0 ) { 
	    throw new UnsupportedOperationException( "invalid store size " + size  );
	}
	if( size == 0 ) { return; }
	if( offset.intValue() + size >= regFileSize ) {
	    throw new UnsupportedOperationException( "invalid store to register file: out of bounds: " + offset + " " + size );
	}
	if( !(e.type().isBitvector() && e.type().width() == regSize*size) ) {
	    throw new UnsupportedOperationException( "type mismatch when storing to register file: " + size + " " + e.type().toString() );
	}
	
	Expr regs = bb.read(registerFile);

	int i = 0;
	for( BigInteger idx : indexEnumerator( offset, size ) ) {
	    Expr er = bb.bvSelect( regSize * i, regSize, e );
	    regs = bb.vectorSetEntry( regs, bb.natLiteral(idx), er );
	    i++;
	}

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
