package com.galois.symbolicSimulator;

import java.math.BigInteger;

// The PCode abstraction for memory - registers, ram, and temporary values
public class Varnode {
	PCodeArchSpec arch;
	PCodeProgram p;
	String space_name;
	PCodeSpace space;
	BigInteger offset;
	int size;
	

	public Varnode (PCodeProgram p) {
		arch = null;
		space_name = null;
		offset = null;
		size = -1;
		p.addVarnode(this); 
	}
	
	// When the parser constructs a varnode, its space is saved by name,
	// later, if we want to run the program, the interpreter calls "loadVarnodes"
	// to save the PCodeSpaces in the "space" field
	public Varnode (PCodeProgram p, String spn, BigInteger off, int sz) {
		arch = p.archSpec;
		space_name = spn;
		offset = off;
		size = sz;
		p.addVarnode(this);
	}
	// This variant creates a Varnode when we have the PCodeSpace in hand,
	// so doesn't bother adding it to the program's varnode list
	public Varnode (PCodeSpace sp, BigInteger destOffset, int sz) {
		arch = sp.arch;
		space = sp;
		space_name = sp.name;
		offset = destOffset;
		size = sz;
	}

	public String toString() {
		// TODO: should include the contents here - iterate through size bytes @ offset
		return "(" + space_name + size +")" + "0x" + offset.toString(16);
	}
	
	int fetchByte(int i) throws Exception {
		return space.getByte(offset,  i);
	}
	
	void storeByte(int i, int val) {
		if (space.constSpace) {
			System.out.println("storeByte to Const Space?");
			// TODO - should exit here? won't happen with proper PCode...
		}
		space.contents.put(offset.add(BigInteger.valueOf(i)), val & 0xff);
	}

	// this varnode is an array of bytes within a space.
	// fetch returns "size-bytes" from offset interpreted as a word
	// if big-endian, the zero'th byte is the most-significant byte
	//    otherwise the zero'th byte is the least-significant
	// byte0 | byte1 | byte2 ... byteN
	BigInteger fetchUnsigned() {
		if (space.constSpace) {
			return offset;
		}
		BigInteger ret = BigInteger.ZERO;
		// in terms of fenceposts, the final + is not followed by a shift
		// so if we do the shifts first then the +, that should do the trick
		int index = 0;
		for (int i = 0; i < size; i++) {
			if (arch.bigEndianP) {
				index = i;
			} else {
				index = size - i - 1;
			}
			ret = ret.shiftLeft(8);
			Integer v = space.contents.get(offset.add(BigInteger.valueOf(index)));
			if (v == null) {
				System.out.println("Warning: fetching uninitialized word");
			} else {
				ret = ret.add(BigInteger.valueOf(v.intValue()));
			}
		}
		return ret;
	}
	
	BigInteger fetchSigned() {
		if (space.constSpace) {
			return offset;
		}
		BigInteger ret = BigInteger.ZERO;
		boolean gotSignBit = false;
		int signBit = 0;
		int index = 0;
		for (int i = 0; i < size; i++) {
			if (arch.bigEndianP) {
				index = i;
			} else {
				index = size - i - 1;
			}
			ret = ret.shiftLeft(8);
			Integer v = space.contents.get(offset.add(BigInteger.valueOf(index)));
			if (v == null) {
				System.out.println("Warning: fetching uninitialized word");
			} else {
				if (!gotSignBit) {
					signBit = (v.intValue() >> 7) & 1;
					gotSignBit = true;
				}
				int bitValue = v.intValue();
				if (signBit > 0) {
					bitValue = ~bitValue;
				}
				ret = ret.or(BigInteger.valueOf(bitValue & 0xff));
			}
		}
		if (signBit > 0) {
			// two's complement the resulting magnitude
			ret = ret.negate().subtract(BigInteger.ONE);
		}
		return ret;
	}

	void storeImmediateUnsigned(BigInteger val) throws Exception {
		storeImmediateUnsigned(val.longValueExact());
	}

