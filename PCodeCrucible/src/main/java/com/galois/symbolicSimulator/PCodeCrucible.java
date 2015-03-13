package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class PCodeCrucible {
    Simulator sim;
    PCodeProgram prog;
    Block curr_bb;
    Map<String, AddrSpaceManager> addrSpaces;

    public PCodeCrucible( Simulator sim, PCodeProgram prog )
    {
	this.sim = sim;
	this.prog = prog;
    }

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
	    x.buildCFGs();
	} finally {
	    sim.close();
	}
    }

    public List<Procedure> buildCFGs() throws Exception {
	LinkedList<Procedure> procs = new LinkedList<Procedure>();
	for( PCodeFunction fn :  prog.getFunctions() ) {
	    if( fn.basicBlocks.size() > 0 ) {
		procs.add( buildCFG( fn ) );
	    }
	}
	return procs;
    }

    public Procedure buildCFG( PCodeFunction fn ) throws Exception {
	System.out.println("Building CFG for function " + fn.name );

	// Build a new procedure
	// FIXME figure out the correct types
	Procedure proc = new Procedure( sim, fn.name, new Type[] { }, Type.UNIT );

	// Set up crucible basic blocks to correspond to the PCode basic blocks
	Map<Varnode, Block> blockmap = new HashMap<Varnode, Block>();
	for( Varnode vn : fn.basicBlocks ) {
	    Block bb;

	    if( vn.equals( fn.macroEntryPoint ) ){
		bb = proc.getEntryBlock();
		System.out.println("Using Entry point: " +bb.toString());
	    } else {
		bb = proc.newBlock();
		System.out.println("New block: " +bb.toString());
	    }

	    blockmap.put( vn, bb );
	}

	//PCodeArchSpec arch = prog.archSpec; 
	PCodeArchSpec arch = new PCodeArchSpec(); // FIXME

	addrSpaces = new HashMap<String, AddrSpaceManager>();
	addrSpaces.put("const"     , new ConstAddrSpace( arch ) );
	addrSpaces.put("register"  , new RegisterAddrSpace( arch, proc ) );
	addrSpaces.put("unique"    , new RegisterAddrSpace( arch, proc ) );
	addrSpaces.put("ram"       , new RegisterAddrSpace( arch, proc ) ); // FIXME

	// Build the basic blocks
	for( Varnode vn : fn.basicBlocks ) {
	    buildBasicBlock( fn, vn, blockmap );
	}

	return proc;
    }

    void buildBasicBlock( PCodeFunction fn, Varnode vn, Map<Varnode,Block> blockmap )
	throws Exception {

	curr_bb = blockmap.get(vn);
	System.out.println("Building basic block " + fn.name + " " + vn.toString() + " " + curr_bb.toString() );

	int microPC = prog.codeSegment.microAddrOfVarnode(vn);

	PCodeOp o = prog.codeSegment.fetch(microPC);
	int i = 0;

	if( !o.blockStart ) {
	    throw new Exception( "Invalid start of basic block " + vn.toString() );
	} else {
	    System.out.println("START OF BLOCK");
	}
	
	while( o != null && !o.isBranch() && i < fn.length ) {
	    addOpToBlock( o );

	    i++;
	    o = prog.codeSegment.fetch(microPC + i);
	}

	if( o != null && o.isBranch() ) {
	    terminateBlock( fn, blockmap, o );
	} else {
	    throw new Exception( "Invalid basic block" + vn.toString() );
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

    void addOpToBlock( PCodeOp o ) throws Exception
    {
	System.out.println( o.toString() );

	Block bb = curr_bb;
	Expr e, e1, e2;

	switch( o.opcode ) {
	case COPY:
	    e = getInput( o.input0 );
	    setOutput( o, e );
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

	// FIXME, operations yet to be implemented
	case INT_CARRY:
	case INT_SCARRY:
	case INT_SBORROW:
        case LOAD:
	case STORE:
        case PIECE:
	case SUBPIECE:
	    System.out.println( "FIXME nyi: " + o.toString() );
	    break;

	case BRANCH:
	case CBRANCH:
	case BRANCHIND:
	case CALL:
	case CALLIND:
	case RETURN:
	    throw new Exception("Unexpected block terminator in addOpToBlock "+o.toString());

        default:
	    throw new Exception("Unsupported instruction " + o.toString() );
	}

    }
    
    Block lookupBlock( PCodeFunction func, Map<Varnode,Block> blockmap, BigInteger offset ) throws Exception
    {
	for( Varnode vn : func.basicBlocks ) {
	    if( vn.offset.equals( offset ) ) {
		return blockmap.get( vn );
	    }
	}

	throw new Exception( "Invalid branch target: " + offset );
    }

    void terminateBlock( PCodeFunction fn, Map<Varnode,Block> blockmap, PCodeOp o ) throws Exception
    {
	Block tgt;
	Expr e;

	System.out.println("BLOCK TERMINATOR");
	System.out.println( o.toString() );

	switch( o.opcode ) {
	case BRANCH:
	    tgt = lookupBlock( fn, blockmap, o.input0.offset );
	    curr_bb.jump(tgt);
	    break;
	    
	case CBRANCH:
	    /*
	    e = getInput( o.input1 );
	    e = bb.bvNonzero( e );
	    {
		Block tgt = lookupBlock( fn, blockmap, o.input0.offset );
		Procedure proc = bb.getProcedure();
		Block newbb = proc.newBlock();
		bb.branch( e, tgt, newbb );
		curr_bb = newbb;
	    }
	    break;
	    */

	case BRANCHIND:
	case CALL:
	case CALLIND:
	case RETURN:
	    System.out.println("FIXME: nyi!");
	    break;

	default:
	    throw new Exception("Unexpected instruction in terminateBlock "+o.toString());
	}

	curr_bb = null;
    }

}
