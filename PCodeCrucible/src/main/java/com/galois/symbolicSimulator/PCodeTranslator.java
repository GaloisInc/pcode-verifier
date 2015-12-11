package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class PCodeTranslator {
    // how many bits are stored in an individual memory cell
    public static final long cellWidth = 8;

    // Name to assign to the Crucible function representing this PCode program
    String procName;

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

    // ABI information about our current execution environment
    ABI abi;

    // Path name to use for source positions
    String loc_path;

    public PCodeTranslator( Simulator sim, PCodeProgram prog, ABI abi, String procName )
    {
        this.sim = sim;
        this.prog = prog;
        this.abi = abi;
        this.byteWidth = abi.getAddrBytes();
        this.addrWidth = abi.getAddrWidth();
        this.procName = procName;
    }

    public PCodeTranslator( Simulator sim, PCodeProgram prog, ABI abi, String procName, String loc_path )
    {
        this.sim = sim;
        this.prog = prog;
        this.abi = abi;
        this.byteWidth = abi.getAddrBytes();
        this.addrWidth = abi.getAddrWidth();
        this.procName = procName;
        this.loc_path = loc_path;
    }

    public Procedure getProc() throws Exception
    {
        if( proc == null ) {

            initProc( procName );

            Map<String, AddrSpaceManager> addrSpaces = abi.initAddrSpaces( proc );
            TempAddrSpace temps = abi.getTemps();
            RegisterAddrSpace regs = abi.getRegisters();
            RAMAddrSpace ram = abi.getRAM();

            // Translate the PCode program into a Crucible CFG
            buildCFG( addrSpaces,
                      temps,
                      regs.getRegisterFile(),
                      ram.getRAM() );

            // DEBUGGING
            //sim.printCFG( proc );

            sim.useCfg( proc );
        }

        return this.proc;
    }

    void initProc( String name ) throws Exception {
        Type[] types = abi.machineStateTypes();
        Type retType = Type.struct( types );
        proc = new Procedure( sim, name, types, retType );
    }

    void buildCFG( Map<String,AddrSpaceManager> addrSpaces,
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
            } else if ( isIntrinsic( fn.name ) ) {
                System.out.println( "Implementing intrinsic: " + fn.name + " 0x" + fn.macroEntryPoint.offset.toString(16) );
                Block bb = fetchBB( fn.macroEntryPoint.offset );
                implementIntrinsic( fn.name, bb );
            } else {
                Block bb = fetchBB( fn.macroEntryPoint.offset );
                System.out.println( "UNIMPLEMENTED: " + fn.name + " 0x" + fn.macroEntryPoint.offset.toString(16) );

                // Return the current state of the machine if we call into an unimplemented function
                {
                    Expr pc    = bb.read(trampolinePC);
                    Expr regs  = bb.read(registerFile);
                    Expr ram   = bb.read(ramReg);
                    Expr ret   = bb.structLiteral( pc, regs, ram );
                    bb.print( "WARNING: Exiting on call to unimplemented function: " + fn.name + "\n" );
                    bb.returnExpr( ret );
                }
            }
        }

        finalizeCFG( registerFile, ramReg );
    }

    void finalizeCFG( Reg registerFile, Reg ramReg ) throws Exception
    {
        // Produce the entry point block, which simply reads the Crucible function
        // arguments corresponding to the program counder, registers and memory
        // and places these values in their respective registers.
        Block bb = proc.getEntryBlock();

        Expr pc       = proc.getArg(0);
        Expr initRegs = proc.getArg(1);
        Expr initRam  = proc.getArg(2);

        bb.write( trampolinePC, pc );
        bb.write( registerFile, initRegs );
        bb.write( ramReg, initRam );

        // End the entry point block by jumping to the trampoline to start execution
        // at the given PC value.
        bb.jump( trampoline );

        // Finsh everything up by constructing the trampoline blocks.
        constructTrampoline( registerFile, ramReg );
    }

    void constructTrampoline( Reg registerFile, Reg ramReg ) throws Exception
    {
        // FIXME? Organize the trampoline as a binary search tree instead of a linear
        // scan so concrete offsets require at most O(log(n)) jumps instead of
        // O(n).  Downside: the path conditions for symbolic indirect jumps
        // become more complicated, including O(n) redundant terms involving inequalities
        // in addition to the O(n) (dis)equalities found at the leaves of the search tree.

        // Start at the head of the trampoline
        Block bb = trampoline;

        // Sort the offsets using a TreeSet so the resulting trampoline code
        // is easier to debug when looking at the generated CFG.
        Set<BigInteger> offsets = new TreeSet<BigInteger>( blockMap.keySet() );

        // For each insturction offset in the program, produce a trampoline block
        // that checks the trampoline register against that offest and jumps to
        // the corresponding Crucible basic block if they match.
        for( BigInteger off : offsets ) {
            Block next = proc.newBlock();
            next.block_description = "mid-trampoline block";

            Block tgt = blockMap.get(off);
            Expr e = bb.bvEq( bb.read(trampolinePC), bb.bvLiteral( addrWidth, off )) ;
            bb.branch( e, tgt, next );

            bb = next;
        }

        // Default case, when we fall of the end of the trampoline
        // This means we tried to indirect jump to an
        // unknown address. In this case, return from the crucible function
        // with updated program counter, register bank and memory.
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

    // FIXME, this implementation of memset makes a number of assumptions that are platform specific
    // 1) The name '_memset' is produced by clang on OSX
    // 2) This code assumes the stack grows downward

    public boolean isIntrinsic( String nm ) {
        return ( nm.equals( "_memset" ) );
    }

    public void implementIntrinsic( String nm, Block bb )
        throws Exception
    {
        if( nm.equals ("_memset") ) {
            Block body_block = proc.newBlock();
            body_block.block_description = "memset loop body";
            Block test_block = proc.newBlock();
            test_block.block_description = "memset loop test";
            Block exit_block = proc.newBlock();
            exit_block.block_description = "memset function epilogue";

            Reg idxReg  = proc.newReg( Type.bitvector( abi.getAddrWidth() ) );
            Reg endReg  = proc.newReg( Type.bitvector( abi.getAddrWidth() ) );
            Reg byteReg = proc.newReg( Type.bitvector( 8 ) );

            RAMAddrSpace ram = (RAMAddrSpace) addrSpaces.get( "ram" );
            AddrSpaceManager regs = addrSpaces.get( "register" );

            {   // Function entry

                // Grab the function inputs
                Expr addr = regs.loadDirect( bb, abi.argumentRegister( 0 ), abi.getAddrBytes() );
                Expr b    = regs.loadDirect( bb, abi.argumentRegister( 1 ), abi.getAddrBytes() );
                Expr n    = regs.loadDirect( bb, abi.argumentRegister( 2 ), abi.getAddrBytes() );

                // Store the result value for later
                regs.storeDirect( bb, abi.returnRegister( 0 ), abi.getAddrBytes(), addr );

                // truncate the byte value to write
                b = bb.bvTrunc( b, 8 );

                // set initial values for the internal crucible registers
                bb.write( idxReg, addr );
                bb.write( byteReg, b );
                // end <- addr + n
                bb.write( endReg, bb.bvAdd( addr, n ) );
                bb.jump( test_block );
            }

            {   // Loop test

                // loop while idx < end
                Expr idx = test_block.read( idxReg );
                Expr end = test_block.read( endReg );
                Expr test = test_block.bvUlt( idx, end );
                test_block.branch( test, body_block, exit_block );
            }

            {   // Loop body
                Expr idx = body_block.read( idxReg );
                Expr b   = body_block.read( byteReg );

                // write the byte into memory
                ram.poke( body_block, idx, 1, b );

                // increment the index
                idx = body_block.bvAdd( idx, body_block.bvLiteral( abi.getAddrWidth(), 0x1 ) );
                body_block.write( idxReg, idx );

                body_block.jump( test_block );
            }

            {   // Function exit
                Expr stackVal = regs.loadDirect( exit_block, abi.stackRegister(), abi.getAddrBytes() );

                // read the return value
                Expr retVal   = ram.peek( exit_block, stackVal, abi.getAddrBytes() );

                // pop the stack
                stackVal = exit_block.bvAdd( stackVal, exit_block.bvLiteral( abi.getAddrWidth(), abi.getAddrBytes() ) );
                regs.storeDirect( exit_block, abi.stackRegister(), abi.getAddrBytes(), stackVal );

                // return
                exit_block.write( trampolinePC, retVal );
                exit_block.jump( trampoline );
            }

            return;
        }

        throw new Exception("Unknown intrinsic: " + nm) ;
    }

    void visitPCodeBlock( PCodeFunction fn, PCodeBasicBlock pcode_bb )  throws Exception {
        curr_bb = fetchBB( pcode_bb.blockBegin.offset );

        String path = loc_path;

        if( pcode_bb.loc != null ) {
            if( path == null ) { path = pcode_bb.loc.getSystemId(); }
            Position pos = new BinaryPosition( procName, path, pcode_bb.blockBegin.offset.longValue() );

            // Position pos = new SourcePosition( procName,
            //                                    pcode_bb.loc.getSystemId(),
            //                                    pcode_bb.loc.getStartLine(),
            //                                    pcode_bb.loc.getStartColumn() );
            curr_bb.setPosition( pos );
        }

        //System.out.println("Building basic block " + fn.name + " " + pcode_bb.blockBegin.offset.toString(16) +
        //" " + pcode_bb.blockEnd.offset.toString(16) +
        //" " + curr_bb.toString() );

        BigInteger macroPC = pcode_bb.blockBegin.offset;
        int blockstart = prog.codeSegment.microAddrOfVarnode(pcode_bb.blockBegin);
        int blockend   = prog.codeSegment.microAddrOfVarnode(pcode_bb.blockEnd);
        int microPC    = blockstart;
        // System.out.println( "block bounds: " + blockstart + " " + blockend );

        PCodeOp o = prog.codeSegment.fetch(microPC);

        if( !o.blockStart ) {
            throw new Exception( "Invalid start of basic block: " + pcode_bb.blockBegin.offset.toString(16) );
        } else {
            //System.out.println("START OF BLOCK");
        }

        // End the loop when we terminate the block via a control-flow instruction (when o == null);
        // or when we have just added an implict jump to the first instruction of the following
        // PCode basic block, which happens when the macroinstruction PC is beyond the ending offset.
        while( o != null && o.offset.compareTo( pcode_bb.blockEnd.offset ) <= 0 ) {

            // Translate the fetched instruction and add it to the current block
            addOpToBlock( path, o, microPC );

            // advance the microcode instruction counter
            microPC++;

            // If we fall off the end of the block which was terminated by some control-flow
            // instruction, we break out early here. We must exit early here to avoid attempting
            // to fetch an instruction past the end of the program.  However, if the block is _not_
            // already terminated (i.e. curr_bb != null), then the following microcode PC must be valid
            // and we have to fetch the crucible basic block corresponding to the following instruction
            // and add an explicit jump, as is done below.
            //
            // NB: blockend is not necessarily the last microinstruction PC in this basic block! Rather,
            // it is the microinstruction PC value for the _first_ microinstruction of the last addressable
            // instruction in the basic block.  Thus !(microPC <= blockend) will hold for _all_ the
            // microinstrucions of the final instruction of the basic block.
            if( !(microPC <= blockend) && curr_bb == null ) {
                o = null;
                break;
            }

            o = prog.codeSegment.fetch(microPC);

            // Create a new crucible basic block if we are at a new offset and
            // end the current basic block by jumping.
            if( o != null && !o.offset.equals( macroPC ) ) {
                Block bb = fetchBB( o.offset );
                if( curr_bb != null ) {
                    curr_bb.jump( bb );
                }

                curr_bb = bb;
                macroPC = o.offset;
            }
        }
    }


    AddrSpaceManager getSpace( String space_name ) throws Exception {
        AddrSpaceManager m = addrSpaces.get( space_name );
        if( m == null ) {
            throw new Exception( "Unknown address space: " + space_name );
        }
        return m;
    }

    Expr getInput( Varnode vn ) throws Exception
    {
        return getSpace( vn.space_name ).loadDirect( curr_bb, vn.offset, vn.size );
    }

    void setOutput( PCodeOp o, Expr e ) throws Exception
    {
        getSpace( o.output.space_name ).storeDirect( curr_bb, o.output.offset, o.output.size, e );
    }

    void addOpToBlock( String path, PCodeOp o, int microPC ) throws Exception
    {
        //System.out.println( o.toString() );
        //System.out.println( o.loc.toString() );

        Block bb = curr_bb;
        Expr e, e1, e2;

        if( o.loc != null ) {
            Position pos = new BinaryPosition( procName, path, o.offset.longValue() );

            // Position pos = new SourcePosition( procName,
            //                                    o.loc.getSystemId(),
            //                                    o.loc.getStartLine(),
            //                                    o.loc.getStartColumn() );

            bb.setCurrentPosition( pos );
        }

        switch( o.opcode ) {
        case COPY:
            e = getInput( o.input0 );
            setOutput( o, e );
            break;
        case LOAD:
            e = getSpace( o.space_id ).loadIndirect( curr_bb, o.input0, o.output.size );
            setOutput( o, e );
            break;
        case STORE:
            e = getInput( o.input1 );
            getSpace( o.space_id ).storeIndirect( curr_bb, o.input0, o.input1.size, e );
            break;

        case BRANCH:
        case CALL: {
            Block tgt = fetchBB( o.input0.offset );

            // Debugging information
            // curr_bb.print("Unconditional branch to: " + o.input0.offset.toString(16) + "\n" );

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
                next.block_description = "PCode internal block 0x" + nextop.offset.toString( 16 ) + " " + nextop.uniq;
                curr_bb.branch( e, tgt, next );

                // Indicates we are continuing to translate microinstructions in this instruction
                curr_bb = next;
            } else {
                Block next = fetchBB( nextop.offset );
                curr_bb.branch( e, tgt, next );

                // Indicates we are done with all the microinstructions in this instruction
                curr_bb = null;
            }

            break;
        }

        case BRANCHIND:
        case CALLIND:
        case RETURN: {
            e = getInput( o.input0 );

            // write the desired jump address to the PC register
            curr_bb.write( trampolinePC, e );

            // Build a short block that prints a warning before jumping to the trampoline
            Block warn_blk = proc.newBlock();
            warn_blk.block_description = "PCode symbolic indirect jump warning block 0x" + o.offset.toString( 16 );
            warn_blk.print( "WARNING: indirect branch on symbolic value at 0x" + o.offset.toString( 16 ) + "\n" );
            warn_blk.print( "    This is quite likely to result in nontermination of the symbolic simulator.\n" );
            warn_blk.jump( trampoline );

            // Figure out if the address to jump to is concrete
            Expr is_conc = curr_bb.isConcrete( e );

            // Jump directly to trampoline if so, but print a warning if it is symbolic
            curr_bb.branch( is_conc, trampoline, warn_blk );
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
