package com.galois.symbolicSimulator;

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
	SortedMap<Integer,Integer> contents;
	boolean constSpace = false;
	boolean regSpace = false;

	public PCodeSpace(String n, PCodeArchSpec a) {
		name = n;
		if (n.equals("const")) constSpace = true;
		else contents = new TreeMap<Integer,Integer>();
		if (n.equals("register")) regSpace = true;
		arch = a;
	}
	public void ensureCapacity(long offset) {
		// nothing to do with Hashtable - with an array, hod to realloc and copy
	}	
	
	public String toString() {
		String ret = "Space " + name + ", length = " + length + ":\n";
		String asciiCol = "";
		if (name.equals("register")) {
			int count = 0;
			int lastKey = 0;
			for (Iterator<Integer> ks = contents.keySet().iterator() ; ks.hasNext(); ) {
				Integer k = ks.next();
				int thisKey = k.intValue();
				if (count % 8 == 0 || thisKey > lastKey + 1) {
					ret += "0x" + Integer.toHexString(thisKey) + ":\t";
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
			for (Iterator<Integer> ks = contents.keySet().iterator() ; ks.hasNext(); ) {
				Integer k = ks.next();
				Integer next = contents.get(k) & 0xff;
				int val = next.intValue() & 0xff;
				ret += "0x" + Integer.toHexString(k.intValue()) + ": " + Integer.toHexString(val);
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
	
	public Integer getByte(long base, int offset) throws Exception {
		if (constSpace) {
			long retVal = 0;
			// base is our number, offset is the "ith" byte we're after
			if (arch.bigEndianP) {
				retVal = 0xffl & (base >> (7-offset) * 8);
			} else {
				retVal = 0xffl & (base >> (offset * 8));
			}
			return new Integer((int)(retVal & 0xff));
		} else {
			Integer ret = contents.get(new Integer((int)base + offset));
			if (ret != null) {
				return ret;
			} else {
				throw new Exception("fetch from unitialized memory @ " + Integer.toHexString(((int)base + offset)));
			}
		}
	}
}

// a read-only space that holds our code
// the macroOps array is a pointer into the microOps list, for each macro-op beginning
// the microOps list is all of the micro instructions
class PCodeCodeSpace extends PCodeSpace {
	List<PCodeOp> microOps;
	int[] macroOps; // indexes into the above list for the beginning of each macroOp
	int microIndex = 0;
	
	public PCodeCodeSpace(PCodeArchSpec a) {
		super("data_segment", a);
		microOps = new ArrayList<>(8196);
		macroOps = new int[128]; // TODO: handle growth, parse length ahead of time, or change to List
	}
	
	public PCodeOp fetch (int pc) {
		return microOps.get(pc);
	}
	
	public int microAddrOfVarnode(Varnode v) {
		return macroOps[(int) v.offset];
	}
	public int microAddrOfMacroInstr(long macroOffset) throws Exception {
		if (macroOffset < macroOps.length) {
			return macroOps[(int)macroOffset];
		} 
		throw new Exception("Attempted to fetch opcode out of range @ " + Long.toHexString(macroOffset));
	}
	
	public Varnode addOp(PCodeOp op, PCodeSpace space, PCodeProgram prog) {
		// TODO: grow when needed
		Varnode ret = null;
		microOps.add(microIndex, op);
		if (op.uniq == 0) {
			macroOps[op.offset] = microIndex;
			ret = new Varnode(prog);
			ret.offset = op.offset;
			ret.space = space;
		}
		microIndex++;
		return ret;
	}
}