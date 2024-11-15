package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

import com.galois.crucible.proto.Protos;

class CrucibleMain {

    public static void main( String[] args ) throws Exception {
        if( args.length != 3 ) {
            System.err.println("Usage: "+ System.getProperty("app.name") + "<ABI name> <pcodefile>");
            System.exit(1);
        }

        String crucibleServerPath = args[0];
        String abiName = args[1];
        String pcodeFilePath = args[2];

        PCodeParser parser = new PCodeParser( pcodeFilePath, System.err );
        PCodeProgram prog = parser.parseProgram();

        // Uncomment these lines to enable heap profiling of the crucible-server
        // Simulator.extraLocalCommandArguments = new LinkedList<String>();
        // Simulator.extraLocalCommandArguments.add( "+RTS" );
        // Simulator.extraLocalCommandArguments.add( "-P -hc" );
        // Simulator.extraLocalCommandArguments.add( "-RTS" );

        // Connect to the crucible server
        //SimpleSimulator sim = SimpleSimulator.launchLocal(crucibleServerPath);
        SAWSimulator sim = SAWSimulator.launchLocal(crucibleServerPath);

        try {
            // listen to messages that come in
            sim.addPrintMessageListener(new MessageConsumer() {
                    public void acceptMessage(SimulatorMessage x) {
                        System.out.print(x.getMessage());
                        System.out.flush();
                    }
                });

            sim.addPathAbortedListener(new MessageConsumer() {
                    public void acceptMessage(SimulatorMessage x) {
                        System.out.println("Path aborted: " + x);
                        System.out.flush();
                    }
                });

            // Currently supported ABIs: AMD64 and ARM
            ABI abi = ABI.getInstance( abiName, prog.archSpec, sim );

            // Set up the translator and build the Crucible CFG
            PCodeTranslator translator = new PCodeTranslator( sim, prog, abi, "pcodeCFG" );

            // Set up any call site overrides...
            // Map<BigInteger, FunctionHandle> ovrs = new HashMap();

            // VerificationHarness testHarness = setupTestHarness();
            // FunctionHandle testOvr = sim.compileHarness( testHarness );
            // ovrs.put( BigInteger.valueOf(0x35l), testOvr );

            // translator.setCallSiteOverrides( ovrs );

            // Varnode vn = new Varnode( prog, "register", BigInteger.valueOf(0x38l), 4 );
            // translator.addVariableWatch( new WatchDirect( BigInteger.valueOf(0x35l), "n", vn ) );

            // Varnode sp = new Varnode( prog, "register", BigInteger.valueOf(0x28l), 8 );
            // BigInteger off = BigInteger.valueOf( -4l );
            // translator.addVariableWatch( new WatchIndirect( BigInteger.valueOf( 0x43l ), "fact return value", sp, "ram", new BigInteger[] { off }, 4 ) );

            // Setup a machine state to execute
            MachineState machine = new MachineState( sim, translator.getProc(), prog, abi );

            verifyRowRound( sim, machine );
            //verifyQR( sim, machine );

            //verifyFact( sim, machine );

            //testFact( sim, machine );
            //testEx( sim, machine );
            //testS20_expand32( sim, machine );
            //testS20_hash( sim, machine );
            //testS20_crypt32( sim, machine );

            //testLFSR( sim, machine );
            //testFirstZero( sim, machine );
            //testAES( sim, machine, testKey, testInput0, testOutput0 );
            //testAES( sim, machine, testKey, testInput1, testOutput1 );

        } catch (SimulatorAbortedException ex) {
            ex.printStackTrace();

            List<SimulatorMessage> msgs = ex.getMessages();
            if( msgs != null && msgs.size() > 0 ) {
                System.out.println( "== Aborted exection paths (" + msgs.size() + ") ==" );
                for( SimulatorMessage msg : ex.getMessages() ) {
                    System.out.println( msg.toString() );
                }
                System.out.println( "== End aborted exection paths ==" );
            }
        } finally {
            sim.close();
        }
    }


