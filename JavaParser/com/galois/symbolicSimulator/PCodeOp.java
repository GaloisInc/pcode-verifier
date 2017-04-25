package com.galois.symbolicSimulator;

import java.math.BigInteger;

// OpCodes
public class PCodeOp {
	PCodeOpCode opcode = null;
        String space_id = null;
	Varnode input0 = null;
	Varnode output = null;
	Varnode input1 = null;
	Varnode input2 = null;
	BigInteger offset;
	int uniq;
	boolean blockStart = false;
	boolean funcStart = false; // if this is the first op in a fn
	PCodeFunction function = null;
        public LocationData loc;
	
	public PCodeOp(PCodeOpCode code, Varnode o, Varnode i0, Varnode i1) {
		opcode = code;
		input0 = i0;
		input1 = i1;
		output = o;		
	}

        public PCodeOp(PCodeOpCode code, String sid, Varnode o, Varnode i0, Varnode i1, Varnode i2, BigInteger off, int u, boolean firstInBlock, boolean firstInFunc, PCodeFunction func) {
		opcode = code;
		space_id = sid;
		input0 = i0;
		input1 = i1;
		input2 = i2;
		output = o;
		offset = off;
		uniq = u;
		blockStart = firstInBlock;
		funcStart = firstInFunc;
		function = func;
	}
	
	public PCodeOp(PCodeOpCode code, Varnode i0) {
		opcode = code;
		input0 = i0;
	}
	
        public boolean isBranch() {
	        switch(opcode) {
			case BRANCH:
			case BRANCHIND:
			case RETURN:
				return true;
			default:
				return false;
		}
	}

	public String toString() {
		return toString(null);
	}
	public String toString(PCodeProgram p) {
		String ret = "";
		if (blockStart) {
			if (funcStart) {
				ret += "<function " + function.name + " @ 0x" + offset.toString(16) + ">\n";
			} else {
				ret += "<block@" + offset.toString(16) + ">\n";
			}
		}
		ret += "  ";
		if (uniq > 0) ret += "  ";
		ret += "0x" + offset.toString(16) + " (" + uniq + "): ";
		ret += opcode.name() + " ";
		if (space_id != null) { ret += "{" + space_id + "} "; }
		if (output != null) ret += output.toString();
		else ret += "<null>";
		if (numArgs() == 0) {
			if (opcode == PCodeOpCode.CALL && p != null) {
				String functionName = p.lookupFunctionNameFromAddr(output.offset);
				ret += " <" + functionName + ">";
			}

			return ret;
		}
		ret += " <- ";
		if (input0 != null) ret += input0.toString();
		else ret += "<null>";
		if (numArgs() > 1) {
			if (input1 != null) ret += " <op> " + input1.toString();
			else ret += " <op> <null>";
		}
		return ret;
	}
	
	enum PCodeOpCode {
		COPY, LOAD, STORE, BRANCH, CBRANCH, BRANCHIND, CALL, CALLIND, RETURN, PIECE, SUBPIECE, 
		INT_EQUAL, INT_NOTEQUAL, INT_LESS, INT_SLESS, INT_LESSEQUAL, INT_SLESSEQUAL, INT_ZEXT, INT_SEXT, 
		INT_ADD, INT_SUB, INT_CARRY, INT_SCARRY, INT_SBORROW, INT_2COMP, INT_NEGATE, INT_XOR, INT_AND, 
		INT_OR, INT_LEFT, INT_RIGHT, INT_SRIGHT, INT_MULT, INT_DIV, INT_REM, INT_SDIV, INT_SREM, 
		BOOL_NEGATE, BOOL_XOR, BOOL_AND, BOOL_OR, 
		FLOAT_EQUAL, FLOAT_NOTEQUAL, FLOAT_LESS, FLOAT_LESSEQUAL, FLOAT_ADD, FLOAT_SUB, FLOAT_MULT, 
		FLOAT_DIV, FLOAT_NEG, FLOAT_ABS, FLOAT_SQRT, FLOAT_CEIL, FLOAT_FLOOR, FLOAT_ROUND, 
		FLOAT_UNORDERED, FLOAT_NAN, 
		INT2FLOAT, FLOAT2FLOAT, TRUNC,
		MULTIEQUAL, INDIRECT, PTRADD,
        CALLOTHER
	}
	
	int numArgs() {
		switch (opcode) {
			case BRANCH:
			case COPY:
			case BOOL_NEGATE:
			case INT_NEGATE:
			case INT_ZEXT:
			case INT_SEXT:
			case INT_2COMP:
			case FLOAT_NEG:
			case FLOAT_SQRT:
			case FLOAT_FLOOR:
			case FLOAT_CEIL:
			case FLOAT_NAN:
			case FLOAT2FLOAT:
			case FLOAT_ROUND:
			case TRUNC:
				return 1;
			default:
				return 2;
		}
	}
}
