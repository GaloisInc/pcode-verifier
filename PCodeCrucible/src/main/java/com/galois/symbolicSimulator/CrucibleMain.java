package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class CrucibleMain {
    // === Here are a bunch of definitions relevant to the X86_64 ABI ===

    // how many bytes are in the register file
    // (pretty sure this is a lot more than we actually need)
    static final int regFileSize = 1024;

    static final BigInteger rax = BigInteger.valueOf( 0x00l ); // offset value for %rax
    static final BigInteger rsp = BigInteger.valueOf( 0x20l ); // offset value for %rsp
    static final BigInteger rbp = BigInteger.valueOf( 0x28l ); // offset value for %rbp
    static final BigInteger rsi = BigInteger.valueOf( 0x30l ); // offset value for %rsi
    static final BigInteger rdi = BigInteger.valueOf( 0x38l ); // offset value for %rdi

    // Hard to be sure, but I think this is where %rdx lives
    static final BigInteger rdx = BigInteger.valueOf( 0x10l ); // offset value for %rdx

    // ugh, no real idea
    //static final BigInteger rbx = BigInteger.valueOf( ??? );
    //static final BigInteger rcx = BigInteger.valueOf( ??? );

    // maybe these are right?
    static final BigInteger r8  = BigInteger.valueOf( 0x40l );
    static final BigInteger r9  = BigInteger.valueOf( 0x48l );
    static final BigInteger r10 = BigInteger.valueOf( 0x50l );
    static final BigInteger r11 = BigInteger.valueOf( 0x58l );
    static final BigInteger r12 = BigInteger.valueOf( 0x60l );
    static final BigInteger r13 = BigInteger.valueOf( 0x68l );
    static final BigInteger r14 = BigInteger.valueOf( 0x70l );
    static final BigInteger r15 = BigInteger.valueOf( 0x78l );

    // There's a big ol' gap here.  Probably for floating-point
    // registers.  x87 and SSE2 registers will fit in this space,
    // with a little left over... maybe enough for their associated
    // control registers....?

    // Status flags appear to be packed in starting at offset 0x200,
    // each flag in its own byte.  I'm pretty confidant about the flags
    // up to overflow (0x20B).  Everything past that is a total guess.
    static final BigInteger carry_flag     = BigInteger.valueOf( 0x200 );
    // 0x201 reserved
    static final BigInteger parity_flag    = BigInteger.valueOf( 0x202 );
    // 0x203 reserved
    static final BigInteger adjust_flag    = BigInteger.valueOf( 0x203 );
    // 0x205 reserved
    static final BigInteger zero_flag      = BigInteger.valueOf( 0x206 );
    static final BigInteger sign_flag      = BigInteger.valueOf( 0x207 );
    static final BigInteger trap_flag      = BigInteger.valueOf( 0x208 );
    static final BigInteger interrupt_flag = BigInteger.valueOf( 0x209 );
    static final BigInteger direction_flag = BigInteger.valueOf( 0x20A );
    static final BigInteger overflow_flag  = BigInteger.valueOf( 0x20B );
    // I/O privlege level... 0x20C, 0x20D, 0x20E
    // 0x20F reserved

    static final BigInteger resume_flag    = BigInteger.valueOf( 0x210 );
    static final BigInteger virtual_mode_flag = BigInteger.valueOf( 0x211 );
    static final BigInteger alignment_check_flag = BigInteger.valueOf( 0x212 );
    static final BigInteger virtual_interrupt_flag = BigInteger.valueOf( 0x213 );
    static final BigInteger virtual_interrupt_pending_flag = BigInteger.valueOf( 0x214 );
    static final BigInteger cpuid_instruction_flag = BigInteger.valueOf( 0x215 );
    // 0x216 - 0x21F reserved ?
    // 0x220 - 0x22F reserved ?
    // 0x230 - 0x23F reserved ?

    // Is there anything here in this gap???

    // code samples seem to indicate that the PC lives here
    static final BigInteger pc = BigInteger.valueOf( 0x288 );


    public static void main( String[] args ) throws Exception {
        if( args.length != 2 ) {
            System.err.println("Usage: "+ System.getProperty("app.name") + " <pcodefile>");
            System.exit(1);
        }

        String crucibleServerPath = args[0];
        String pcodeFilePath = args[1];

        PCodeParser parser = new PCodeParser( pcodeFilePath, System.err );
        PCodeProgram prog = parser.parseProgram();
        PCodeArchSpec arch = prog.archSpec;
        int byteWidth = arch.wordSize;
        long addrWidth = byteWidth * PCodeCrucible.cellWidth;

        // Connect to the crucible server
        Simulator sim = Simulator.launchLocal(crucibleServerPath);
        try {
            // listen to messages that come in
            sim.addPrintMessageListener(new Consumer<String>() {
                    public void accept(String x) {
                        System.out.print(x);
                        System.out.flush();
                    }
                });

            sim.addPathAbortedListener(new Consumer<String>() {
                    public void accept(String x) {
                        System.out.println("Path aborted: " + x);
                        System.out.flush();
                    }
                });

            // Set up the translator
            PCodeCrucible translator = new PCodeCrucible( sim, prog );

            // Initialize the CFG
            Procedure proc = translator.initProc( "pcodeCFG" );

            // Build the addresse spaces according to the X86_64 examples we have seen
            Map<String, AddrSpaceManager> addrSpaces = new HashMap<String, AddrSpaceManager>();

            ConstAddrSpace consts = new ConstAddrSpace( arch );
            RegisterAddrSpace regs = new RegisterAddrSpace( arch, proc, regFileSize );
            TempAddrSpace temps = new TempAddrSpace( arch, proc );
            RAMAddrSpace ram = new RAMAddrSpace( arch, proc, addrWidth, addrSpaces );

            addrSpaces.put("const"     , consts );
            addrSpaces.put("register"  , regs );
            addrSpaces.put("unique"    , temps );
            addrSpaces.put("ram"       , ram );

            // Translate the PCode program into a Crucible CFG
            translator.buildCFG( addrSpaces, temps, regs.getRegisterFile(), ram.getRAM() );

            // Print the generated CFG for debugging purposes
            // sim.printCFG(proc);

            // Install the generated CFG into the simulator
            sim.useCfg(proc);

            // Start executing at this address (the "first_zero" function in pcode_primitives.o.xml)
            SimulatorValue entryPoint = sim.bvLiteral( addrWidth, 0x280l );

            // Address of the bottom of the stack: 0x4000 is arbitrary
            SimulatorValue bottomOfStack = sim.bvLiteral( addrWidth, 0x4000l );

            // Address to return to, also arbitrary
            SimulatorValue returnAddr = sim.bvLiteral( addrWidth, 0xdeadbeefl );

            SimulatorValue initreg = regs.initialRegisters( sim );
            SimulatorValue initram = ram.initialRam( sim, prog.dataSegment );

	    // Some place in memory (arbitrary) where we will store some 4-byte integers
	    int how_many = 10;
	    SimulatorValue arg1 = sim.bvLiteral( addrWidth, 0x7000l );
	    SimulatorValue arg2 = sim.bvLiteral( addrWidth, how_many );

            // store arg1 into %rdi
	    initreg = regs.storeRegister( sim, initreg, rdi, byteWidth, arg1 );

            // store arg2 into %rsi
            initreg = regs.storeRegister( sim, initreg, rsi, byteWidth, arg2 );

            // store start of the stack into %rsp
            initreg = regs.storeRegister( sim, initreg, rsp, byteWidth, bottomOfStack );

            // store start of the stack into %rbp
            initreg = regs.storeRegister( sim, initreg, rbp, byteWidth, bottomOfStack );

            // set the return address on the stack
            initram = ram.storeRAM( sim, initram, bottomOfStack, byteWidth, returnAddr );

	    SimulatorValue baseVal = sim.bvLiteral( 4*PCodeCrucible.cellWidth, 0x100 );
	    //SimulatorValue baseVal = sim.freshConstant( VarType.bitvector( 4*PCodeCrucible.cellWidth ));

	    // Set up an array of 4-byte integers
	    for( int i = 0; i < how_many; i++ ) {
		SimulatorValue off = sim.bvAdd( arg1, sim.bvLiteral( addrWidth, 4*i ) );
		SimulatorValue val = sim.bvAdd( baseVal, sim.bvLiteral( 4*PCodeCrucible.cellWidth, i ) );
		initram = ram.storeRAM( sim, initram, off, 4, val );
	    }

	    // Overwrite position 3 with a symbolic 4-byte integer
	    initram = ram.storeRAM( sim, initram,
				    sim.bvAdd( arg1, sim.bvLiteral( addrWidth, 4*3 ) ),
				    4, sim.freshConstant( VarType.bitvector( 32 ) ) );

	    // Overwrite position 8 with 0
	    initram = ram.storeRAM( sim, initram,
				    sim.bvAdd( arg1, sim.bvLiteral( addrWidth, 4*8 ) ),
				    4, sim.bvLiteral( 32, 0 ) );

            // make the simulator a lot more chatty
            //sim.setVerbosity( 5 );

            // Call the simulator!
            SimulatorValue v = sim.runCall( proc.getHandle(), entryPoint, initreg, initram );

            SimulatorValue finalpc  = sim.structGet( 0, v );
            SimulatorValue finalreg = sim.structGet( 1, v );
            SimulatorValue finalram = sim.structGet( 2, v );

            // Load the function return from %rax
            SimulatorValue result = regs.loadRegister( sim, finalreg, rax, byteWidth );

            System.out.println( "finalpc: " + finalpc );
	    System.out.println( "result: " + result );

	    // Try to prove something about the result: that it must return either 3 or 8
            SimulatorValue q = sim.or( sim.eq( result, sim.bvLiteral( addrWidth, 3 ) ),
				       sim.eq( result, sim.bvLiteral( addrWidth, 8 ) ) );

	    // Negate in hopes to get UNSAT
	    q = sim.not(q);

	    // Yea!
	    System.out.println( "ABC sat answer: " + sim.checkSatWithAbc( q ) );

	    // Also write out an SMTLib2 version of the problem
            sim.writeSmtlib2( "asdf.smt2", q );
        } finally {
            sim.close();
        }
    }
}