    public static VerificationHarness setupTestHarness()
    {
        VerificationHarness harness = new VerificationHarness("testHarness", X86_64.regWidth, X86_64.addrWidth, Protos.Endianness.LittleEndian);
        Protos.VariableReference x = harness.prestate().addVar( "x", 32 );

        harness.prestate().assignRegister( X86_64.rdi.longValue(), x );
        harness.prestate().assignRegister( X86_64.rsp.longValue(), harness.stackVar );
        harness.prestate().assignMemory( VerificationHarness.stackVar, 0x0, harness.returnVar );

        Protos.VariableReference y = harness.poststate().addVar( "y", 32 );
        harness.poststate().bindVariable( y, "x + 42" );
        harness.poststate().assignRegister( X86_64.rax.longValue(), y );

        return harness;
    }

    public static VerificationHarness setupRowround()
    {
        VerificationHarness harness = new VerificationHarness( "rowroundHarness", X86_64.regWidth, X86_64.addrWidth, Protos.Endianness.LittleEndian );
        harness.addCryptolSource( "PCodeXML-examples/Salsa20.cry" );

        harness.prestate().assignRegister( X86_64.rsp.longValue(), harness.stackVar );
        harness.prestate().assignMemory( VerificationHarness.stackVar, 0x0, harness.returnVar );

        // Set up an array of uint32_t[16]
        Protos.VariableReference y = harness.prestate().addVar( "y", 16, 32 );
        Protos.VariableReference p = harness.prestate().addVar( "p", 64 );

        // Arbitrary place for our buffer
        harness.prestate().bindVariable( p, "zero # 0x8000" );
        harness.prestate().assignMemory( p, 0x0, y );
        harness.prestate().assignRegister( 0x38, p );

        // Set up an array of uint32_t[16]
        Protos.VariableReference z = harness.poststate().addVar( "z", 16, 32 );
        // Read the buffer from p
        harness.poststate().assignMemory( p, 0x0, z );

        // Assert the correctness condition
        harness.poststate().assertCondition( "rowround y == z" );
        return harness;
    }

    public static VerificationHarness setupQuarterroundHarness()
    {
        VerificationHarness harness = new VerificationHarness( "quarterroundHarness", X86_64.regWidth, X86_64.addrWidth, Protos.Endianness.LittleEndian );

        harness.addCryptolSource( "PCodeXML-examples/Salsa20.cry" );

        harness.prestate().assignRegister( X86_64.rsp.longValue(), harness.stackVar );
        harness.prestate().assignMemory( VerificationHarness.stackVar, 0x0, harness.returnVar );

        Protos.VariableReference y0 = harness.prestate().addVar( "y0", 32 );
        Protos.VariableReference y1 = harness.prestate().addVar( "y1", 32 );
        Protos.VariableReference y2 = harness.prestate().addVar( "y2", 32 );
        Protos.VariableReference y3 = harness.prestate().addVar( "y3", 32 );

        Protos.VariableReference p0 = harness.prestate().addVar( "p0", 64 );
        Protos.VariableReference p1 = harness.prestate().addVar( "p1", 64 );
        Protos.VariableReference p2 = harness.prestate().addVar( "p2", 64 );
        Protos.VariableReference p3 = harness.prestate().addVar( "p3", 64 );

        harness.prestate().bindVariable( p0, "zero # 0x8000" );
        harness.prestate().bindVariable( p1, "p0 + 8" );
        harness.prestate().bindVariable( p2, "p0 + 16" );
        harness.prestate().bindVariable( p3, "p0 + 24" );

        harness.prestate().assignMemory( p0, 0x0, y0 );
        harness.prestate().assignMemory( p1, 0x0, y1 );
        harness.prestate().assignMemory( p2, 0x0, y2 );
        harness.prestate().assignMemory( p3, 0x0, y3 );

        harness.prestate().assignRegister( 0x38, p0 );
        harness.prestate().assignRegister( 0x30, p1 );
        harness.prestate().assignRegister( 0x10, p2 );
        harness.prestate().assignRegister( 0x08, p3 );

        Protos.VariableReference z0 = harness.poststate().addVar( "z0", 32 );
        Protos.VariableReference z1 = harness.poststate().addVar( "z1", 32 );
        Protos.VariableReference z2 = harness.poststate().addVar( "z2", 32 );
        Protos.VariableReference z3 = harness.poststate().addVar( "z3", 32 );

        harness.poststate().assignMemory( p0, 0x0, z0 );
        harness.poststate().assignMemory( p1, 0x0, z1 );
        harness.poststate().assignMemory( p2, 0x0, z2 );
        harness.poststate().assignMemory( p3, 0x0, z3 );

        harness.poststate().assertCondition( "quarterround [y0,y1,y2,y3] == [z0,z1,z2,z3]" );

        return harness;
    }

