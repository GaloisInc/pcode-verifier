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

            ABI abi = new X86_64( prog.archSpec );
            //ABI abi = new ARM( prog.archSpec );
            long addrWidth = abi.getAddrWidth();

            // Set up the translator and build the Crucible CFG
            PCodeTranslator translator = new PCodeTranslator( sim, prog, abi, "pcodeCFG" );
            Procedure proc = translator.getProc();
            MachineState machine = new MachineState( sim, proc, prog, abi );

            // Some place in memory (arbitrary) where we will store some 4-byte integers
            int how_many = 10;
            SimulatorValue arg1 = sim.bvLiteral( addrWidth, 0x7000l );
            SimulatorValue arg2 = sim.bvLiteral( addrWidth, how_many );

            // Set up an array of 4-byte integers
            SimulatorValue baseVal = sim.bvLiteral( 4*PCodeTranslator.cellWidth, 0x100 );
            for( int i = 0; i < how_many; i++ ) {
                SimulatorValue off = sim.bvAdd( arg1, sim.bvLiteral( addrWidth, 4*i ) );
                SimulatorValue val = sim.bvAdd( baseVal, sim.bvLiteral( 4*PCodeTranslator.cellWidth, i ) );
                machine.poke( off, 4, val );
            }

            // Overwrite position 3 with a symbolic 4-byte integer
            machine.poke( sim.bvAdd( arg1, sim.bvLiteral( addrWidth, 4*3 ) ),
                          4, sim.freshConstant( VarType.bitvector( 32 ) ) );

            // Overwrite position 8 with 0
            machine.poke( sim.bvAdd( arg1, sim.bvLiteral( addrWidth, 4*8 ) ),
                          4, sim.bvLiteral( 32, 0 ) );

            //SimulatorValue arg1 = machine.getEntryPoint( "doubler1" );
            //SimulatorValue arg2 = machine.getEntryPoint( "doubler2" );

            // make the simulator a bit more chatty
            sim.setVerbosity( 2 );

            // set up the stack register(s)
            machine.initStack( BigInteger.valueOf( 0x4000l ) );

            // Make up some arbitrary return address
            SimulatorValue retVal = sim.bvLiteral( addrWidth, 0xdeadbeefl );

            // Call a function!
            SimulatorValue result = machine.callFunction( "first_zero", retVal, arg1, arg2 );
            SimulatorValue finalpc = machine.currentPC;

            System.out.println( "finalpc: " + finalpc ); // should be retVal
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
