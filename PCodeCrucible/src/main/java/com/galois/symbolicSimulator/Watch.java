package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

/** Superclass for various types of variable watches.
 *  When control-flow reaches the instruction address
 *  given in the Watch object, the value of a register
 *  or memory location of interest is printed to the 
 *  simulator's console, along with the associated comment.
 */
public class Watch {
    public BigInteger instrAddress;
    public String comment;

    protected Watch( BigInteger instrAddress, String comment ) {
        this.instrAddress = instrAddress;
        this.comment = comment;
    }
}
