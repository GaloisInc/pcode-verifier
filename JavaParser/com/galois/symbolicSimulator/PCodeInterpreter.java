package com.galois.symbolicSimulator;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

// Takes a parsed program and a machine state, and executes the
// program, mutating the machine state with each step.
public class PCodeInterpreter {
	PCodeMachineState m;
	PCodeProgram p;
	
	Set<Integer> breakpoints;

	public PCodeInterpreter(PCodeProgram program) {
		p = program;
		m = new PCodeMachineState(p);
		breakpoints = new HashSet<Integer>();
	}
	
	// PCode is a bit weird on LOAD and STORE - 
	// the 2 arg versions of these take a register destination Varnode,
	// but really point into RAM (harumph)
	void load(Varnode dest, Varnode src) throws Exception {
		assert dest != null : "LOAD dest is null";
		assert src  != null : "LOAD src is null";
		dest.loadIndirect(src, m.getRAMspace());
	}
	
	void store(Varnode dest, Varnode src) throws Exception {
		dest.storeIndirect(src,m.getRAMspace());
	}

	void step(PCodeMachineState s) throws Exception {
		// fetch code at PC
		PCodeOp op = m.program.codeSegment.fetch(m.microPC++);
		System.out.println(op.toString());
		// decode op
		long lhs, rhs, res;
		switch (op.opcode) {
			case COPY:
				op.output.copyBytes(op.input0);
				break;
			case LOAD:
				// output = *input0
				load(op.output, op.input0);
				break;
			case STORE:
				// *output = input1 (check?)
				store(op.output, op.input0);
				break;
			case BRANCH:
				m.microPC = m.program.codeSegment.microAddrOfMacroInstr(op.output.offset);
				break;
			case CBRANCH:
				lhs = op.input0.fetch();
				if (lhs != 0) {
					m.microPC = m.program.codeSegment.microAddrOfMacroInstr(op.output.offset);
				}
				break;
			case BRANCHIND:
				lhs = op.output.fetch();
				m.microPC = m.program.codeSegment.microAddrOfMacroInstr(lhs);
				break;
			case CALL:
				m.microPC = m.program.codeSegment.microAddrOfMacroInstr(op.output.offset);
				break;
			case CALLIND:
				lhs = op.output.fetch();
				m.microPC = m.program.codeSegment.microAddrOfMacroInstr(lhs);
				break;
			case RETURN:
				lhs = op.output.fetch();
				m.microPC = m.program.codeSegment.microAddrOfMacroInstr(lhs);
				break;
			case PIECE:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				long concat = 0;
				
				if (op.input0.size + op.input1.size != op.output.size){
					System.out.println("Warning: PIECE operands != size of output");
					break;
				}
				// "lhs makes up the most significant part of the output"
				if (p.archSpec.bigEndianP) {
					// LHS | RHS
					for (int i = 0; i < op.input0.size; i++) {
						op.output.storeByte(i, op.input0.fetchByte(i));
					}
					int lhsSize = op.input0.size;
					for (int j = 0; j < op.input1.size; j++) {
						op.output.storeByte(lhsSize+j, op.input1.fetchByte(j));
					}
				} else {
					// RHS | LHS
					for (int i = 0; i < op.input1.size; i++) {
						op.output.storeByte(i, op.input0.fetchByte(i));
					}
					int lhsSize = op.input1.size;
					for (int j = 0; j < op.input0.size; j++) {
						op.output.storeByte(lhsSize+j, op.input0.fetchByte(j));
					}
				}
				op.output.storeImmediate(concat);
				break;
			case SUBPIECE:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();

				if (rhs > op.input0.size) {
					System.out.println("Warning: SUBPIECE operand > size of input");
					break;
				}
				// "throw away rhs-bytes of input"
				for (int i = 0; i < op.input0.size; i++) {
					if (p.archSpec.bigEndianP) {
						op.output.storeByte(i, op.input0.fetchByte((int) (i + rhs)));
					} else {
						op.output.storeByte(i, op.input0.fetchByte(i));
					}					
				}
				op.output.storeImmediate(lhs);
				break;
			case INT_EQUAL:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				op.output.storeImmediate(lhs == rhs ? 1 : 0);
				break;
			case INT_NOTEQUAL:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				op.output.storeImmediate(lhs != rhs ? 1 : 0);
				break;
			case INT_LESS: // todo - work properly with unsigned
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				op.output.storeImmediate(lhs < rhs ? 1 : 0);
				break;
			case INT_SLESS:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				op.output.storeImmediate(lhs < rhs ? 1 : 0);
				break;
			case INT_LESSEQUAL: // todo - work properly with unsigned
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				op.output.storeImmediate(lhs <= rhs ? 1 : 0);
				break;
			case INT_SLESSEQUAL:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				op.output.storeImmediate(lhs <= rhs ? 1 : 0);
				break;
			case INT_ZEXT:
				lhs = op.input0.fetch();
				op.output.storeImmediate(lhs); // todo: check if this is right (if output size takes care of this)
				break;
			case INT_SEXT:
				break;
			case INT_ADD:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs + rhs;
				op.output.storeImmediate(res);
				break;
			case INT_SUB:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs - rhs;
				op.output.storeImmediate(res);
				break;
/*
			case INT_CARRY:
				break;
			case INT_SCARRY:
				break;
			case INT_2COMP:
				break;
			case INT_NEGATE:
				break;
*/
			case INT_SBORROW:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				long smaller = Math.min(lhs, rhs);
				long bigger = Math.max(lhs, rhs);
				long maxRange = maxSizeOfElt(op.input0.size);
				if (bigger - smaller > maxRange) {
					op.output.storeImmediate(1);
				} else {
					op.output.storeImmediate(0);
				}
				break;
			case INT_XOR:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs ^ rhs;
				op.output.storeImmediate(res);
				break;
			case INT_AND:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs & rhs;
				op.output.storeImmediate(res);
				break;
			case INT_OR:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs | rhs;
				op.output.storeImmediate(res);
				break;
			case INT_LEFT:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs << rhs;
				op.output.storeImmediate(res);
				break;
			case INT_RIGHT: // TODO: fix this for unsigned
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs >> rhs;
				op.output.storeImmediate(res);
				break;
			case INT_SRIGHT:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs >> rhs;
				op.output.storeImmediate(res);
				break;
			case INT_MULT:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs * rhs;
				op.output.storeImmediate(res);
				break;
			case INT_DIV:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs / rhs;
				op.output.storeImmediate(res);
				break;
			case INT_REM:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs % rhs;
				op.output.storeImmediate(res);
				break;
			case INT_SDIV:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs / rhs;
				op.output.storeImmediate(res);
				break;
			case INT_SREM:
				lhs = op.input0.fetch();
				rhs = op.input1.fetch();
				res = lhs % rhs;
				op.output.storeImmediate(res);
				break;
			case BOOL_NEGATE:
				lhs = op.input0.fetch();
				op.output.storeImmediate(lhs == 0 ? 1 : 0);
				break;
			case BOOL_XOR:
				lhs = op.input0.fetch() & 1;
				rhs = op.input1.fetch() & 1;
				res = lhs ^ rhs;
				op.output.storeImmediate(res & 1);
				break;
			case BOOL_AND:
				lhs = op.input0.fetch() & 1;
				rhs = op.input1.fetch() & 1;
				res = lhs & rhs;
				op.output.storeImmediate(res & 1);
				break;
			case BOOL_OR:
				lhs = op.input0.fetch() & 1;
				rhs = op.input1.fetch() & 1;
				res = lhs | rhs;
				op.output.storeImmediate(res & 1);
				break;
/*
				// floats go here
			case MULTIEQUAL:
				break;
			case INDIRECT:
				break;
			case PTRADD:
				break;
*/
			default:
				System.out.println("Unimplemented opcode: " + op.opcode.name());
		}
		// interpret op, modifying machine state, including (perhaps) PC
	}
	
