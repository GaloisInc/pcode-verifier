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

	    // The SystemV AMD64 ABI
            ABI abi = new X86_64( prog.archSpec );

	    // The ARM ABI
            //ABI abi = new ARM( prog.archSpec );

            // Set up the translator and build the Crucible CFG
            PCodeTranslator translator = new PCodeTranslator( sim, prog, abi, "pcodeCFG" );
            MachineState machine = new MachineState( sim, translator.getProc(), prog, abi );

	    testFirstZero( sim, machine );
	    //testAES( sim, machine, testKey, testInput0, testOutput0 );
	    //testAES( sim, machine, testKey, testInput1, testOutput1 );

        } finally {
            sim.close();
        }
    }


    /*
Some basic AES test vectors.

  plain-text:
    6bc1bee2 2e409f96 e93d7e11 7393172a
    ae2d8a571e03ac9c9eb76fac45af8e51
    30c81c46a35ce411e5fbc1191a0a52ef
    f69f2445df4f9b17ad2b417be66c3710

  key:
    2b7e1516 28aed2a6 abf71588 09cf4f3c

  resulting cipher
    3ad77bb40d7a3660a89ecaf32466ef97
    f5d3d58503b9699de785895a96fdbaaf
    43b1cd7f598ece23881b00e3ed030688
    7b0c785e27e8ad3f8223207104725dd4
    */

    public static int[] testKey =
         { 0x2b, 0x7e, 0x15, 0x16,   // 2b7e 1516
	   0x28, 0xae, 0xd2, 0xa6,   // 28ae d2a6
	   0xab, 0xf7, 0x15, 0x88,   // abf7 1588
	   0x09, 0xcf, 0x4f, 0x3c }; // 09cf 4f3c

    public static int[] testInput0 =
         { 0x6b, 0xc1, 0xbe, 0xe2,   // 6bc1 bee2
	   0x2e, 0x40, 0x9f, 0x96,   // 2e40 9f96
	   0xe9, 0x3d, 0x7e, 0x11,   // e93d 7e11
	   0x73, 0x93, 0x17, 0x2a }; // 7393 172a

    public static int[] testOutput0 =
         { 0x3a, 0xd7, 0x7b, 0xb4,   // 3ad7 7bb4 
	   0x0d, 0x7a, 0x36, 0x60,   // 0d7a 3660
	   0xa8, 0x9e, 0xca, 0xf3,   // a89e caf3
	   0x24, 0x66, 0xef, 0x97 }; // 2466 ef97

    public static int[] testInput1 =
         { 0xae, 0x2d, 0x8a, 0x57,   // ae2d 8a57
	   0x1e, 0x03, 0xac, 0x9c,   // 1e03 ac9c
	   0x9e, 0xb7, 0x6f, 0xac,   // 9eb7 6fac
	   0x45, 0xaf, 0x8e, 0x51 }; // 45af 8e51

    public static int[] testOutput1 =
         { 0xf5, 0xd3, 0xd5, 0x85,   // f5d3 d585
	   0x03, 0xb9, 0x69, 0x9d,   // 03b9 699d
	   0xe7, 0x85, 0x89, 0x5a,   // e785 895a
	   0x96, 0xfd, 0xba, 0xaf }; // 96fd baaf

    public static void testAES( Simulator sim,
				MachineState machine,
				int[] keyBytes,
				int[] inputBytes,
				int[] outputBytes )
	throws Exception
    {
	SimulatorValue keyAddr    = machine.makeWord( 0x7000l );
	SimulatorValue inputAddr  = machine.makeWord( 0x7100l );
	SimulatorValue outputAddr = machine.makeWord( 0x7200l );

	// Set up the key
	for( int i=0; i < keyBytes.length; i++ ) {
	    machine.poke( sim.bvAdd( keyAddr, machine.makeWord( i ) ), 1,
			  sim.bvLiteral( 8, keyBytes[i] ) );
	}
	// Set up the input
	for( int i=0; i < inputBytes.length; i++ ) {
	    machine.poke( sim.bvAdd( inputAddr, machine.makeWord( i ) ), 1,
			  sim.bvLiteral( 8, inputBytes[i] ) );
	}

	// make the simulator a bit more chatty
	sim.setVerbosity( 2 );

	// set up the stack register(s)
	machine.initStack( BigInteger.valueOf( 0x4000l ) );

	// Make up some arbitrary return address
	SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

	// Call a function!
	machine.callFunction( retAddr, "AES128_ECB_encrypt", inputAddr, keyAddr, outputAddr );

	System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr

	SimulatorValue q = sim.boolLiteral( true );
	for( int i=0; i<16; i++ ) {
	    SimulatorValue out = machine.peek( sim.bvAdd( outputAddr, machine.makeWord( i ) ), 1 );
	    q = sim.and( q, sim.eq( out, sim.bvLiteral( 8, outputBytes[i] ) ) );
	    System.out.println( out );
	}

	// Yea!
	System.out.println( "ABC sat answer: " + sim.checkSatWithAbc( q ) );
    }

    public static void testFirstZero( Simulator sim, MachineState machine )
	throws Exception
    {
	long addrWidth = machine.getABI().getAddrWidth();

	// Some place in memory (arbitrary) where we will store some 4-byte integers
	int how_many = 10;
	SimulatorValue arg1 = machine.makeWord( 0x7000l );
	SimulatorValue arg2 = machine.makeWord( how_many );

	// Set up an array of 4-byte integers
	SimulatorValue baseVal = sim.bvLiteral( 4*PCodeTranslator.cellWidth, 0x100 );
	for( int i = 0; i < how_many; i++ ) {
	    SimulatorValue off = sim.bvAdd( arg1, machine.makeWord( 4*i ) );
	    SimulatorValue val = sim.bvAdd( baseVal, sim.bvLiteral( 4*PCodeTranslator.cellWidth, i ) );
	    machine.poke( off, 4, val );
	}

	// Overwrite position 3 with a symbolic 4-byte integer
	machine.poke( sim.bvAdd( arg1, machine.makeWord( 4*3 ) ),
		      4, sim.freshConstant( VarType.bitvector( 32 ) ) );

	// Overwrite position 8 with 0
	machine.poke( sim.bvAdd( arg1, machine.makeWord( 4*8 ) ),
		      4, sim.bvLiteral( 32, 0 ) );

	// make the simulator a bit more chatty
	sim.setVerbosity( 2 );

	// set up the stack register(s)
	machine.initStack( BigInteger.valueOf( 0x4000l ) );

	// Make up some arbitrary return address
	SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

	// Call a function!
	SimulatorValue result = machine.callFunction( retAddr, "first_zero", arg1, arg2 );

	System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr
	System.out.println( "result: " + result );

	// Try to prove something about the result: that it must return either 3 or 8
	SimulatorValue q = sim.or( sim.eq( result, machine.makeWord( 3 ) ),
                                   sim.eq( result, machine.makeWord( 8 ) ) );

	// Negate in hopes to get UNSAT
	q = sim.not(q);

	// Yea!
	System.out.println( "ABC sat answer: " + sim.checkSatWithAbc( q ) );

	// Also write out an SMTLib2 version of the problem
	sim.writeSmtlib2( "asdf.smt2", q );
    }
}
