package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

public final class RAMAddrSpace extends AddrSpaceManager {
    static final int cellSize = 8; // memory cells are always 8 bits

    Procedure proc;
    long addrWidth;
    Type ramType;
    Reg ramReg;

    Map<String, AddrSpaceManager> addrSpaces;


    public RAMAddrSpace( PCodeArchSpec arch, Procedure proc, long addrWidth, Map<String, AddrSpaceManager> addrSpaces )
    {
	super(arch);
	this.proc = proc;
	this.addrWidth = addrWidth;
	this.addrSpaces = addrSpaces;
	ramType = Type.wordMap( addrWidth, Type.bitvector(cellSize) );
	ramReg = proc.newReg( ramType );
    }

    public Reg getRAM()
    {
	return ramReg;
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

	Expr ram = bb.read(ramReg);
	Expr[] bytes = new Expr[size];
	int i = 0;
	for( BigInteger idx : indexEnumerator( offset, size ) ) {
	    bytes[i++] = bb.lookupWordMap( bb.bvLiteral( addrWidth, idx ), ram );
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

	Expr ram = bb.read(ramReg);
	int i = 0;
	for( BigInteger idx : indexEnumerator( offset, size ) ) {
	    Expr er = bb.bvSelect( cellSize * i, cellSize, e );
	    ram = bb.insertWordMap( bb.bvLiteral( addrWidth, idx), er, ram );
	    i++;
	}

	bb.write(ramReg, ram);
    }

    public Expr loadIndirect( Block bb, Varnode vn, int size )
	throws Exception
    {
	if( size < 0 ) {
	    throw new UnsupportedOperationException( "invalid load size " + size  );
	}
	if( size == 0 ) {
	    return bb.bvLiteral( 0, BigInteger.ZERO );
	}
	if( cellSize * vn.size > addrWidth ) {
	    throw new UnsupportedOperationException( "Cannot truncate address length in loadIndirect: " + vn.size + " " + size );
	}

	Expr baseAddr = addrSpaces.get( vn.space_name ).loadDirect( bb, vn.offset, vn.size );

	// If necessary, zero extend the address
	if( cellSize * vn.size < addrWidth ) {
	    baseAddr = bb.bvZext( baseAddr, addrWidth );
	}

	Expr ram = bb.read(ramReg);
	Expr[] bytes = new Expr[size];
	int i = 0;
	for( BigInteger idx : indexEnumerator( BigInteger.ZERO, size ) ) {
	    bytes[i++] = bb.lookupWordMap( bb.bvAdd( baseAddr, bb.bvLiteral( addrWidth, idx )), ram );
	}

	return bb.bvConcat(bytes);
    }

    public void storeIndirect( Block bb, Varnode vn, int size, Expr e )
	throws Exception
    {
	if( size < 0 ) {
	    throw new UnsupportedOperationException( "invalid store size " + size  );
	}
	if( cellSize * vn.size > addrWidth ) {
	    throw new UnsupportedOperationException( "Cannot truncate address length in loadIndirect: " + vn.size + " " + size );
	}

	Expr baseAddr = addrSpaces.get( vn.space_name ).loadDirect( bb, vn.offset, vn.size );

	// If necessary, zero extend the address
	if( cellSize * vn.size < addrWidth ) {
	    baseAddr = bb.bvZext( baseAddr, addrWidth );
	}

	Expr ram = bb.read(ramReg);
	int i = 0;
	for( BigInteger idx : indexEnumerator( BigInteger.ZERO, size ) ) {
	    Expr er = bb.bvSelect( cellSize * i, cellSize, e );
	    ram = bb.insertWordMap( bb.bvAdd( baseAddr, bb.bvLiteral( addrWidth, idx )), er, ram );
	    i++;
	}

	bb.write(ramReg, ram);
    }
}
