package com.galois.symbolicSimulator;

import java.math.BigInteger;


import org.junit.*;
import static org.junit.Assert.*;

import com.galois.symbolicSimulator.PCodeOp.PCodeOpCode;

public class TestParser {
	static PCodeParser parser;
	static PCodeMachineState m;
	static PCodeProgram program;
	
	@BeforeClass
	public static void initialize() {
		parser = new PCodeParser("fib.xml");
		parser.parseProgram(parser.topNodes);
		program = parser.program;
		m = new PCodeMachineState(program);
	}
	
	@Test
	public void testParser() {
		assertNotNull("Parser failed to produce a program", program);
		PCodeFunction fibFun = program.lookupFunction("_fib");
		assertNotNull("Unable to find _fib", fibFun);
	}

	@Test
	public void testVarnodes() {
		try {
			m.initMachineState();
			PCodeSpace regs = m.spaces.get("register");
			PCodeSpace c = m.spaces.get("const");
			
			Varnode r0 = new Varnode(regs, BigInteger.ZERO, 8);
			Varnode r1 = new Varnode(regs, BigInteger.valueOf(0x8), 8);
			Varnode c1 = new Varnode(c, BigInteger.ONE, 8);
			Varnode cFood = new Varnode(c, BigInteger.valueOf(0xf00dl), 8);
			
			r0.storeImmediateUnsigned(0x123);
			r1.storeImmediateUnsigned(0x0f00dl);
 
			assertEquals("Storing food failed", (r1.fetchUnsigned().longValue() & 0xffff), 0xf00dl);
			assertEquals("Const 1 failed", BigInteger.ONE, c1.fetchUnsigned());
			assertEquals("Const f00d failed", BigInteger.valueOf(0xf00dl), cFood.fetchUnsigned());
			assertEquals("Const byte 1 failed", 1, c1.fetchByte(0));
			
			// todo: test PIECE / SUBPIECE
			
			// todo: test endianness interpretation 
		} catch (Exception e) {
			e.printStackTrace();
			fail("Error in Varnode test: ");
		}
	}
	
