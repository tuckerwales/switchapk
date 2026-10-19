package org.w3c.dom.ls;

public interface LSParserFilter {
    short FILTER_ACCEPT = 1;
    short FILTER_REJECT = 2;
    short FILTER_SKIP = 3;
    short FILTER_INTERRUPT = 4;
    short startElement(org.w3c.dom.Element p0);
    short acceptNode(org.w3c.dom.Node p0);
    int getWhatToShow();
}
