package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class PCodeCrucible {
    public static void main( String[] args ) throws Exception {
	if( args.length != 2 ) {
	    System.err.println("Usage: "+ System.getProperty("app.name") + " <pcodefile>");
	    System.exit(1);
	}

	String crucibleServerPath = args[0];
	String pcodeFilePath = args[1];

	PCodeParser p = new PCodeParser( pcodeFilePath, System.err );
	PCodeProgram prog = p.parseProgram();

	Simulator sim = Simulator.launchLocal(crucibleServerPath);
	try {
	    PCodeCrucible x = new PCodeCrucible( sim, prog );
	    x.buildCFG( "pcodeCFG" );
	} finally {
	    sim.close();
	}
    }


    static final long cellWidth = 8;
    long addrWidth;

    Simulator sim;
    PCodeProgram prog;

    Procedure proc;
    Map<String, AddrSpaceManager> addrSpaces;
    Map<BigInteger, Block> codeSegment;
    Block trampoline;
    Reg trampolinePC;
    Set<BigInteger> visitedAddr;

    Block curr_bb;

    public PCodeCrucible( Simulator sim, PCodeProgram prog )
    {
	this.sim = sim;
	this.prog = prog;
	this.addrWidth = 64;
    }

    public Procedure buildCFG( String name ) throws Exception {
	Type[] types = new Type[]
	    { Type.bitvector( addrWidth ),
	      Type.vector( Type.bitvector(cellWidth) ),
	      Type.wordMap( addrWidth, Type.bitvector(cellWidth) )
	    };
	proc = new Procedure( sim, name, types, Type.UNIT );

	trampoline   = proc.newBlock();
	trampolinePC = proc.newReg( Type.bitvector( addrWidth ) );
	codeSegment  = new HashMap<BigInteger, Block>();

	PCodeArchSpec arch = new PCodeArchSpec(); // FIXME

	// Seems to be enough for the X86 examples we have
	int regFileSize = 1024;

	addrSpaces = new HashMap<String, AddrSpaceManager>();

	ConstAddrSpace consts = new ConstAddrSpace( arch );
	RegisterAddrSpace regs = new RegisterAddrSpace( arch, proc, regFileSize );
	TempAddrSpace temps = new TempAddrSpace( arch, proc );
	RAMAddrSpace ram = new RAMAddrSpace( arch, proc, 64, addrSpaces );

	addrSpaces.put("const"     , consts );
	addrSpaces.put("register"  , regs );
	addrSpaces.put("unique"    , temps );
	addrSpaces.put("ram"       , ram );

	visitedAddr = new HashSet<BigInteger>();
	for( PCodeFunction fn : prog.getFunctions() ) {
	    if( fn.basicBlocks != null && fn.basicBlocks.size() > 0 ) {
		for( Varnode fnbb : fn.basicBlocks ) {
		    visitPCodeBlock( fn, fnbb );
		}
	    } else {
		Block bb = fetchBB( fn.macroEntryPoint.offset );
		System.out.println( "UNIMPLEMENTED: " + fn.name + " " + fn.macroEntryPoint.offset );
		bb.reportError(new StringValue("Unimplemented function!"));
	    }
	}

	constructTrampoline();
	finalizeCFG( regs.getRegisterFile(), ram.getRAM() );

	// Print the generated CFG for debugging purposes
	sim.printCFG(proc);

	return proc;
    }

    void constructTrampoline() throws Exception
    {
	Block bb = trampoline;

	for( BigInteger off : codeSegment.keySet() ) {

	    Block next = proc.newBlock();
	    Block tgt = codeSegment.get(off);
	    Expr e = bb.bvEq( bb.read(trampolinePC), bb.bvLiteral( addrWidth, off )) ;

	    bb.branch( e, tgt, next );

	    bb = next;
	}

	// Tried to indirect jump to an unknown address... RETURN!
	bb.returnExpr( new UnitValue() );
    }

    void finalizeCFG( Reg registerFile, Reg ramReg ) throws Exception
    {
	Expr pc       = proc.getArg(0);
	Expr initRegs = proc.getArg(1);
	Expr initRam  = proc.getArg(2);

	Block bb = proc.getEntryBlock();
	bb.write( trampolinePC, pc );
	bb.write( registerFile, initRegs );
	bb.write( ramReg, initRam );
	bb.jump( trampoline );
    }


    Block fetchBB( BigInteger offset ) {
	Block bb = codeSegment.get( offset );
	if( bb == null ) {
	    bb = proc.newBlock();
	    codeSegment.put( offset, bb );
	}
	return bb;
    }

    void visitPCodeBlock( PCodeFunction fn, Varnode fnbb )  throws Exception {
	if( visitedAddr.contains( fnbb.offset ) ) { return; }

	curr_bb = fetchBB( fnbb.offset );
	System.out.println("Building basic block " + fn.name + " " + fnbb.offset.toString() + " " + curr_bb.toString() );

	BigInteger macroPC = fnbb.offset;
	visitedAddr.add( fnbb.offset );
	int microPC = prog.codeSegment.microAddrOfVarnode(fnbb);
	int fnend = microPC + fn.length;

	PCodeOp o = prog.codeSegment.fetch(microPC);


	if( !o.blockStart ) {
	    throw new Exception( "Invalid start of basic block " + fnbb.offset.toString() );
	} else {
	    System.out.println("START OF BLOCK");
	}

	while( o != null && !o.isBranch() && microPC < fnend ) {
	    addOpToBlock( fn, o, microPC );

	    microPC++;
	    o = prog.codeSegment.fetch(microPC);

	    // Create a new crucible basic block if we are at a new opcode
	    if( !o.offset.equals( macroPC ) ) {
		Block bb = fetchBB( o.offset );
		if( curr_bb != null ) {
		    curr_bb.jump( bb );
		}

		curr_bb = bb;
		macroPC = o.offset;
		visitedAddr.add( macroPC );
	    }
	}

	if( o != null && o.isBranch() ) {
	    addOpToBlock( fn, o, microPC );
	} else {
	    throw new Exception( "Invalid basic block" + fnbb.toString() );
	}


	if( curr_bb != null ) {
	    System.out.println("Possible unterminated block! "+ macroPC);
	}
    }


    AddrSpaceManager getSpace( Varnode vn ) throws Exception {
	AddrSpaceManager m = addrSpaces.get( vn.space_name );
	if( m == null ) {
	    throw new Exception( "Unknown address space: " + vn.space_name );
	}
	return m;
    }

    Expr getInput( Varnode vn ) throws Exception
    {
	return getSpace( vn ).loadDirect( curr_bb, vn.offset, vn.size );
    }

    void setOutput( PCodeOp o, Expr e ) throws Exception
    {
	getSpace( o.output ).storeDirect( curr_bb, o.output.offset, o.output.size, e );
    }

    void addOpToBlock( PCodeFunction fn, PCodeOp o, int microPC ) throws Exception
    {
	System.out.println( o.toString() );

	Block bb = curr_bb;
	Expr e, e1, e2;

	switch( o.opcode ) {
	case COPY:
	    e = getInput( o.input0 );
	    setOutput( o, e );
	    break;
        case LOAD:
	    e = addrSpaces.get( o.space_id ).loadIndirect( curr_bb, o.input0, o.output.size );
	    setOutput( o, e );
	    break;
	case STORE:
	    e = getInput( o.input1 );
	    addrSpaces.get( o.space_id ).storeIndirect( curr_bb, o.input0, o.input1.size, e );
	    break;
        case PIECE:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvConcat( e1, e2 );
	    setOutput( o, e );
	    break;
        case SUBPIECE:
	    if( !o.input1.space_name.equals( "const" ) ) {
		throw new UnsupportedOperationException("Second argument to SUBPIECE is required to be constant");
	    }
	    {
		e = getInput( o.input0 );
		long toTrunc = o.input1.offset.intValue();
		if( toTrunc > 0 ) {
		    e = bb.bvLshr( e, bb.bvLiteral( o.input0.size * 8, toTrunc * 8 ) );
		}
		if( o.input0.size - toTrunc > o.output.size  ) {
		    e = bb.bvTrunc( e, o.output.size * 8 );
		}
		setOutput( o, e );
	    }
	    break;
        case INT_EQUAL:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.eq( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
        case INT_NOTEQUAL:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.not( curr_bb.eq( e1, e2 ) );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
        case INT_LESS:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = curr_bb.bvUlt( e1, e2 );
	    e = curr_bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
        case INT_SLESS:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSlt( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
        case INT_LESSEQUAL:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvUle( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
        case INT_SLESSEQUAL:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSle( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case INT_CARRY:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvCarry( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case INT_SCARRY:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSCarry( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case INT_SBORROW:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSBorrow( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case INT_ZEXT:
	    e = getInput( o.input0 );
	    e = bb.bvZext( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case INT_SEXT:
	    e = getInput( o.input0 );
	    e = bb.bvSext( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case INT_ADD:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvAdd( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_SUB:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSub( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_NEGATE:
	    e1 = getInput( o.input0 );
	    // FIXME? should we directly expose a unary negation operator?
	    e = bb.bvSub( bb.bvLiteral( o.input0.size*8, 0 ), e1 );
	    setOutput( o, e );
	    break;
	case INT_XOR:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvXor( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_AND:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvAnd( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_OR:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvOr( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_LEFT:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvShl( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_RIGHT:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvLshr( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_SRIGHT:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvAshr( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_MULT:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvMul( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_DIV:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvUdiv( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_REM:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvUrem( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_SDIV:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSdiv( e1, e2 );
	    setOutput( o, e );
	    break;
	case INT_SREM:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e = bb.bvSrem( e1, e2 );
	    setOutput( o, e );
	    break;
	case BOOL_NEGATE:
	    e1 = getInput( o.input0 );
	    e1 = bb.bvNonzero( e1 );
	    e = bb.not( e1 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case BOOL_XOR:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e1 = bb.bvNonzero( e1 );
	    e2 = bb.bvNonzero( e2 );
	    e = bb.xor( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case BOOL_AND:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e1 = bb.bvNonzero( e1 );
	    e2 = bb.bvNonzero( e2 );
	    e = bb.and( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;
	case BOOL_OR:
	    e1 = getInput( o.input0 );
	    e2 = getInput( o.input1 );
	    e1 = bb.bvNonzero( e1 );
	    e2 = bb.bvNonzero( e2 );
	    e = bb.or( e1, e2 );
	    e = bb.boolToBV( e, o.output.size*8 );
	    setOutput( o, e );
	    break;

	case BRANCH:
	case CALL: {
	    Block tgt = fetchBB( o.input0.offset );
	    curr_bb.jump(tgt);
	    curr_bb = null;
	    break;
	}

	case CBRANCH: {
	    e = getInput( o.input1 );
	    e = curr_bb.bvNonzero( e );
	    Block tgt  = fetchBB( o.input0.offset );
	    PCodeOp nextop = prog.codeSegment.fetch(microPC + 1);
	    Block next = fetchBB( nextop.offset );
	    curr_bb.branch( e, tgt, next );
	    curr_bb = null;
	    break;
	}

	case BRANCHIND:
	case CALLIND:
	case RETURN: {
	    e = getInput( o.input0 );
	    curr_bb.write( trampolinePC, e );
	    curr_bb.jump( trampoline );
	    curr_bb = null;
	    break;
	}

        default:
	    throw new Exception("Unsupported instruction " + o.toString() );
	}
    }

}
