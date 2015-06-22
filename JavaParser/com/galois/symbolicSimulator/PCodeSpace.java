package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class PCodeSpace {
	String name;
	int length;
	int wordsize;
	PCodeArchSpec arch;
	SortedMap<BigInteger,Integer> contents;
	boolean constSpace = false;

	public PCodeSpace(String n, PCodeArchSpec a) {
		name = n;
		if (n.equals("const")) constSpace = true;
		else contents = new TreeMap<BigInteger,Integer>();
		arch = a;
	}
	
	public String toString() {
		String ret = "Space " + name + ", length = " + length + ":\n";
		String asciiCol = "";
		if (name.equals("register")) {
			int count = 0;
			int lastKey = 0;
			for (Iterator<BigInteger> ks = contents.keySet().iterator() ; ks.hasNext(); ) {
				BigInteger k = ks.next();
				int thisKey = k.intValue();
				if (count % 8 == 0) {
					ret += "0x" + k.toString(16) + ":\t";
				} else if (thisKey > lastKey + 1) {
					ret += "0x" + k.toString(16) + ":\t";
					count++; // make space for these tags
				}
				Integer next = contents.get(k);
				ret += Integer.toHexString(0xff & next.intValue()) + "\t";
				if (++count % 8 == 0) {
					ret += "\n";
				}
				lastKey = thisKey;
			}
		} else {
			int count = 0;
			for (Iterator<BigInteger> ks = contents.keySet().iterator() ; ks.hasNext(); ) {
				BigInteger k = ks.next();
				Integer next = contents.get(k) & 0xff;
				int val = next.intValue() & 0xff;
				ret += "0x" + k.toString(16) + ": " + Integer.toHexString(val);
				if (val >= 32 && val <= 176) {
					asciiCol += String.valueOf((char)val);
				} // else { asciiCol += "."; }
				ret += " \t";
				if (++count % 8 == 0) {
					if (asciiCol.length() > 0)
						ret += "[" + asciiCol + "]";
					ret += "\n";
					asciiCol = "";
				}
			}
		}
		return ret;
	}
	
	public Integer getByte(BigInteger base, int offset) throws Exception {
		if (constSpace) {
			long retVal = 0;
			long baseVal = base.longValue();
			// base is our number, offset is the "ith" byte we're after
			if (arch.bigEndianP) {
				retVal = 0xffl & (baseVal >> (7-offset) * 8);
			} else {
				retVal = 0xffl & (baseVal >> (offset * 8));
			}
			return new Integer((int)(retVal & 0xff));
		} else {
			Integer ret = contents.get(base.add(BigInteger.valueOf(offset)));
			if (ret != null) {
				return ret;
			} else {
				throw new Exception("fetch from unitialized memory @ " + 
						base.add(BigInteger.valueOf(offset)).toString(16));
			}
		}
	}
}

// a read-only space that holds our code
// the macroOps array is a pointer into the microOps list, for each macro-op beginning
// the microOps list is all of the micro instructions
class PCodeCodeSpace extends PCodeSpace {
	List<PCodeOp> microOps;
	// "contents" indexes into the above list for the beginning of each macroOp
	int microIndex = 0;
	PCodeSpace ram;
	
	public PCodeCodeSpace(PCodeArchSpec a, PCodeSpace ram) {
		super("data_segment", a);
		microOps = new ArrayList<PCodeOp>(8196);
		this.ram = ram;
	}
	
	public PCodeOp fetch (int pc) {
		return microOps.get(pc);
	}
	
	public int microAddrOfVarnode(Varnode v) throws Exception {
		return microAddrOfMacroInstr(v.offset);
	}
	public int microAddrOfMacroInstr(BigInteger macroOffset) throws Exception {
		if (contents == null) {
			throw new Exception ("Null contents in varnode " + this.toString());
		}
		if (macroOffset == null) {
			throw new Exception ("Null PC in fetch instruction");
		}
		if (contents.containsKey(macroOffset))
			return contents.get(macroOffset);
		else {
			if (ram.contents.get(macroOffset) != null) {
				throw new Exception("Fetching non-decoded instruction @0x" + macroOffset.toString(16));
			}
			throw new Exception ("Fetch outside code space @0x" + macroOffset.toString(16));

		}
	}
	
	public Varnode addOp(PCodeOp op, PCodeSpace space, PCodeProgram prog) {
		// TODO: grow when needed
		Varnode ret = null;
		microOps.add(microIndex, op);
		if (op.uniq == 0) {
			contents.put(op.offset, new Integer(microIndex));
			ret = new Varnode(prog);
			ret.offset = op.offset;
			ret.space = space;
		}
		microIndex++;
		return ret;
	}
}
