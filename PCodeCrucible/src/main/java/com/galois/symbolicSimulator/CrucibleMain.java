package com.galois.symbolicSimulator;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

import com.galois.crucible.*;
import com.galois.crucible.cfg.*;

class CrucibleMain {
    // how many bytes are in the register file
    // this seems to be enough for the X86-64 examples we have
    static final int regFileSize = 1024;

    static final BigInteger rax = BigInteger.valueOf( 0x00l ); // offset value for %rax
    static final BigInteger rsp = BigInteger.valueOf( 0x20l ); // offset value for %rsp
    static final BigInteger rbp = BigInteger.valueOf( 0x28l ); // offset value for %rbp
    static final BigInteger rsi = BigInteger.valueOf( 0x30l ); // offset value for %rsi
    static final BigInteger rdi = BigInteger.valueOf( 0x38l ); // offset value for %rdi


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
            sim.printCFG(proc);

            // Install the generated CFG into the simulator
            sim.useCfg(proc);


            // Start executing at this address
            SimulatorValue entryPoint = sim.bvLiteral( addrWidth, 0x0l );

            // Address of the bottom of the stack
            SimulatorValue bottomOfStack = sim.bvLiteral( addrWidth, 0x4000l );

            // Address to return to
            SimulatorValue returnAddr = sim.bvLiteral( addrWidth, 0xdeadbeefl );

            SimulatorValue initreg = regs.initialRegisters( sim );
            SimulatorValue initram = ram.initialRam( sim, prog.dataSegment );

            SimulatorValue arg1 = sim.bvLiteral( PCodeCrucible.cellWidth, 12 );
            //SimulatorValue arg1 = sim.freshConstant( VarType.bitvector(PCodeCrucible.cellWidth) );

            //SimulatorValue arg2 = sim.bvLiteral( PCodeCrucible.cellWidth, 35 );
            SimulatorValue arg2 = sim.freshConstant( VarType.bitvector(PCodeCrucible.cellWidth) );


            // store arg1 into the low byte of %rdi
            initreg = regs.storeRegister( sim, initreg, rdi, 1, arg1 );

            // store arg2 into the low byte of %rsi
            initreg = regs.storeRegister( sim, initreg, rsi, 1, arg2 );

            // store 0x4000 (start of the stack) into %rsp
            initreg = regs.storeRegister( sim, initreg, rsp, byteWidth, bottomOfStack );

            // store 0x4000 (start of the stack) into %rbp
            initreg = regs.storeRegister( sim, initreg, rbp, byteWidth, bottomOfStack );

            // set the return address on the stack
            initram = ram.storeRAM( sim, initram, bottomOfStack, byteWidth, returnAddr );

            // make the simulator slightly more chatty
            sim.setVerbosity( 2 );

            // Call the simulator!
            SimulatorValue v = sim.runCall( proc.getHandle(), entryPoint, initreg, initram );

            SimulatorValue finalpc  = sim.structGet( 0, v );
            SimulatorValue finalreg = sim.structGet( 1, v );
            SimulatorValue finalram = sim.structGet( 2, v );

            System.out.println( finalpc );

            // Load the function return from %rax
            SimulatorValue result = regs.loadRegister( sim, finalreg, rax, byteWidth );

            SimulatorValue q = sim.eq( result, sim.bvLiteral( addrWidth, 42 ) );
            //q = sim.and( q, sim.eq( arg2, sim.bvLiteral( 8, 30 )) );

            sim.writeSmtlib2( "asdf.smt2", q );

            System.out.println( "Answer: " + sim.checkSatWithAbc( q ) );

        } finally {
            sim.close();
        }
    }
}
