package org.apache.harmony.xml.dom;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** A simple (non-live) NodeList. Framework-internal; public for the XPath implementation. */
public class NodeListImpl implements NodeList {
    private final List<Node> nodes;

    public NodeListImpl() {
        nodes = new ArrayList<Node>();
    }

    public NodeListImpl(List<? extends Node> list) {
        nodes = new ArrayList<Node>(list);
    }

    public void add(Node node) {
        nodes.add(node);
    }

    public Node item(int index) {
        return index < 0 || index >= nodes.size() ? null : nodes.get(index);
    }

    public int getLength() {
        return nodes.size();
    }
}
