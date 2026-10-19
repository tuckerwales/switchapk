package org.apache.harmony.xml.dom;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Node;

public final class CDATASectionImpl extends TextImpl implements CDATASection {
    public CDATASectionImpl(DocumentImpl document, String data) {
        super(document, data);
    }

    public String getNodeName() {
        return "#cdata-section";
    }

    public short getNodeType() {
        return Node.CDATA_SECTION_NODE;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        return new CDATASectionImpl(target, getData());
    }
}
