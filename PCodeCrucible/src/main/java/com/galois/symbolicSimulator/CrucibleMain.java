package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class CrucibleMain {

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

            // Build the address spaces according to the PCode examples we have seen
            Map<String, AddrSpaceManager> addrSpaces = new HashMap<String, AddrSpaceManager>();

	    //ABI abi = new X86_64();
	    ABI abi = new ARM();

            ConstAddrSpace consts = new ConstAddrSpace( arch );
            RegisterAddrSpace regs = new RegisterAddrSpace( arch, proc, ABI.regFileSize );
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
            //SimulatorValue entryPoint = sim.bvLiteral( addrWidth, 0x280l );
            SimulatorValue entryPoint = sim.bvLiteral( addrWidth, 0x10344l );


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
	    initreg = regs.storeRegister( sim, initreg, abi.argumentRegister( 0 ), byteWidth, arg1 );

            // store arg2 into %rsi
            initreg = regs.storeRegister( sim, initreg, abi.argumentRegister( 1 ), byteWidth, arg2 );

            // store start of the stack into %rsp
            initreg = regs.storeRegister( sim, initreg, abi.stackRegister(), byteWidth, bottomOfStack );

            // store start of the stack into %rbp
	    if( abi.frameRegister() != null ) {
		initreg = regs.storeRegister( sim, initreg, abi.frameRegister(), byteWidth, bottomOfStack );
	    }

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
            SimulatorValue result = regs.loadRegister( sim, finalreg, abi.returnRegister( 0 ), byteWidth );

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
