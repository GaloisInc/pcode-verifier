package com.galois.symbolicSimulator;

public class PCodeArchSpec {
	int wordSize; // in bytes (so 64-bit machines have wordSize 8
	boolean bigEndianP;
	// what else? stack discipline? calling convention? register count?
	public int offsetSize = 4;
}
