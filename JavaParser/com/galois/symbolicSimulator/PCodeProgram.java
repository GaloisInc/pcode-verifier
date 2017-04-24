package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collection;
import java.util.Hashtable;

// The static state of a PCode program
public class PCodeProgram {
	Map<String,PCodeFunction> functions;
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
	
	public PCodeArchSpec getArchSpec() {
		return archSpec;
        }
	
        public Collection<PCodeFunction> getFunctions() {
	    return functions.values();
	}

	public PCodeFunction lookupFunction(String funcname) {
		return functions.get(funcname);
	}
	
	public String lookupFunctionNameFromAddr(BigInteger addr) {
		for( PCodeFunction f : functions.values() ) {
		     if (f.macroEntryPoint.offset.equals(addr)) {
			return f.name;
		     }
		}
		return "unknown function";
	}	

	public String toString(PCodeMachineState m) {
		String ret = "";
		for( PCodeFunction f : functions.values() ) {
			ret += f.toString(m);
		}
		ret += this.toString();
		return ret;
	}

	public void addVarnode(Varnode varnode) {
		varnodes.add(varnode);
	}
	
}
