package com.galois.symbolicSimulator;

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
		ret.ensureCapacity(offset);
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
		Varnode rbp = new Varnode(regs, (long)0x28, 8);
		Varnode rsp = new Varnode(regs, (long)0x20, 8);
		Varnode r14 = new Varnode(regs, (long)0xb0, 8);
		Varnode rbx = new Varnode(regs, (long)0x18, 8);
		Varnode rdi = new Varnode(regs, (long)0x38, 8); // input
		Varnode rax = new Varnode(regs, (long)0x0, 8); // output
		rbp.storeImmediate(0x4000l); 
		rsp.storeImmediate(0x4000l);
		r14.storeImmediate(0x2l);
		rbx.storeImmediate(0xf00dl);
		rdi.storeImmediate(0x7l); // asking for fib(7), which should be 13 (0xd)
		rax.storeImmediate(0xcafel);
		// initialize the stack at least a bit:
		rsp.storeIndirect(rax, program.dataSegment);
	}
}
