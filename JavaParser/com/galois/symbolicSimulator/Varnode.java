package com.galois.symbolicSimulator;

// The PCode abstraction for memory - registers, ram, and temporary values
public class Varnode {
	PCodeArchSpec arch;
	PCodeProgram p;
	String space_name;
	PCodeSpace space;
	long offset;
	int size;
	

	public Varnode (PCodeProgram p) {
		arch = null;
		space_name = null;
		offset = -1;
		size = -1;
		p.addVarnode(this); 
	}
	
	// When the parser constructs a varnode, its space is saved by name,
	// later, if we want to run the program, the interpreter calls "loadVarnodes"
	// to save the PCodeSpaces in the "space" field
	public Varnode (PCodeProgram p, String spn, long off, int sz) {
		arch = p.archSpec;
		space_name = spn;
		offset = off;
		size = sz;
		p.addVarnode(this);
	}
	// This variant creates a Varnode when we have the PCodeSpace in hand,
	// so doesn't bother adding it to the program's varnode list
	public Varnode (PCodeSpace sp, long off, int sz) {
		arch = sp.arch;
		space = sp;
		space_name = sp.name;
		offset = off;
		size = sz;
	}

	public String toString() {
		// TODO: should include the contents here - iterate through size bytes @ offset
		return "(" + space_name + size +")" + "0x" + Integer.toHexString((int)offset);
	}
	
	int fetchByte(int i) throws Exception {
		return space.getByte(offset,  i);
	}
	
	void storeByte(int i, int val) {
		if (space.constSpace) {
			System.out.println("storeByte to Const Space?");
			// TODO - should exit here? won't happen with proper PCode...
		}
		space.contents.put((int) (offset+i), val & 0xff);
	}
	// this varnode is an array of bytes within a space.
	// fetch returns "size-bytes" from offset interpreted as a word
	// if big-endian, the zero'th byte is the most-significant byte
	//    otherwise the zero'th byte is the least-significant
	// byte0 | byte1 | byte2 ... byteN
	long fetch() {
		if (space.constSpace) {
			return offset;
		}
		long ret = 0;
		// in terms of fenceposts, the final + is not followed by a shift
		// so if we do the shifts first then the +, that should do the trick
		if (arch.bigEndianP) {
			for (long b = offset; b < offset+size; b++) {
				ret = ret << 8;
				Integer v = space.contents.get(new Integer((int)b));
				if (v == null) {
					System.out.println("Warning: fetching uninitialized word");
				} else {
					ret += v.intValue();
				}
			}
		} else {
			for (long b = offset + size - 1; b >= offset; b--) {
				ret = ret << 8;
				Integer v = space.contents.get(new Integer((int)b));
				if (v == null) {
					System.out.println("Warning: fetching uninitialized word @" +
							Integer.toHexString((int)b));
				} else {
					ret += v.intValue();
				}
			}
		}
		return ret;
	}
	
	// stores up to 8 bytes of an immediate value into this varnode
	// TODO: Java's signed longs will cause a problem if we're dealing with really
	//       big numbers...
	void storeImmediate(long val) throws Exception {
		PCodeSpace destSpace = this.space;
		if (space.constSpace) {
			throw new Exception("storing into constant-space");
		}
		if (offset + size >= destSpace.length) {
			destSpace.length = (int) offset + size;
		}
		if (arch.bigEndianP) {
			for (long b = offset + size - 1; b >= offset; b--) {
				destSpace.contents.put(new Integer((int) b), (int) (val & 0xff));
				val = val >> 8;
			}
		} else {
			for (long b = offset; b < offset+size; b++) {
				destSpace.contents.put(new Integer((int) b), (int) (val & 0xff));
				val = val >> 8;
			}
		}
	}
	
	// copies "size" bytes from src into data segment ("ram") memory pointed to by "this"
	//    [this] <- src
	void storeIndirect(Varnode src, PCodeSpace destSpace) throws Exception {
		// assert(this.isRegister);
		long destOffset = this.fetch();
		Varnode ramPointer = new Varnode(destSpace, destOffset, src.size);
		src.copyTo(ramPointer);
	}
	
	// copies "size" bytes from the data segment pointed to by src into "this" (register)
	//  this = [src]
	void loadIndirect(Varnode src, PCodeSpace sourceSpace) throws Exception {
		// assert this.isRegister
		Varnode ramPointer = new Varnode(sourceSpace, src.fetch(), src.size);
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
				System.out.println("Warning: copyTo from uninitialized word @" +
						Integer.toHexString((int)(this.offset + i)));
			}
			dest.space.contents.put(new Integer((int) (dest.offset + i)), srcVal);
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

