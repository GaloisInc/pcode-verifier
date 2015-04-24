package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class PCodeCrucible {
    // how many bits are stored in an individual memory cell
    public static final long cellWidth = 8;

    // size of this machine's primary word length/address length in bytes
    int byteWidth;

    // size of this machine's primary word length/address length in bits
    long addrWidth;

    // PCode program we are translating
    PCodeProgram prog;

    // Reference to the Crucible simulator object
    Simulator sim;

    // Crucible procedure handle for the CFG we are constructing
    Procedure proc;

    // Map from space names to address space managers
    Map<String, AddrSpaceManager> addrSpaces;

    // Mapping from PCode micro-instruction sequence numbers to Crucible blocks
    Map<BigInteger, Block> blockMap;

    // The Crucible block representing the head of the indirect-jump trampoline
    Block trampoline;

    // The register holding the PC to jump to for the trampoline
    Reg trampolinePC;

    // The current Crucible basic block
    Block curr_bb;

    public PCodeCrucible( Simulator sim, PCodeProgram prog )
    {
        this.sim = sim;
        this.prog = prog;
        this.byteWidth = prog.archSpec.wordSize;
        this.addrWidth = byteWidth * cellWidth;
    }

    public Procedure initProc( String name ) throws Exception {
        Type regFileType = Type.vector( Type.bitvector(cellWidth) );
        Type ramType     = Type.wordMap( addrWidth, Type.bitvector(cellWidth) );

        Type[] types = new Type[]
            { Type.bitvector( addrWidth ),
              regFileType,
              ramType
            };
        Type retType = Type.struct( types );

        proc = new Procedure( sim, name, types, retType );
        return proc;
    }

    public void buildCFG( Map<String,AddrSpaceManager> addrSpaces,
                          TempAddrSpace temps,
                          Reg registerFile,
                          Reg ramReg ) throws Exception {

        this.addrSpaces   = addrSpaces;
        this.trampoline   = proc.newBlock();
	this.trampoline.block_description = "trampoline block";
        this.trampolinePC = proc.newReg( Type.bitvector( addrWidth ) );
        this.blockMap     = new HashMap<BigInteger, Block>();

        for( PCodeFunction fn : prog.getFunctions() ) {
            if( fn.basicBlocks != null && fn.basicBlocks.size() > 0 ) {
                for( PCodeBasicBlock pcode_bb : fn.basicBlocks ) {
                    visitPCodeBlock( fn, pcode_bb );

                    // Clear the set of temporaray registers after visiting each basic block.
                    // It is not entirely clear what are the scoping rules that govern the
                    // temporary address space, but perhaps it is correct to assume they
                    // scope across an entire basic block...
                    temps.clearRegisters();
                }
            } else {
                Block bb = fetchBB( fn.macroEntryPoint.offset );
                System.out.println( "UNIMPLEMENTED: " + fn.name + " " + fn.macroEntryPoint.offset );

                // Return the current state of the machine if we call into an unimplemented function
                {
                    Expr pc    = bb.read(trampolinePC);
                    Expr regs  = bb.read(registerFile);
                    Expr ram   = bb.read(ramReg);
                    Expr ret   = bb.structLiteral( pc, regs, ram );
                    bb.returnExpr( ret );
                }

            }
        }

        finalizeCFG( registerFile, ramReg );
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

	constructTrampoline( registerFile, ramReg );
    }

    void constructTrampoline( Reg registerFile, Reg ramReg ) throws Exception
    {
        Block bb = trampoline;

        for( BigInteger off : blockMap.keySet() ) {

            Block next = proc.newBlock();
	    next.block_description = "mid-trampoline block";
            Block tgt = blockMap.get(off);
            Expr e = bb.bvEq( bb.read(trampolinePC), bb.bvLiteral( addrWidth, off )) ;

            bb.branch( e, tgt, next );

            bb = next;
        }

        // Default case, when we fall of the end of the trampoline
        // This means we tried to indirect jump to an
        // unknown address... RETURN!
        Expr pc   = bb.read(trampolinePC);
        Expr regs = bb.read(registerFile);
        Expr ram  = bb.read(ramReg);
        Expr ret  = bb.structLiteral( pc, regs, ram );
        bb.returnExpr( ret );
    }

    Block fetchBB( BigInteger offset ) {
        Block bb = blockMap.get( offset );
        if( bb == null ) {
            bb = proc.newBlock();
	    bb.block_description = "PCode Block: " + offset.toString( 16 );
            blockMap.put( offset, bb );
        }
        return bb;
    }

    void visitPCodeBlock( PCodeFunction fn, PCodeBasicBlock pcode_bb )  throws Exception {

        curr_bb = fetchBB( pcode_bb.blockBegin.offset );
        System.out.println("Building basic block " + fn.name + " " + pcode_bb.blockBegin.offset.toString(16) +
			   " " + pcode_bb.blockEnd.offset.toString(16) +
			   " " + curr_bb.toString() );

        BigInteger macroPC = pcode_bb.blockBegin.offset;
        int microPC = prog.codeSegment.microAddrOfVarnode(pcode_bb.blockBegin);

        int fnstart = prog.codeSegment.microAddrOfVarnode(fn.macroEntryPoint);
        int fnend   = fnstart + fn.length;
        System.out.println( "fn bounds: " + fnstart + " " + microPC + " " + fnend );

        PCodeOp o = prog.codeSegment.fetch(microPC);

        if( !o.blockStart ) {
            throw new Exception( "Invalid start of basic block: " + pcode_bb.blockBegin.offset.toString(16) );
        } else {
            System.out.println("START OF BLOCK");
        }

        while( o != null && o.offset.compareTo( pcode_bb.blockEnd.offset ) <= 0 ) {
            addOpToBlock( o, microPC );

            microPC++;
            if( !(microPC < fnend) ) {
                o = null;
                break;
            }

            o = prog.codeSegment.fetch(microPC);

            // Create a new crucible basic block if we are at a new offset and
	    // end the current basic block by jumping
            if( !o.offset.equals( macroPC ) ) {
                Block bb = fetchBB( o.offset );
                if( curr_bb != null ) {
                    curr_bb.jump( bb );
                }

                curr_bb = bb;
                macroPC = o.offset;
            }
        }

        if( curr_bb != null ) {
            System.out.println("Possible unterminated block! " + macroPC.toString(16) );
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

    void addOpToBlock( PCodeOp o, int microPC ) throws Exception
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
            Block tgt = fetchBB( o.input0.offset );
            PCodeOp nextop = prog.codeSegment.fetch(microPC + 1);

	    // A CBRANCH may occur in the middle of a block of micro-instructions.
	    // In this case, we need to create a new Crucible basic block to represent
	    // the microinstrutions occuring following the CBRANCH.
	    //
	    // However, if the memory offset of the following microinstruction is _different_
	    // from our current offset, then the CBRANCH is the final microinstruction of
	    // at that offset, and we instead need to fetch the block corresponding to the offset
	    // of the following instruction.
	    if( nextop.offset.equals( o.offset ) ) {
		Block next = proc.newBlock();
		next.block_description = "PCode internal block " + nextop.offset.toString( 16 ) + " " + nextop.uniq;
		curr_bb.branch( e, tgt, next );
		curr_bb = next;
	    } else {
		Block next = fetchBB( nextop.offset );
		curr_bb.branch( e, tgt, next );
		curr_bb = null;
	    }

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
                    e = bb.bvLshr( e, bb.bvLiteral( o.input0.size * cellWidth, toTrunc * cellWidth ) );
                }
                if( o.input0.size > o.output.size  ) {
                    e = bb.bvTrunc( e, o.output.size * cellWidth );
                }
                setOutput( o, e );
            }
            break;
        case INT_EQUAL:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.eq( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_NOTEQUAL:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.not( curr_bb.eq( e1, e2 ) );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_LESS:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = curr_bb.bvUlt( e1, e2 );
            e = curr_bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_SLESS:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.bvSlt( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_LESSEQUAL:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.bvUle( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_SLESSEQUAL:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.bvSle( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_ZEXT:
            e = getInput( o.input0 );
            e = bb.bvZext( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_SEXT:
            e = getInput( o.input0 );
            e = bb.bvSext( e, o.output.size * cellWidth );
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
        case INT_CARRY:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.bvCarry( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_SCARRY:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.bvSCarry( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_SBORROW:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e = bb.bvSBorrow( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case INT_2COMP:
            e1 = getInput( o.input0 );
            // FIXME? should we directly expose a unary negation operator?
            e = bb.bvSub( bb.bvLiteral( o.input0.size * cellWidth, 0 ), e1 );
            setOutput( o, e );
            break;
        case INT_NEGATE:
            e1 = getInput( o.input0 );
            e = bb.bvNot( e1 );
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

            // zero extend input 1 if necessary
            if( o.input0.size > o.input1.size ) {
                e2 = bb.bvZext( e2, o.input0.size * cellWidth );
            }

            // truncate input 1 if necessary
            // This is safe to do because the maximum possible
            // shift is expressible in (significantly) fewer bits
            // than the size of input0
            if( o.input0.size < o.input1.size ) {
                e2 = bb.bvTrunc( e2, o.input0.size * cellWidth );
            }

            e = bb.bvShl( e1, e2 );
            setOutput( o, e );
            break;
        case INT_RIGHT:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );

            // zero extend input 1 if necessary
            if( o.input0.size > o.input1.size ) {
                e2 = bb.bvZext( e2, o.input0.size * cellWidth );
            }

            // truncate input 1 if necessary
            // This is safe to do because the maximum possible
            // shift is expressible in (significantly) fewer bits
            // than the size of input0
            if( o.input0.size < o.input1.size ) {
                e2 = bb.bvTrunc( e2, o.input0.size * cellWidth );
            }

            e = bb.bvLshr( e1, e2 );
            setOutput( o, e );
            break;
        case INT_SRIGHT:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );

            // zero extend input 1 if necessary
            if( o.input0.size > o.input1.size ) {
                e2 = bb.bvZext( e2, o.input0.size * cellWidth );
            }

            // truncate input 1 if necessary
            // This is safe to do because the maximum possible
            // shift is expressible in (significantly) fewer bits
            // than the size of input0
            if( o.input0.size < o.input1.size ) {
                e2 = bb.bvTrunc( e2, o.input0.size * cellWidth );
            }

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
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case BOOL_XOR:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e1 = bb.bvNonzero( e1 );
            e2 = bb.bvNonzero( e2 );
            e = bb.xor( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case BOOL_AND:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e1 = bb.bvNonzero( e1 );
            e2 = bb.bvNonzero( e2 );
            e = bb.and( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;
        case BOOL_OR:
            e1 = getInput( o.input0 );
            e2 = getInput( o.input1 );
            e1 = bb.bvNonzero( e1 );
            e2 = bb.bvNonzero( e2 );
            e = bb.or( e1, e2 );
            e = bb.boolToBV( e, o.output.size * cellWidth );
            setOutput( o, e );
            break;

        case FLOAT_EQUAL:
        case FLOAT_NOTEQUAL:
        case FLOAT_LESS:
        case FLOAT_LESSEQUAL:
        case FLOAT_ADD:
        case FLOAT_SUB:
        case FLOAT_MULT:
        case FLOAT_DIV:
        case FLOAT_NEG:
        case FLOAT_ABS:
        case FLOAT_SQRT:
        case FLOAT_CEIL:
        case FLOAT_FLOOR:
        case FLOAT_ROUND:
        case FLOAT_UNORDERED:
        case FLOAT_NAN:
        case INT2FLOAT:
        case FLOAT2FLOAT:
        case TRUNC:
            throw new Exception("Floating point instructions not yet supported:" + o.toString() );

        case MULTIEQUAL:
        case INDIRECT:
        case PTRADD:
        default:
            throw new Exception("Unsupported instruction " + o.toString() );
        }
    }

}
