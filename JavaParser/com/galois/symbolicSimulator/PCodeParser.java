package com.galois.symbolicSimulator;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class AddrValuePair {
	public AddrValuePair(String addr, String val) {
		address = PCodeParser.parseBigHex(addr.substring(2));
		value = Integer.parseInt(val.substring(2), 16);
	}
	BigInteger address;
	Integer value;
}

// Top-level class for parsing PCode XML
public class PCodeParser {
	Node programNode;
	List<Node> functionNodes;
	List<Node> spaceNodes;
	DocumentBuilderFactory dbf;
	DocumentBuilder db;
	Document doc;
	Element root;
	NodeList topNodes;

	PCodeProgram program;
	PrintStream out;
	
	public PCodeParser(String file, PrintStream o) {
		try {
			dbf = DocumentBuilderFactory.newInstance();
			db = dbf.newDocumentBuilder();
			doc = db.parse(file);
			doc.getDocumentElement().normalize();
			root = doc.getDocumentElement();
			topNodes = root.getChildNodes();
			out = o;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		PrintStream out = System.out;
		if (args.length == 0) {
			out.println("Usage: PCodeParser pcodefile.xml");
			return;
		}
		PCodeParser p = new PCodeParser(args[0], out);
		p.parseProgram(p.topNodes);
		Scanner in = new Scanner(System.in);
		boolean done = false;
		PCodeInterpreter interpreter = new PCodeInterpreter(p.program, out);
		out.print("> ");
		while (!done && in.hasNextLine()) {
			String cmd = in.next();
			if (cmd.contains("quit")) { 
				done = true;
				continue;
			} else if (cmd.contains("run")) {
				String funcToRun = in.next();
				in.nextLine();
				done = interpreter.runInteractive(funcToRun, in); // TODO: parse and pass args
			} else if (cmd.contains("bro")) {
				out.println("Functions:");
				for (Enumeration<PCodeFunction> e = interpreter.p.functions.elements(); e.hasMoreElements(); ) {
					PCodeFunction f = e.nextElement();
					out.println("name: " + f.name);
				}
			} else {
				out.println("{browse|run <func_name>|quit}");
			}
			out.print("> ");
		}
		out.println("Bye!");
	}

	public PCodeProgram parseProgram(NodeList topElts) {
		program = new PCodeProgram();
		
		for (int i = 0; i < topElts.getLength(); i++) {
			Node n = topElts.item(i);
			if (!(n instanceof Element)) continue;
			Element elt = (Element) n;
			String tag = n.getNodeName();
			if (tag.startsWith("data_segment")) {
				parseDataSegment(n, program.dataSegment);
			} else if (tag.startsWith("function_description")) {
				PCodeFunction parsedFunction = parseFunction(n);
				program.functions.put(parsedFunction.name, parsedFunction);
			} else if (tag.startsWith("endian")) {
				String isBig = elt.getAttribute("isBigEndian");
				program.archSpec.bigEndianP = isBig.toLowerCase().startsWith("true");
			} else if (tag.startsWith("wordSize")) {
				String wordSize = elt.getAttribute("bits");
				program.archSpec.wordSize = Integer.parseInt(wordSize)/8;
			} 
		}
		return program;
	}
	
	private PCodeFunction parseFunction(Node n) {
		PCodeFunction ret = new PCodeFunction();
		NodeList functionElts = n.getChildNodes();
		int startPC = program.codeSegment.microIndex;
		boolean firstBlock = true;
		for (int i = 0; i < functionElts.getLength(); i++) {
			Node funcNode = functionElts.item(i);
			if (!(funcNode instanceof Element)) continue;
			Element funcElt = (Element) funcNode;
			String eltName = funcElt.getNodeName();
			// at this level, the element will be function, parameter_description, or basicblock
			if (eltName.startsWith("function")) {
				ret.name = funcElt.getAttribute("name"); // could extract "size" here too, don't know what it does
				out.println("Parsing function " + ret.name);
			} else if (eltName.startsWith("parameter_description")) {
				// need to give Sean a function with defined parameters to know what to do here
			} else if (eltName.startsWith("basicblock")) {
				Varnode newBlockHead = parseBlock(funcElt, firstBlock, ret);
				firstBlock = false;
				if (ret.macroEntryPoint == null) {
					ret.macroEntryPoint = newBlockHead;
				}
			}
		}
		if (ret.macroEntryPoint == null) {
			// external function, most likely
			ret.macroEntryPoint = new Varnode (program.dataSegment, BigInteger.ZERO, 0);
		}
		ret.length = program.codeSegment.microIndex - startPC; // a bit hacky, but what else can we do?
		return ret;
	}
	
	/*
	 * <op mnemonic="COPY" code="1"><seqnum space="ram" offset="0x0" uniq="0x0"/><addr space="unique" offset="0x1c30" size="8"/><addr space="register" offset="0x28" size="8"/></op>
     * <op mnemonic="INT_SUB" code="20"><seqnum space="ram" offset="0x0" uniq="0x1"/><addr space="register" offset="0x20" size="8"/><addr space="register" offset="0x20" size="8"/><addr space="const" offset="0x8" size="8"/></op>
	 */
	private Varnode parseBlock(Element blockElt, boolean firstBlock, PCodeFunction function) {
		NodeList ops = blockElt.getChildNodes();
		Varnode ret = null;
		boolean firstOp = true;
		for (int i = 0; i < ops.getLength(); i++) {
			Node opNode = ops.item(i);
			if (opNode instanceof Element) {
				Element opElt = (Element) opNode;
				PCodeOp op = parseOp(opElt, firstOp, firstBlock, function);
				firstOp = false;
				Varnode vn = program.codeSegment.addOp(op, program.codeSegment, program);
				if (ret == null) {
					ret = vn;
				}
			}
		}
		return ret;
	}

	public PCodeOp parseOp(Element op, boolean firstInBlock, boolean firstInFunction, PCodeFunction f) {
		String opcode = op.getAttribute("mnemonic");
		NodeList argNodes = op.getChildNodes();
		Varnode[] args = new Varnode[3];
		BigInteger offset = null;
		int uniq = -1;

		int argi = 0;
		for (int i = 0; i < argNodes.getLength(); i++) {
			Node argNode = argNodes.item(i);
			if (argNode instanceof Element) {
				// seqnum or varnode
				Element argE = (Element) argNode;
				String argTag = argE.getNodeName();
				if (argTag.equals("addr")) {
					args[argi++] = parseVarnode(argE);
				} else if (argTag.equals("seqnum")) {
					uniq = Integer.decode(argE.getAttribute("uniq"));
					offset = parseBigHex(argE.getAttribute("offset"));
				} else if (argTag.equals("void")){ 
					continue;
				} else if (argTag.equals("spaceid")){
					continue;
				} else {
					out.println("unexpected tag " + argTag + " where opcode arg belongs " + op.toString());
				}
			}
		}
		
		PCodeOp ret = new PCodeOp(PCodeOp.PCodeOpCode.valueOf(opcode), args[0],args[1],args[2],offset,uniq,firstInBlock, firstInFunction, f);

		return ret;
	}
	
	private Varnode parseVarnode(Element argNode) {
		Varnode ret = new Varnode(program);
		String offset = argNode.getAttribute("offset");
		String size = argNode.getAttribute("size");
		ret.arch = program.archSpec;

		ret.offset = parseBigHex(offset);
		ret.size = Integer.decode(size);
		ret.space_name = argNode.getAttribute("space");
		return ret;
	}

	public void parseDataSegment(Node ds, PCodeSpace dataSegment) {
		NodeList addrBytePairs = ds.getChildNodes();
		// long maxAddr = 0;
		ArrayList<AddrValuePair> addrPairs = new ArrayList<>();
		for (int i = 0; i < addrBytePairs.getLength(); i++) {
			Node avpNode = addrBytePairs.item(i);
			if (avpNode instanceof Element) {
				Element avpElt = (Element) avpNode;
				String addr = avpElt.getAttribute("address");
				String val = avpElt.getAttribute("value");
				AddrValuePair avp = new AddrValuePair(addr, val);
				addrPairs.add(avp);
				// if (avp.address > maxAddr) {maxAddr = avp.address;}
			} 
		}
		// dataSegment.length = maxAddr; // todo later - check if base + offset would be better
		for (Iterator<AddrValuePair>i = addrPairs.iterator(); i.hasNext();) {
			AddrValuePair e = i.next();
			dataSegment.contents.put(e.address, e.value);
			// out.println("@ " + e.address + " -> " + e.value);
		}
		dataSegment.wordsize = 8;
	}
	public static BigInteger parseBigHex(String hexNum) {
		if (hexNum.startsWith("0x")) {
			hexNum = hexNum.substring(2);
		}
		BigInteger t = new BigInteger(hexNum,16);
		return t;
	}

}
