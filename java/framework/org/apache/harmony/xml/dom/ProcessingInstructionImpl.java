package org.apache.harmony.xml.dom;

import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

public final class ProcessingInstructionImpl extends LeafNodeImpl implements ProcessingInstruction {
    private final String target;
    private String data;

    ProcessingInstructionImpl(DocumentImpl document, String target, String data) {
        super(document);
        this.target = target;
        this.data = data == null ? "" : data;
    }

    public String getNodeName() {
        return target;
    }

    public short getNodeType() {
        return Node.PROCESSING_INSTRUCTION_NODE;
    }

    public String getNodeValue() {
        return data;
    }

    public void setNodeValue(String value) {
        setData(value);
    }

    public String getTarget() {
        return target;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data == null ? "" : data;
    }

    NodeImpl cloneInto(DocumentImpl doc, boolean deep) {
        return new ProcessingInstructionImpl(doc, target, data);
    }
}
