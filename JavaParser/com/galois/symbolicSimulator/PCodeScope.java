package com.galois.symbolicSimulator;

import java.util.Dictionary;

// these are in the XML, but not yet sure what they do.
// hopefully getting PCode programs with scopes in them soon
public class PCodeScope {
	Dictionary<String, Varnode> symbols; // TODO: is this right? Should there be a separate Symbol type?
}