	@Test
	public void testUnsignedOpcodes() {
		try {
			m.initMachineState();
			PCodeInterpreter interpreter = new PCodeInterpreter(program);
			PCodeSpace regs = m.spaces.get("register");
			PCodeSpace c = m.spaces.get("const");
			PCodeSpace ram = m.spaces.get("ram");
			
			Varnode r0 = new Varnode(regs, BigInteger.ZERO, 8);
			Varnode r1 = new Varnode(regs, BigInteger.valueOf(8), 8);
			Varnode r2 = new Varnode(regs, BigInteger.valueOf(16), 8);
			Varnode r3 = new Varnode(regs, BigInteger.ONE, 4);
			
			Varnode c0 = new Varnode(c, BigInteger.ZERO, 8);
			Varnode c1 = new Varnode(c, BigInteger.ONE, 8);
			Varnode c3 = new Varnode(c, BigInteger.valueOf(0xdeadbeef), 4);
			Varnode c4 = new Varnode(c, BigInteger.valueOf(0x10101010), 4);
			Varnode c5 = new Varnode(c, BigInteger.valueOf(0x5f5f5f5f), 4);
			
			interpreter.doOp(new PCodeOp(PCodeOpCode.COPY, r0, c1, null)); // r0 should have a 1
			assertEquals("COPY test", BigInteger.ONE, r0.fetchUnsigned());
			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_ADD, r0, r0, r0)); // r0 <- 2
			assertEquals("INT_ADD test", BigInteger.valueOf(2), r0.fetchSigned());
			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_MULT, r1, r0, r0)); // r1 <- 4
			assertEquals("INT_MULT test", BigInteger.valueOf(4), r1.fetchUnsigned());
			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_SUB, r2, r1, r0)); // r2 <- 2
			assertEquals("INT_SUB test", BigInteger.valueOf(2), r2.fetchSigned());
			
			interpreter.doOp(new PCodeOp(PCodeOpCode.COPY, r0, c0, null)); // r0 should have a 1
			for (int i = 0; i < 512; i++) {
				assertEquals("for loop ADD test", BigInteger.valueOf(i), r0.fetchUnsigned());
				interpreter.doOp(new PCodeOp(PCodeOpCode.INT_ADD, r0, r0, c1)); // r0 <- r0 + 1
			}
			
			// the expected answers below are from Cryptol.
			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_XOR, r3, c3, c4)); // r3 <- deadbeef & 10101010
			assertEquals("INT_XOR test", new BigInteger("cebdaeff", 16), r3.fetchUnsigned());

			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_AND, r3, c3, c5)); // r3 <- deadbeef & 5f5f5f5f
			assertEquals("INT_AND test", new BigInteger("5e0d1e4f", 16), r3.fetchUnsigned());

			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_NEGATE, r3, c3, null)); // r3 <- ~deadbeef 
			assertEquals("INT_NEGATE test", new BigInteger("21524110", 16), r3.fetchUnsigned());

			// TODO: other unsigned opcodes, incl INT_CARRY, INT_LESSEQUAL, INT_LESS
			// INT_NOTEQUAL, INT_EQUAL, PIECE, SUBPIECE, INT_XOR, INT_AND, INT_OR, INT_LEFT, INT_RIGHT
			// INT_MULT, INT_DIV, INT_REM, BOOL_NEGATE, 
		} catch (Exception e) {
			e.printStackTrace();
			fail("Error in Varnode test: ");
		}

	}
	
	@Test
	public void testSignedOpcodes() {
		try {
			m.initMachineState();
			PCodeInterpreter interpreter = new PCodeInterpreter(program);
			PCodeSpace regs = m.spaces.get("register");
			PCodeSpace c = m.spaces.get("const");
			PCodeSpace ram = m.spaces.get("ram");
			
			Varnode r0 = new Varnode(regs, BigInteger.ZERO, 2);
			Varnode r1 = new Varnode(regs, BigInteger.valueOf(8), 2);
			Varnode r2 = new Varnode(regs, BigInteger.valueOf(16), 2);
			Varnode r3 = new Varnode(regs, BigInteger.ZERO, 8);
			
			Varnode c0 = new Varnode(c, BigInteger.ZERO, 2);
			Varnode c1 = new Varnode(c, BigInteger.ONE, 2);
			
			interpreter.doOp(new PCodeOp(PCodeOpCode.COPY, r1, c1, null)); // r1 should have a 1
			assertEquals("COPY one test", BigInteger.ONE, r1.fetchUnsigned());
			
			interpreter.doOp(new PCodeOp(PCodeOpCode.COPY, r0, c0, null)); // r0 should have a 0
			assertEquals("COPY zero test", BigInteger.ZERO, r0.fetchUnsigned());
			for (int i = 0; i < 512; i++) {
				assertEquals("for loop SUB test", BigInteger.valueOf(-1 * i), r0.fetchSigned());
				interpreter.doOp(new PCodeOp(PCodeOpCode.INT_SUB, r0, r0, c1)); // r0 <- r0-1
			}
			// we now use r0's -512 value in the next few tests:
			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_SEXT, r3, r0, null)); // r3 should have -512:[64]
			assertEquals("INT_SEXT test", BigInteger.valueOf(-512), r3.fetchSigned());
			
			interpreter.doOp(new PCodeOp(PCodeOpCode.INT_ZEXT, r3, r0, null)); // r3 should have 0xfe00:[64]
			assertEquals("INT_ZEXT test", BigInteger.valueOf(0xfe00), r3.fetchUnsigned());
			
			// TODO: INT_SLESS, INT_SLESSEQUAL, INT_SCARRY, INT_2COMP, INT_NEGATE, INT_SBORROW
			// INT_SRIGHT, 	INT_SDIV, INT_SREM, ...
		} catch (Exception e) {
			e.printStackTrace();
			fail("Error in Varnode test: ");
		}

	}
	
	@Test
	public void testControlFlow() {
		// TODO: BRANCH, CBRANCH, BRANCHIND, CALL, RETURN, CALLIND
	}
	
	@Test
	public void testMemOps() {
		// COPY, LOAD, STORE, PTRADD, INDIRECT
	}
	
}
