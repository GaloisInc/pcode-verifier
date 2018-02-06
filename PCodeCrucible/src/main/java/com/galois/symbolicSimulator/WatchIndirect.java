package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

/**
 *  An "indirect" watch is one that reports the value of a memory location
 *  that is reached by a series (at least, but perhaps more than one) of
 *  pointer indirections.  The base value is the starting location for the
 *  watch.  It will typically be the value of a register.  The value of
 *  the first offset in the given offset array is added to the base value,
 *  and the resulting address is used to load the next value.  This continues
 *  happening until there is only one offset value remaining.  The final offset
 *  value is used to load the value that will be reported.
 * 
 *  Each load in the chain, except the last, is loaded at the pointer width
 *  of the current architecture.  The final load is performed at the given size.
 */
public class WatchIndirect extends Watch {
    public Varnode base;
    public String space_id;
    public BigInteger offsets[];
    public int size;

    public WatchIndirect( BigInteger instrAddress,
                          String comment,
                          Varnode base,
                          String space_id,
                          BigInteger offsets[],
                          int size ) {
        super( instrAddress, comment );

        this.base = base;
        this.space_id = space_id;
        this.offsets = offsets;
        this.size = size;
    }
}
