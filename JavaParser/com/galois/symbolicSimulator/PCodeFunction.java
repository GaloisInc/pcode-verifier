package com.galois.symbolicSimulator;

// A PCodeFunction is a named collection of PCode instructions.
public class PCodeFunction {
	String name;
	PCodeScope scope;
	public Varnode macroEntryPoint;
	public int length;
	
	public String toString(PCodeMachineState m) {
		String ret = name + ":\n";
		if (macroEntryPoint != null) { 
			try {
				int microPC = m.program.codeSegment.microAddrOfVarnode(macroEntryPoint);
				for (int i = 0; i < length; i++) {
					PCodeOp o = m.program.codeSegment.fetch(microPC+i);
					ret += o.toString() + "\n";
				}
			} catch (Exception e) {
				ret += "ERROR IN XLATE - Unable to fetch " + macroEntryPoint + "\n";
			}
		} else {
			ret += "<external function>\n";
		}
		return ret;
	}
}
