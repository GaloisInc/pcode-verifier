package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

public final class TempAddrSpace extends AddrSpaceManager implements Iterable<Reg> {
    Map<BigInteger,Reg> regMap;
    Procedure proc;

    public TempAddrSpace( PCodeArchSpec arch, Procedure proc )
    {
	super(arch);
	this.proc = proc;
	regMap = new HashMap<BigInteger,Reg>();
    }

    public Iterator<Reg> iterator() {
	return regMap.values().iterator();
    }

    Reg getReg( BigInteger offset, int size )
	throws Exception
    {
	int bitsize = size * 8;

	Reg r = regMap.get( offset );
	if( r == null ) {
	    r = proc.newReg( Type.bitvector( bitsize ) );
	    regMap.put( offset, r );
	} else {
	    if( !(r.type().width() == bitsize) ) {
		throw new Exception( "PCode Temporary used at inconsistent sizes: " +
				     offset.toString(16) + " " + r.type().width() + " " + bitsize );
	    }
	}

	return r;
    }

    public void clearRegisters()
    {
	regMap = new HashMap<BigInteger,Reg>();
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

	return bb.read( getReg(offset, size) );
    }

    public void storeDirect( Block bb, BigInteger offset, int size, Expr e )
	throws Exception
    {
	if( size < 0 ) {
	    throw new UnsupportedOperationException( "invalid store size " + size  );
	}
	if( size == 0 ) { return; }
	if( !(e.type().isBitvector() && e.type().width() == 8*size) ) {
	    throw new UnsupportedOperationException( "type mismatch when storing to temporaray: " + size + " " + e.type().toString() );
	}

	bb.write( getReg(offset, size), e );
    }

    public Expr loadIndirect( Block bb, Varnode vn, int size )
	throws Exception
    {
	throw new Exception("Cannot load indirectly from a temporary address space");
    }

    public void storeIndirect( Block bb, Varnode vn, int size, Expr v )
	throws Exception
    {
	throw new Exception("Cannot store indirectly to a temporary address space");
    }
}
