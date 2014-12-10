package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

// The static state of a PCode program
public class PCodeProgram {
	Dictionary<String,PCodeFunction> functions;
	PCodeArchSpec archSpec;
	PCodeCodeSpace codeSegment; // code gets parsed into here
	PCodeSpace     dataSegment; // our RAM segment, "data_segment" gets put here
	ArrayList <Varnode> varnodes; // we save these for loading later
	
	public PCodeProgram() {
		functions = new Hashtable<String,PCodeFunction>();
		archSpec = new PCodeArchSpec();
		dataSegment = new PCodeSpace("ram", archSpec);
		codeSegment = new PCodeCodeSpace(archSpec,dataSegment);
		varnodes = new ArrayList<Varnode>();
	}
	
	public PCodeFunction lookupFunction(String funcname) {
		return functions.get(funcname);
	}
	
	public String lookupFunctionNameFromAddr(BigInteger addr) {
		for (Enumeration<PCodeFunction> fs = functions.elements(); fs.hasMoreElements() ; ) {
			PCodeFunction f = fs.nextElement();
			if (f.macroEntryPoint.offset.equals(addr)) {
				return f.name;
			}
		}
		return "unknown function";
	}

	public String toString(PCodeMachineState m) {
		String ret = "";
		for (Enumeration<PCodeFunction> e = functions.elements(); e.hasMoreElements(); ) {
			PCodeFunction f = e.nextElement();
			ret += f.toString(m);
		}
		ret += this.toString();
		return ret;
	}

	public void addVarnode(Varnode varnode) {
		varnodes.add(varnode);
	}
	
}