	// stores up to 8 bytes of an immediate value into this varnode
	// TODO: Java's signed longs will cause a problem if we're dealing with really
	//       big numbers...
	void storeImmediateUnsigned(long val) throws Exception {
		PCodeSpace destSpace = this.space;
		if (space.constSpace) {
			throw new Exception("storing into constant-space");
		}
		if (arch.bigEndianP) {
			// for (long b = offset + size - 1; b >= offset; b--) {
			for (int i = size - 1; i >= 0; i--) {
				destSpace.contents.put(offset.add(BigInteger.valueOf(i)), (int) (val & 0xff));
				val = val >> 8;
			}
		} else {
			for (int i = 0; i < size; i++) {
				destSpace.contents.put(offset.add(BigInteger.valueOf(i)), (int) (val & 0xff));
				val = val >> 8;
			}
		}
	}

	void storeImmediateSigned(long val) throws Exception {
		PCodeSpace destSpace = this.space;
		if (space.constSpace) {
			throw new Exception("storing into constant-space");
		}
		boolean negativeP = false;
		if (val < 0) {
			negativeP = true;
			val = (val + 1) * -1;
		}
		if (arch.bigEndianP) {
			// for (long b = offset + size - 1; b >= offset; b--) {
			for (int i = size - 1; i >= 0; i--) {
				int byteVal = (int) (negativeP ? (~val & 0xff) : val & 0xff);
				destSpace.contents.put(offset.add(BigInteger.valueOf(i)), byteVal);
				val = val >> 8;
			}
		} else {
			for (int i = 0; i < size; i++) {
				int byteVal = (int) (negativeP ? (~val & 0xff) : val & 0xff);
				destSpace.contents.put(offset.add(BigInteger.valueOf(i)), byteVal);
				val = val >> 8;
			}
		}
	}

	// copies "size" bytes from src into data segment ("ram") memory pointed to by "this"
	//    [this] <- src
	void storeIndirect(Varnode src, PCodeSpace destSpace) throws Exception {
		// assert(this.isRegister);
		BigInteger destOffset = this.fetchUnsigned();
		Varnode ramPointer = new Varnode(destSpace, destOffset, src.size);
		src.copyTo(ramPointer);
	}
	
	// copies "size" bytes from the data segment pointed to by src into "this" (register)
	//  this = [src]
	void loadIndirect(Varnode src, PCodeSpace sourceSpace) throws Exception {
		// assert this.isRegister?
		// NOPE - this can be a unique pseudo-register (weird)
		// also interesting, sizes don't necessarily match - so this.size is the
		// number of bytes we want.
		Varnode ramPointer = new Varnode(sourceSpace, src.fetchUnsigned(), this.size);
		ramPointer.copyTo(this);
	}

	// copies "size" bytes from "this" to dest
	void copyTo(Varnode dest) throws Exception {
		if (dest.space.constSpace) {
			throw new Exception("storing into constant-space");
		}
		if (this.size != dest.size){
			throw new Exception("mismatched size in copy");
		}
		// TODO: won't work for copying from Const space...which doesn't yet support byte access
		for (long i = 0; i < size; i++) {
			Integer srcVal = space.getByte(this.offset, (int) i);
			if (srcVal == null) {
				srcVal = new Integer((int) 0);
				System.out.println("Warning: copyTo from uninitialized word @0x" +
						offset.add(BigInteger.valueOf(i)).toString(16));
			}
			dest.space.contents.put(dest.offset.add(BigInteger.valueOf(i)), srcVal);
		}
	}

	// copy bytes from source to this
	void copyBytes(Varnode src) throws Exception {
		assert this != null : "dest is null in copy";
		assert src != null : "src is null in copy";
		assert size == src.size : "size mismatch in copy";
		for (int i = 0; i < src.size; i++) {
			storeByte(i, src.fetchByte(i));
		}
	}	
}

