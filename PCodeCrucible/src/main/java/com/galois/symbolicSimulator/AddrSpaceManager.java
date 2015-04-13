package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

abstract class AddrSpaceManager {
    PCodeArchSpec arch;

    public AddrSpaceManager( PCodeArchSpec arch )
    {
        this.arch = arch;
    }

    abstract public Expr loadDirect( Block bb, BigInteger offset, int size ) throws Exception;
    abstract public void storeDirect( Block bb, BigInteger offset, int size, Expr e ) throws Exception;

    abstract public Expr loadIndirect( Block bb, Varnode vn, int size ) throws Exception;
    abstract public void storeIndirect( Block bb, Varnode vn, int size, Expr e ) throws Exception;


    class CountUpIter implements Iterator<BigInteger>, Iterable<BigInteger> {
        public CountUpIter(BigInteger offset, int limit)
        {
            this.offset = offset;
            this.limit = limit;
            curr = 0;
        }

        int limit;
        int curr;
        BigInteger offset;

        public boolean hasNext()
        {
            return ( curr < limit );
        }

        public BigInteger next() {
            BigInteger e = offset.add( BigInteger.valueOf(curr) );
            curr++;
            return e;
        }

        public Iterator<BigInteger> iterator() {
            return this;
        }

        public void remove() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }
    }

    class CountDownIter implements Iterator<BigInteger>, Iterable<BigInteger> {
        public CountDownIter(BigInteger offset, int limit)
        {
            this.offset = offset;
            this.curr = limit-1;
        }

        BigInteger offset;
        int curr;

        public Iterator<BigInteger> iterator() {
            return this;
        }

        public boolean hasNext()
        {
            return ( curr >= 0 );
        }

        public BigInteger next() {
            BigInteger e = offset.add( BigInteger.valueOf(curr) );
            curr--;
            return e;
        }

        public void remove() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Enumerate the offsets of a multibyte value with base <code>offset</code>
     * in an order that gives the most significant byte offsets first and
     * least significant byte offsets last.
     */
    Iterable<BigInteger> indexEnumerator(final BigInteger offset, final int size) {
        if( arch.bigEndianP ) {
            return new CountUpIter(offset, size);
        } else {
            return new CountDownIter(offset, size);
        }
    }

}
