package com.galois.symbolicSimulator;

public class PCodeArchSpec {
	int wordSize = 4;
        // in bytes (so 64-bit machines have wordSize 8)
        // default to 4-bytes (32-bits)

	boolean bigEndianP;
	// what else? stack discipline? calling convention? register count?
}
