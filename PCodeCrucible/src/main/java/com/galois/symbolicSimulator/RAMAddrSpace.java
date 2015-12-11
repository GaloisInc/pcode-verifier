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
    Simulator sim;

    Map<String, AddrSpaceManager> addrSpaces;


    public RAMAddrSpace( PCodeArchSpec arch, Procedure proc, long addrWidth, Map<String, AddrSpaceManager> addrSpaces, Simulator sim )
    {
        super(arch);
        this.proc = proc;
        this.addrWidth = addrWidth;
        this.addrSpaces = addrSpaces;
        this.sim = sim;
        ramType = getRAMType( addrWidth );
        ramReg = proc.newReg( ramType );
    }

    public Reg getRAM()
    {
        return ramReg;
    }

    public static Type getRAMType( long addrWidth )
    {
        return Type.wordMap( addrWidth, Type.bitvector(cellSize) );
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


    Map<Integer, FunctionHandle> storeMap = new HashMap<Integer,FunctionHandle>();
    Map<Integer, FunctionHandle> loadMap = new HashMap<Integer,FunctionHandle>();

    public <T extends Typed>
        T storeRAM( ValueCreator<T> vc, T ram, T offset, int size, T e )
        throws Exception {

        if( size < 0 ) {
            throw new UnsupportedOperationException( "invalid store size " + size  );
        }
        if( size == 0 ) { return ram; }

        if( size == 1 ) {
            return vc.insertWordMap( offset, e, ram );
        }

        FunctionHandle hdl = storeMap.get( new Integer(size) );
        if( hdl == null ) {
            hdl = sim.getMultipartStoreHandle( addrWidth, cellSize, size );
            storeMap.put( new Integer(size), hdl );
        }

        T b = vc.boolLiteral( arch.bigEndianP );
        return vc.callHandle( hdl, b, offset, e, ram );
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

        if( size == 1 ) {
            return vc.lookupWordMap( offset, ram );
        }

        FunctionHandle hdl = loadMap.get( new Integer(size) );
        if( hdl == null ) {
            hdl = sim.getMultipartLoadHandle( addrWidth, cellSize, size );
            loadMap.put( new Integer(size), hdl );
        }

        T b = vc.boolLiteral( arch.bigEndianP );
        return vc.callHandle( hdl, b, offset, ram, vc.nothingValue( Type.bitvector(cellSize) ) );
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


    public Expr peek( Block bb, Expr addr, int size )
        throws Exception
    {
        Expr ram = bb.read(ramReg);
        return loadRAM( bb, ram, addr, size );
    }

    public void poke( Block bb, Expr addr, int size, Expr e )
        throws Exception
    {
        Expr ram = bb.read(ramReg);
        ram = storeRAM( bb, ram, addr, size, e );
        bb.write(ramReg, ram);
    }
}