	private long maxSizeOfElt(int size) {
		return (long) Math.pow(2, 8 * size -1) -1;
	}

	void copy(Varnode out, Varnode in) throws Exception { // TODO: make the exceptions more meaningful
		if (out.size != in.size) {
			throw new Exception("mismatched sizes in COPY");
		}
		long val = in.fetch();
		out.storeImmediate(val);
	}
	
	public void runInteractive (String funcToRun, Scanner in) {
		boolean done = false;
		try {
			// set up registers, stack, etc.
			m.initMachineState();
			m.initMachineStateForFunctionCall(); 
		} catch (Exception e1) {
			System.out.println("Unable to initialze machine state");
			e1.printStackTrace();
			System.exit(-1);
		}

		// set up PC
		
		PCodeFunction f = p.lookupFunction(funcToRun);
		if (f == null) {
			System.out.println("Unable to locate function " + funcToRun);
			return;
		}
		System.out.println("Starting " + funcToRun);
		m.microPC = m.program.codeSegment.microAddrOfVarnode(f.macroEntryPoint);
		
		System.out.print("> ");
		while (!done) {
			try {
				String cmd = null;
				if (!in.hasNextLine()) {
					done = true;
				} else {
					cmd = in.nextLine();
					if (cmd.contains("quit")) {
						System.out.println("Bye!");
						System.exit(0);
					} else if (cmd.contains("next") || cmd.length() == 0) {
						step(m);
					} else if (cmd.contains("list")) {
						Scanner args = new Scanner(cmd);
						args.next(); 
						if (args.hasNext()) { // print one space
							String fn = args.next();
							if (fn.equals("all")) {
								System.out.println("Program state:");
								System.out.println(p.toString(m));
								System.out.println("Machine state:");
								System.out.println(m.toString());
							} else if (fn.equals("spaces")){
								System.out.println("Spaces:");
								for (Enumeration<String> e = m.spaces.keys(); e.hasMoreElements(); ) {
									System.out.print(e.nextElement() + " ");
								}
								System.out.print("\n");
							} else {
								PCodeFunction pfn = p.functions.get(fn);
								if (pfn != null)
									System.out.println(pfn.toString(m));
								else {
									System.out.println("Unable to locate function " + fn);
								}
							}
						} else {
							for (int i = 0; i < 10; i++) {
								PCodeOp o = m.program.codeSegment.fetch(m.microPC+i);
								System.out.println(o.toString());
							}
						}
						args.close();
					} else if (cmd.contains("print")) {
						Scanner args = new Scanner(cmd);
						args.next(); 
						if (args.hasNext()) { // print one space
							PCodeSpace s = m.getSpace(args.next());
							if (s != null) {
								System.out.println(s.toString());
							} else {
								System.out.println("unable to locate space ");
							}
						} else { // print all spaces
							System.out.println(p.toString());
						}
						args.close();
					} else if (cmd.contains("break")) {
						Scanner args = new Scanner(cmd);
						args.next(); 
						if (args.hasNext()) { // specify a breakpoint, by hex addr, or function name
							String arg = args.next();
							PCodeFunction bf = p.functions.get(arg);
							if (bf != null) {
								long bp = m.program.codeSegment.microAddrOfVarnode(bf.macroEntryPoint);
								breakpoints.add(new Integer((int)bp));
							} else {
								// assume it's a hex address
								long macroEntry = Integer.decode(arg);
								long bp = m.program.codeSegment.microAddrOfMacroInstr(macroEntry);
								breakpoints.add(new Integer((int)bp));
							}
						} else { // add a breakpoint "here"
							breakpoints.add(new Integer(m.microPC));
						}
						args.close();
					} else if (cmd.contains("cont")) {
						System.out.println("Execution trace:");
						do {
							step(m);
						} while (notAtBreakpoint(m.microPC));
					} else {
						System.out.print("this interpreter supports {next|quit|print|list|cont|break<function | addr>}");
					}
				}
			} catch (Exception e) {
				System.out.println("error: " + e.getMessage());
				e.printStackTrace();
			}
			System.out.print("> ");
		}
	}

	private boolean notAtBreakpoint(int microPC) {
		boolean foundBreakpoint = breakpoints.contains(new Integer(microPC));
		return !foundBreakpoint;
	}
}
