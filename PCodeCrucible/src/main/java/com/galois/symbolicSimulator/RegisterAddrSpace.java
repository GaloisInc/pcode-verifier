package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

public final class RegisterAddrSpace extends AddrSpaceManager {
    Map<BigInteger,Reg> regMap;
    Procedure proc;
    static final int regSize = 8; // registers are always 8-bits

    public RegisterAddrSpace( PCodeArchSpec arch, Procedure proc )
    {
	super(arch);
	this.proc = proc;
	regMap = new HashMap<BigInteger,Reg>();
    }

    Reg getReg( BigInteger offset ) throws Exception
    {
	Reg r = regMap.get( offset );
	if( r == null ) {
	    r = proc.newReg( Type.bitvector(regSize) );
	    regMap.put( offset, r );
	}

	return r;
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

	Expr[] bytes = new Expr[size];
	int i = 0;
	for( BigInteger idx : indexEnumerator( offset, size ) ) {
	    bytes[i++] = bb.read( getReg(idx) );
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
	
	int i = 0;
	for( BigInteger idx : indexEnumerator( offset, size ) ) {
	    Expr er = bb.bvSelect( regSize * i, regSize, e );
	    bb.write( getReg(idx), er );
	    i++;
	}
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
