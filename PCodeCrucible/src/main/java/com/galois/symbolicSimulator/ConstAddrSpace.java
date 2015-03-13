package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

public final class ConstAddrSpace extends AddrSpaceManager {

    public ConstAddrSpace( PCodeArchSpec arch )
    {
	super(arch);
    }

    public Expr loadDirect( Block bb, BigInteger offset, int size )
	throws Exception
    {
	int bitsize = size*8;

	if( offset.compareTo(BigInteger.ZERO) >= 0
	    &&
	    offset.compareTo(BigInteger.valueOf(2).pow(bitsize)) < 0 ) {
	    return new BitvectorValue(bitsize, offset);
	} else {
	    throw new Exception("constant does not fit in claimed bit width");
	}
    }

    public void storeDirect( Block bb, BigInteger offset, int size, Expr e )
	throws Exception
    {
	throw new Exception("Cannot store directly to the constant address space");
    }

    public Expr loadIndirect( Block bb, Varnode vn, int size )
	throws Exception
    {
	throw new Exception("Cannot load indirectly from the constant address space");
    }

    public void storeIndirect( Block bb, Varnode vn, int size, Expr e ) 
	throws Exception	
    {
	throw new Exception("Cannot store indirectly to the constant address space");
    }
}
