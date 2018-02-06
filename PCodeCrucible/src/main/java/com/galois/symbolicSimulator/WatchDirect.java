package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

/**
 *  A "direct" watch is one that directly reads the value of the given
 *  location and reports its value.  This can be used to read the value
 *  of a register, or the value of memory at a constant location.
 */
public class WatchDirect extends Watch {
    public Varnode location;

    public WatchDirect( BigInteger instrAddress, String comment, Varnode location ) {
        super( instrAddress, comment );
        this.location = location;
    }
}
