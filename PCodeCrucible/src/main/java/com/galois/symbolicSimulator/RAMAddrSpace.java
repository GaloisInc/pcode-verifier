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

    public <T extends Typed> T initialRam( ValueCreator<T> vc )
    {
        return vc.emptyWordMap( addrWidth, Type.bitvector(cellSize) );
    }

    public <T extends Typed> T initialRam( ValueCreator<T> vc, PCodeSpace dataSegment )
        throws Exception
    {
        T initram = initialRam( vc );
        return setupDataSegment( vc, initram, dataSegment );
    }

    public <T extends Typed>
        T setupDataSegment( ValueCreator<T> vc, T ram, PCodeSpace dataSegment )
        throws Exception {

        SortedMap<BigInteger, Integer> ramMap = dataSegment.contents;

        for( BigInteger offset : ramMap.keySet() ) {
            int val = ramMap.get(offset).intValue();
            ram = vc.insertWordMap( vc.bvLiteral( addrWidth, offset ),
                                    vc.bvLiteral( cellSize, val ),
                                    ram );
        }

        return ram;
    }

    public <T extends Typed>
        T storeRAM( ValueCreator<T> vc, T ram, T offset, int size, T e )
        throws Exception {

        if( size < 0 ) {
            throw new UnsupportedOperationException( "invalid store size " + size  );
        }
        if( size == 0 ) { return ram; }

        // Enumerate from MSB first (at i = 0) to LSB (at i = size - 1)
        int i = 0;
        for( BigInteger idx : indexEnumerator( BigInteger.ZERO, size ) ) {
            T er = vc.bvSelect( cellSize * (size - i - 1), cellSize, e );
            ram = vc.insertWordMap( vc.bvAdd( offset, vc.bvLiteral( addrWidth, idx ) ), er, ram );
            i++;
        }

        return ram;
    }

    public <T extends Typed>
        T loadRAM( ValueCreator<T> vc, T ram, T offset, int size )
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
        for( BigInteger idx : indexEnumerator( BigInteger.ZERO, size ) ) {
            bytes[i++] = vc.lookupWordMap( vc.bvAdd( offset, vc.bvLiteral( addrWidth, idx ) ), ram );
        }

        return vc.bvConcat(bytes);
    }

    public Expr loadDirect( Block bb, BigInteger offset, int size )
        throws Exception
    {
        Expr ram = bb.read(ramReg);
        return loadRAM( bb, ram, bb.bvLiteral( addrWidth, offset ), size );
    }

    public void storeDirect( Block bb, BigInteger offset, int size, Expr e )
        throws Exception
    {
        Expr ram = bb.read(ramReg);
        ram = storeRAM( bb, ram, bb.bvLiteral( addrWidth, offset ), size, e );
        bb.write(ramReg, ram);
    }


    public Expr loadAddress( Block bb, Varnode vn, int size )
        throws Exception
    {
        if( cellSize * vn.size > addrWidth ) {
            throw new UnsupportedOperationException( "Cannot truncate address length in loadIndirect: " + vn.size + " " + size );
        }

        Expr baseAddr = addrSpaces.get( vn.space_name ).loadDirect( bb, vn.offset, vn.size );

        // If necessary, zero extend the address
        if( cellSize * vn.size < addrWidth ) {
            baseAddr = bb.bvZext( baseAddr, addrWidth );
        }

        return baseAddr;
    }

    public Expr loadIndirect( Block bb, Varnode vn, int size )
        throws Exception
    {
        Expr baseAddr = loadAddress( bb, vn, size );
        Expr ram = bb.read(ramReg);
        return loadRAM( bb, ram, baseAddr, size );
    }

    public void storeIndirect( Block bb, Varnode vn, int size, Expr e )
        throws Exception
    {
        Expr baseAddr = loadAddress( bb, vn, size );
        Expr ram = bb.read(ramReg);
        ram = storeRAM( bb, ram, baseAddr, size, e );
        bb.write(ramReg, ram);
    }
}
