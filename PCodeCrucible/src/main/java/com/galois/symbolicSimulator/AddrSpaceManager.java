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
}
