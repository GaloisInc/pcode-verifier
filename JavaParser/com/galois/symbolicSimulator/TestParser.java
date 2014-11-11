package com.galois.symbolicSimulator;

import static org.junit.Assert.*;

import org.junit.*;

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
			
			Varnode r0 = new Varnode(regs, (long)0x0, 8);
			Varnode r1 = new Varnode(regs, (long)0x8, 8);
			Varnode c1 = new Varnode(c, (long)0x1, 8);
			Varnode cFood = new Varnode(c, (long) 0xf00dl, 8);
			
			r0.storeImmediate(0x123);
			r1.storeImmediate(0x0f00dl);
 
			assertEquals("Storing food failed", (long)(r1.fetch() & 0xffff), 0xf00dl);
			assertEquals("Const 1 failed", 1, c1.fetch());
			assertEquals("Const f00d failed", 0xf00dl, cFood.fetch());
			assertEquals("Const byte 1 failed", 1, c1.fetchByte(0));
			
			// todo: test PIECE / SUBPIECE
			
			// todo: test endianness interpretation 
		} catch (Exception e) {
			e.printStackTrace();
			fail("Error in Varnode test: ");
		}
	}
}
