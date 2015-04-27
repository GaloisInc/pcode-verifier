package com.galois.symbolicSimulator;

import java.util.Stack;

import org.w3c.dom.*;
import org.w3c.dom.events.*;
import org.xml.sax.helpers.*;
import org.xml.sax.*;

public class LocationAnnotator extends XMLFilterImpl {
    private Locator locator;
    //private Element lastAddedElement;
    private Stack<Locator> locatorStack = new Stack<Locator>();
    private Stack<Element> elementStack = new Stack<Element>();
    private UserDataHandler dataHandler = new LocationDataHandler();

    LocationAnnotator(XMLReader xmlReader, Document dom) {
        super(xmlReader);

        // Add listener to DOM, so we know which node was added.
        EventListener modListener = new EventListener() {
            @Override
            public void handleEvent(Event e) {
                EventTarget target = ((MutationEvent) e).getTarget();
                elementStack.push((Element) target);
                //lastAddedElement = (Element) target;
            }
        };
        ((EventTarget) dom).addEventListener("DOMNodeInserted",
                modListener, true);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    @Override
    public void startElement(String uri, String localName,
            String qName, Attributes atts) throws SAXException {
        super.startElement(uri, localName, qName, atts);

        // Keep snapshot of start location,
        // for later when end of element is found.
        locatorStack.push(new LocatorImpl(locator));
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {

        // Mutation event fired by the adding of element end,
        // and so lastAddedElement will be set.
        super.endElement(uri, localName, qName);

        if (locatorStack.size() > 0) {
            Locator startLocator = locatorStack.pop();

            LocationData location = new LocationData(
                    startLocator.getSystemId(),
                    startLocator.getLineNumber(),
                    startLocator.getColumnNumber(),
                    locator.getLineNumber(),
                    locator.getColumnNumber());

            Element e = elementStack.pop();
            e.setUserData(
                    LocationData.LOCATION_DATA_KEY, location,
                    dataHandler);
        }
    }

    // Ensure location data copied to any new DOM node.
    private class LocationDataHandler implements UserDataHandler {

        @Override
        public void handle(short operation, String key, Object data,
                Node src, Node dst) {

            if (src != null && dst != null) {
                LocationData locatonData = (LocationData)
                        src.getUserData(LocationData.LOCATION_DATA_KEY);

                if (locatonData != null) {
                    dst.setUserData(LocationData.LOCATION_DATA_KEY,
                            locatonData, dataHandler);
                }
            }
        }
    }
}
