package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

// Machine state needed for interpreting PCode programs
public class PCodeMachineState {
	int microPC; // index into microOps
	
	PCodeProgram program;
	Dictionary<String,PCodeSpace> spaces;
		
	public PCodeMachineState(PCodeProgram p) {
		program = p;
		microPC = -1;
		spaces = new Hashtable<String,PCodeSpace>();
		spaces.put("register", new PCodeSpace("register", p.archSpec));
		spaces.put("unique",   new PCodeSpace("unique",   p.archSpec));
		spaces.put("const",    new PCodeSpace("const",    p.archSpec));
		spaces.put("ram",      program.dataSegment);
	}

	public PCodeSpace getSpace(String spaceName) {
		return spaces.get(spaceName);		
	}
	
	public PCodeSpace getSpaceAndEnsureCapacity(String spaceName, long offset) {
		PCodeSpace ret = spaces.get(spaceName);
		if (ret == null) {
			ret = new PCodeSpace(spaceName, program.archSpec);
			spaces.put(spaceName, ret);
			System.out.println("creating space " + spaceName);
		}
		return ret;
	}
	
	public String toString() {
		String ret = "Program state:\n";
		for (Enumeration<PCodeSpace> e = spaces.elements(); e.hasMoreElements();) {
			PCodeSpace s = e.nextElement();
			if (s.name.equals("const")) continue;
			ret += s.toString() + "\n";
		}
		return ret;
	}

	public PCodeSpace getRAMspace() {
		return program.dataSegment;
	}

	public void initMachineState() throws Exception {
		for (Iterator<Varnode> e = program.varnodes.iterator(); e.hasNext(); ) {
			Varnode v = e.next();
			if (v.space == null) {
				v.space = getSpace(v.space_name);
			}
		}
	}
	
	public void initMachineStateForFunctionCall() throws Exception {
		
		PCodeSpace regs = spaces.get("register");
		Varnode rbp = new Varnode(regs, BigInteger.valueOf(0x28l), 8);
		Varnode rsp = new Varnode(regs, BigInteger.valueOf(0x20l), 8);
		Varnode r14 = new Varnode(regs, BigInteger.valueOf(0xb0l), 8);
		Varnode rbx = new Varnode(regs, BigInteger.valueOf(0x18l), 8);
		Varnode rdi = new Varnode(regs, BigInteger.valueOf(0x38l), 8); // input
		Varnode rax = new Varnode(regs, BigInteger.ZERO, 8); // output
		rbp.storeImmediateUnsigned(0x4000l); 
		rsp.storeImmediateUnsigned(0x4000l);
		r14.storeImmediateUnsigned(0x2l);
		rbx.storeImmediateUnsigned(0xf00dl);
		rdi.storeImmediateUnsigned(0x7l); // asking for fib(7), which should be 13 (0xd)
		rax.storeImmediateUnsigned(0xcafel);
		// initialize the stack at least a bit:
		rsp.storeIndirect(rax, program.dataSegment);
	}
}
