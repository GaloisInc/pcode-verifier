package com.galois.symbolicSimulator;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.*;
import javax.xml.transform.dom.*;
import org.xml.sax.*;

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

	Document doc;
	Element root;
	NodeList topNodes;

	PCodeProgram program;
	PrintStream out;

    void getDoc(String file)
	throws Exception
    {
	// Here we do a bunch of dumb stuff to make sure our XML parser
	// keeps track of source line and column numbers and attaches
	// them to the generated DOM nodes.

	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	TransformerFactory transformerFactory = TransformerFactory.newInstance();
	Transformer nullTransformer = transformerFactory.newTransformer();
	DocumentBuilder db = dbf.newDocumentBuilder();

	doc = db.newDocument();
	DOMResult domResult = new DOMResult(doc);

	SAXParserFactory saxFactory = SAXParserFactory.newInstance();
	SAXParser sax = saxFactory.newSAXParser();
	XMLReader xmlReader = sax.getXMLReader();

	LocationAnnotator locAnn = new LocationAnnotator( xmlReader, doc );

	InputSource input = new InputSource( file );
	SAXSource saxSource = new SAXSource( locAnn, input );

	nullTransformer.transform( saxSource, domResult );

	doc.getDocumentElement().normalize();
	root = doc.getDocumentElement();
	topNodes = root.getChildNodes();
    }

    void getDoc2(String file)
	throws Exception
    {
	// Simpler way to parse that doesn't track line/column numbers

	DocumentBuilderFactory dbf;
	DocumentBuilder db;

	dbf = DocumentBuilderFactory.newInstance();
	db = dbf.newDocumentBuilder();
	doc = db.parse(file);
	doc.getDocumentElement().normalize();
	root = doc.getDocumentElement();
	topNodes = root.getChildNodes();
    }

    LocationData getLoc( Node n ) {
	LocationData loc = null;
	Object o = n.getUserData(LocationData.LOCATION_DATA_KEY);
	if( o != null ) {
	    loc = (LocationData) o;
	}
	return loc;
    }


	public PCodeParser(String file, PrintStream o) {
	    try {
		getDoc(file);
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
				for (PCodeFunction f : interpreter.p.getFunctions() ) {
					out.println("name: " + f.name);
				}
			} else {
				out.println("{browse|run <func_name>|quit}");
			}
			out.print("> ");
		}
		out.println("Bye!");
	}

        public PCodeProgram parseProgram() {
	    return parseProgram(this.topNodes);
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
		ret.loc = getLoc(n);

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

				NodeList innerElts = funcElt.getChildNodes();
				for(int j=0; j < innerElts.getLength(); j++) {
				    Node innerNode = innerElts.item(j);
				    if(!(innerNode instanceof Element)) continue;

				    Element innerElt = (Element) innerNode;
				    String innerName = innerElt.getNodeName();

				    if( innerName.equals("addr") ) {
					String space_name = innerElt.getAttribute("space");
					BigInteger offset = parseBigHex(innerElt.getAttribute("offset"));
					int size = 1; // FIXME? is this right? does it matter?
					ret.macroEntryPoint = new Varnode( program, space_name, offset, size );
				    }
				    // FIXME... bunch of other stuff here....
				}

			} else if (eltName.startsWith("parameter_description")) {
				// need to give Sean a function with defined parameters to know what to do here
			} else if (eltName.startsWith("basicblock")) {
				PCodeBasicBlock block = parseBlock(funcElt, firstBlock, ret);
				Varnode newBlockHead = block.blockBegin;
				firstBlock = false;
				ret.basicBlocks.add(block);
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
	private PCodeBasicBlock parseBlock(Element blockElt, boolean firstBlock, PCodeFunction function) {
		NodeList ops = blockElt.getChildNodes();
		PCodeBasicBlock block = new PCodeBasicBlock();
		block.loc = getLoc(blockElt);

		boolean firstOp = true;
		for (int i = 0; i < ops.getLength(); i++) {
			Node opNode = ops.item(i);
			if (opNode instanceof Element) {
				Element opElt = (Element) opNode;
				PCodeOp op = parseOp(opElt, firstOp, firstBlock, function);
				firstOp = false;
				Varnode vn = program.codeSegment.addOp(op, program.codeSegment, program);
				if (block.blockBegin == null) {
					block.blockBegin = vn;
				}
				if (vn != null) {
				    block.blockEnd = vn;
				}
			}
		}

		if( block.blockBegin == null ) {
		    throw new Error("Empty basic block at " + block.loc.toString());
		}

		return block;
	}

	public PCodeOp parseOp(Element op, boolean firstInBlock, boolean firstInFunction, PCodeFunction f) {
		String opcode = op.getAttribute("mnemonic");
		String space_id = null;
		NodeList argNodes = op.getChildNodes();
		Varnode[] args = new Varnode[4];
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
					if (argi >= args.length) {
						throw new Error("Too many arguments");
					}
					args[argi++] = parseVarnode(argE);
				} else if (argTag.equals("seqnum")) {
					uniq = Integer.decode(argE.getAttribute("uniq"));
					offset = parseBigHex(argE.getAttribute("offset"));
				} else if (argTag.equals("void")){
				        // skip a slot when we encounter the void tag
   				        argi++;
					continue;
				} else if (argTag.equals("spaceid")){
				        space_id = argE.getAttribute("name");
					continue;
				} else {
					out.println("unexpected tag " + argTag + " where opcode arg belongs " + op.toString());
				}
			}
		}

		PCodeOp ret = new PCodeOp(PCodeOp.PCodeOpCode.valueOf(opcode),
					  space_id, args[0], args[1], args[2], args[3],
					  offset, uniq, firstInBlock, firstInFunction, f);
		ret.loc = getLoc( op );

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
		ArrayList<AddrValuePair> addrPairs = new ArrayList<AddrValuePair>();
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
