package org.apache.harmony.xml.dom;

import org.w3c.dom.Comment;
import org.w3c.dom.Node;

public final class CommentImpl extends CharacterDataImpl implements Comment {
    public CommentImpl(DocumentImpl document, String data) {
        super(document, data);
    }

    public String getNodeName() {
        return "#comment";
    }

    public short getNodeType() {
        return Node.COMMENT_NODE;
    }

    NodeImpl cloneInto(DocumentImpl target, boolean deep) {
        return new CommentImpl(target, getData());
    }
}