    public static void verifyRowRound( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        sim.setPathSatChecking(false);
        
        VerificationOptions opts = new VerificationOptions();
        
        opts.setProgram( machine.getProc() );
        opts.setReturnAddress( machine.makeWord( 0xdeadbeefl ) );
        opts.setStartStack( machine.makeWord( 0x4000l ) );
        opts.setStartPC( machine.getEntryPoint( "_s20_rowround" ) );
        opts.setOutputDirectory( "." );
        opts.setSeparateObligations( false );

        VerificationHarness testHarness = setupRowround();
        sim.produceVerificationGoals( testHarness, opts );
    }

    public static void verifyQR( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        sim.setPathSatChecking(false);

        VerificationOptions opts = new VerificationOptions();
        opts.setProgram( machine.getProc() );
        opts.setReturnAddress( machine.makeWord( 0xdeadbeefl ) );
        opts.setStartStack( machine.makeWord( 0x4000l ) );
        opts.setStartPC( machine.getEntryPoint( "_s20_quarterround" ) );
        opts.setOutputDirectory( "." );
        opts.setSeparateObligations( false );

        VerificationHarness testHarness = setupQuarterroundHarness();
        sim.produceVerificationGoals( testHarness, opts );
    }

    public static void verifyFact( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        sim.setPathSatChecking(false);

        VerificationOptions opts = new VerificationOptions();
        opts.setProgram( machine.getProc() );
        opts.setReturnAddress( machine.makeWord( 0xdeadbeefl ) );
        opts.setStartStack( machine.makeWord( 0x4000l ) );
        opts.setStartPC( machine.getEntryPoint( "_fact" ) );
        opts.setOutputDirectory( "." );
        opts.setSeparateObligations( false );

        VerificationHarness testHarness = setupTestHarness();
        sim.produceVerificationGoals( testHarness, opts );
    }

    public static void testFact( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );
        machine.initStack( BigInteger.valueOf( 0x4000l ));

        SimulatorValue arg = machine.makeWord( 8 );
        SimulatorValue result = machine.callFunction( retAddr, "_fact", arg );

