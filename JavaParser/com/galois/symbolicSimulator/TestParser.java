package com.galois.symbolicSimulator;

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.junit.*;

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
 
			assertEquals("Storing food failed", (r1.fetchUnsigned().longValueExact() & 0xffff), 0xf00dl);
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
	public void testSimpleOpcodes() {
		try {
			m.initMachineState();
			PCodeInterpreter interpreter = new PCodeInterpreter(program);
			PCodeSpace regs = m.spaces.get("register");
			PCodeSpace c = m.spaces.get("const");
			PCodeSpace ram = m.spaces.get("ram");
			
			Varnode r0 = new Varnode(regs, BigInteger.ZERO, 8);
			Varnode r1 = new Varnode(regs, BigInteger.valueOf(8), 8);
			Varnode r2 = new Varnode(regs, BigInteger.valueOf(16), 8);
			
			Varnode c0 = new Varnode(c, BigInteger.ZERO, 8);
			Varnode c1 = new Varnode(c, BigInteger.ONE, 8);
			
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
				
		} catch (Exception e) {
			e.printStackTrace();
			fail("Error in Varnode test: ");
		}

	}

}