        System.out.println( "Result: " + result.toString() );
        sim.printTerm( result );
     }

    public static void testEx( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );
        SimulatorValue stack_chk_guard = machine.makeWord( 0x1008 );
        SimulatorValue stack_canary = sim.freshConstant( VarType.bitvector(64) );
        machine.poke( stack_chk_guard, 8, stack_canary );

        SimulatorValue result = machine.callFunction( retAddr, "_broken" );
        sim.writeSAW( "broken.sawcore", result );
    }

    public static void testS20_hash( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

        int how_many = 64;
        SimulatorValue arg = machine.makeWord( 0x7000l );

        SimulatorValue symInput = sim.freshConstant( VarType.vector( how_many, VarType.bitvector(8) ) );

        for( int i = 0; i < how_many; i++ ) {
            SimulatorValue off = sim.bvAdd( arg, machine.makeWord( i ) );
            //SimulatorValue val = sim.freshConstant( VarType.bitvector(8) );
            SimulatorValue val = sim.vectorGetEntry( symInput, sim.natLiteral( i ) );
            machine.poke( off, 1, val );
        }

        // set up the stack register(s)
        machine.initStack( BigInteger.valueOf( 0x4000l ) );

        SimulatorValue result = machine.callFunction( retAddr, "s20_hash", arg );

        SimulatorValue[] outputs = new SimulatorValue[how_many];
        for( int i = 0; i < how_many; i++ ) {
            SimulatorValue off = sim.bvAdd( arg, machine.makeWord( i ) );
            outputs[i] = machine.peek( off, 1 );
        }

        SimulatorValue output = sim.vectorLit( Type.bitvector(8), outputs );
        sim.writeSAW( "s20_hash.saw", output );
    }

    public static void testS20_expand32( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

        int key_num = 32;
        int nonce_num = 16;
        int keystream_num = 64;

        SimulatorValue key_arg       = machine.makeWord( 0x7000l );
        SimulatorValue nonce_arg     = machine.makeWord( 0x8000l );
        SimulatorValue keystream_arg = machine.makeWord( 0x9000l );

        SimulatorValue key       = sim.freshConstant( VarType.vector( key_num, VarType.bitvector(8) ) );
        SimulatorValue nonce     = sim.freshConstant( VarType.vector( nonce_num, VarType.bitvector(8) ) );

        for( int i = 0; i < key_num; i++ ) {
            SimulatorValue off = sim.bvAdd( key_arg, machine.makeWord( i ) );
            //SimulatorValue val = sim.bvLiteral( 8, 0 );
            SimulatorValue val = sim.vectorGetEntry( key, sim.natLiteral( i ) );
            machine.poke( off, 1, val );
        }
        for( int i = 0; i < nonce_num; i++ ) {
            SimulatorValue off = sim.bvAdd( nonce_arg, machine.makeWord( i ) );
            //SimulatorValue val = sim.bvLiteral( 8, 0 );
            SimulatorValue val = sim.vectorGetEntry( nonce, sim.natLiteral( i ) );
            machine.poke( off, 1, val );
        }

        // set up the stack register(s)
        machine.initStack( BigInteger.valueOf( 0x4000l ) );

        SimulatorValue result =
            machine.callFunction( retAddr, "_s20_expand32",
                                  key_arg,
                                  nonce_arg,
                                  keystream_arg
                                );

        System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr

        SimulatorValue[] outputs = new SimulatorValue[keystream_num];
        for( int i = 0; i < keystream_num; i++ ) {
            SimulatorValue off = sim.bvAdd( keystream_arg, machine.makeWord( i ) );
            outputs[i] = machine.peek( off, 1 );
            //System.out.print( outputs[i].toString() + " " );
            //if( (i+1) % 10 == 0 ) { System.out.println(); }
        }
        //System.out.println();

        SimulatorValue output = sim.vectorLit( Type.bitvector(8), outputs );
        System.out.println( output.toString() );
        sim.writeSAW( "s20_expand32.saw", output );
    }

    public static void testS20_crypt32( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

        int knum = 32;
        int nnum =  8;
        int mnum = 16;

        SimulatorValue karg = machine.makeWord( 0x7000l );
        SimulatorValue narg = machine.makeWord( 0x8000l );
        SimulatorValue marg = machine.makeWord( 0x9000l );

        SimulatorValue key   = sim.freshConstant( VarType.vector( knum, VarType.bitvector(8) ) );
        SimulatorValue nonce = sim.freshConstant( VarType.vector( nnum, VarType.bitvector(8) ) );
        SimulatorValue msg   = sim.freshConstant( VarType.vector( mnum, VarType.bitvector(8) ) );

        for( int i = 0; i < knum; i++ ) {
            SimulatorValue off = sim.bvAdd( karg, machine.makeWord( i ) );
            SimulatorValue val = sim.vectorGetEntry( key, sim.natLiteral( i ) );
            machine.poke( off, 1, val );
        }
        for( int i = 0; i < nnum; i++ ) {
            SimulatorValue off = sim.bvAdd( narg, machine.makeWord( i ) );
            SimulatorValue val = sim.vectorGetEntry( nonce, sim.natLiteral( i ) );
            machine.poke( off, 1, val );
        }
        for( int i = 0; i < mnum; i++ ) {
            SimulatorValue off = sim.bvAdd( marg, machine.makeWord( i ) );
            SimulatorValue val = sim.vectorGetEntry( msg, sim.natLiteral( i ) );
            machine.poke( off, 1, val );
        }

        // set up the stack register(s)
        machine.initStack( BigInteger.valueOf( 0x4000l ) );

        SimulatorValue result =
            machine.callFunction( retAddr, "_s20_crypt32",
                                  karg,
                                  narg,
                                  machine.makeWord( 0 ),
                                  marg,
                                  machine.makeWord( mnum )
                                );

        System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr
        System.out.println( "result: " + result );

        SimulatorValue[] outputs = new SimulatorValue[mnum];
        for( int i = 0; i < mnum; i++ ) {
            SimulatorValue off = sim.bvAdd( marg, machine.makeWord( i ) );
            outputs[i] = machine.peek( off, 1 );
        }

        SimulatorValue output = sim.vectorLit( Type.bitvector(8), outputs );
        sim.writeSAW( "s20_crypt32.saw", output );
    }

    public static void testLFSR( SAWSimulator sim, MachineState machine )
        throws Exception
    {
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

        SimulatorValue arg = sim.bvZext( sim.freshConstant( VarType.bitvector( 16 )), 64 );

        // Call a function!
        SimulatorValue result = machine.callFunction( retAddr, "_lfsr_word32_seed", arg );

        System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr
        System.out.println( "result: " + result );

        sim.printTerm( sim.bvTrunc( result, 32 ) );

        //sim.writeAIGER("lfsr.aiger", sim.bvTrunc( result, 32 ) );
        sim.writeSAW("lfsr.sawext", sim.bvTrunc( result, 32 ));
    }

    public static void testFirstZero( SimpleSimulator sim, MachineState machine )
        throws Exception
    {
        // Some place in memory (arbitrary) where we will store some 4-byte integers
        int how_many = 10;
        SimulatorValue arg1 = machine.makeWord( 0x7000l );
        SimulatorValue arg2 = machine.makeWord( how_many );

        //SimulatorValue vals = sim.freshConstant(VarType.vector( how_many, VarType.bitvector( 32 )));

        // Set up an array of 4-byte integers
        SimulatorValue baseVal = sim.bvLiteral( 32, 0x100 );
        for( int i = 0; i < how_many; i++ ) {
            SimulatorValue off = sim.bvAdd( arg1, machine.makeWord( 4*i ) );
            SimulatorValue val = sim.bvAdd( baseVal, sim.bvLiteral( 32, i ) );
            //SimulatorValue val = sim.vectorGetEntry( vals, sim.natLiteral( i ) );
            machine.poke( off, 4, val );
        }

        // Overwrite position 3 with a symbolic 4-byte integer
        machine.poke( sim.bvAdd( arg1, machine.makeWord( 4*3 ) ),
                      4, sim.freshConstant( VarType.bitvector( 32 ) ) );

        // Overwrite position 8 with 0
        machine.poke( sim.bvAdd( arg1, machine.makeWord( 4*8 ) ),
                       4, sim.bvLiteral( 32, 0 ) );

        // make the simulator a bit more chatty
        //sim.setVerbosity( 2 );

        // set up the stack register(s)
        machine.initStack( BigInteger.valueOf( 0x4000l ) );

        // Make up some arbitrary return address; it should not collide with
        // any instruction in the program...
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

        // Call a function!
        SimulatorValue result = machine.callFunction( retAddr, "_first_zero", arg1, arg2 );

        System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr
        System.out.println( "result: " + result );

        // Try to prove something about the result: that it must return either 3 or 8
        SimulatorValue q = sim.or( sim.eq( result, machine.makeWord( 3 ) ),
                                   sim.eq( result, machine.makeWord( 8 ) ) );

        // Negate in hopes to get UNSAT
        q = sim.not(q);

        // Print the generated query term
        sim.printTerm( q );

        // Try to prove it: expect to get false (i.e., UNSAT)
        System.out.println( "ABC sat answer: " + sim.checkSatWithAbc( q ) );

        // // Also write out an SMTLib2 version of the problem
        //sim.writeSmtlib2( "first_zero.smt2", q );

        // Export an AIGER of the function itself
        sim.writeAIGER("first_zero.aiger", result );

        sim.printTerm( result );

        //sim.writeSAW("first_zero.sawext", result );
    }

    /*
Some basic AES test vectors.

  plain-text:
    6bc1bee22e409f96e93d7e117393172a
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

    public static void testAES( SAWSimulator sim,
                                MachineState machine,
                                int[] keyBytes,
                                int[] inputBytes,
                                int[] outputBytes )
        throws Exception
    {
        // Choose some arbitrary locations for buffers
        SimulatorValue keyAddr    = machine.makeWord( 0x7000l );
        SimulatorValue inputAddr  = machine.makeWord( 0x7100l );
        SimulatorValue outputAddr = machine.makeWord( 0x7200l );

        // Set up the key
        for( int i=0; i < keyBytes.length; i++ ) {
            machine.poke( sim.bvAdd( keyAddr, machine.makeWord( i ) ), 1,
                          sim.bvLiteral( 8, keyBytes[i] ) );
        }

        SimulatorValue symInput = sim.freshConstant( VarType.vector( 16, VarType.bitvector(8) ) );

        // Set up the input
        for( int i=0; i < inputBytes.length; i++ ) {
            machine.poke( sim.bvAdd( inputAddr, machine.makeWord( i ) ), 1,
                          sim.vectorGetEntry( symInput, sim.natLiteral( i ) ) );

            //machine.poke( sim.bvAdd( inputAddr, machine.makeWord( i ) ), 1,
            //              sim.bvLiteral( 8, inputBytes[i] ) );
        }

        // make the simulator a bit more chatty
        sim.setVerbosity( 2 );

        // set up the stack register(s)
        machine.initStack( BigInteger.valueOf( 0x4000l ) );

        // Make up some arbitrary return address
        SimulatorValue retAddr = machine.makeWord( 0xdeadbeefl );

        // Call a function!
        machine.callFunction( retAddr, "_AES128_ECB_encrypt", inputAddr, keyAddr, outputAddr );

        System.out.println( "finalpc: " + machine.currentPC ); // should be retAddr

        // SimulatorValue p = sim.boolLiteral( true );
        // for( int i=0; i<inputBytes.length; i++ ) {
        //     SimulatorValue in = sim.bvLiteral( 8, inputBytes[i] );
        //     p = sim.and( p, sim.eq( in, symInputs[i] ) );
        // }

        // SimulatorValue q = sim.boolLiteral( true );
        // for( int i=0; i < outputBytes.length; i++ ) {
        //     SimulatorValue out = machine.peek( sim.bvAdd( outputAddr, machine.makeWord( i ) ), 1 );
        //     q = sim.and( q, sim.eq( out, sim.bvLiteral( 8, outputBytes[i] ) ) );

        //     //System.out.println( out );
        // }

        // //q = sim.not( q );
        // q = sim.and( p, sim.not( q ) );

        // System.out.println("Answer: " + q );

        SimulatorValue[] xs = new SimulatorValue[16];
        for( int i=0; i < xs.length; i++ ) {
            xs[i] = machine.peek( sim.bvAdd( outputAddr, machine.makeWord( i ) ), 1 );
        }
        SimulatorValue output = sim.bvConcat( xs );
        sim.writeSAW( "aes.sawcore", output );

        //SimulatorValue v = machine.peek( outputAddr, 16 );
        //sim.printTerm( v );

        // Yea!
        //System.out.println( "ABC sat answer: " + sim.checkSatWithAbc( q ) );

        //sim.writeSmtlib2( "aes.smt2", q );
        //sim.writeYices( "aes.yices", q );
        //System.out.println( "Yices sat answer: " + sim.checkSatWithYices( q ) );

    }

}
