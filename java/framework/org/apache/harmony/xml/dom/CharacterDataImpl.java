package org.apache.harmony.xml.dom;

import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;

public abstract class CharacterDataImpl extends LeafNodeImpl implements CharacterData {
    StringBuilder buffer;

    CharacterDataImpl(DocumentImpl document, String data) {
        super(document);
        buffer = new StringBuilder(data == null ? "" : data);
    }

    public String getData() throws DOMException {
        return buffer.toString();
    }

    public void setData(String data) throws DOMException {
        buffer = new StringBuilder(data == null ? "" : data);
    }

    public String getNodeValue() {
        return getData();
    }

    public void setNodeValue(String value) {
        setData(value);
    }

    public String getTextContent() {
        return getData();
    }

    public void setTextContent(String value) {
        setData(value);
    }

    public int getLength() {
        return buffer.length();
    }

    private void checkOffset(int offset, int count) {
        if (offset < 0 || offset > buffer.length() || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "INDEX_SIZE_ERR: The index or size is negative, or "
                    + "greater than the allowed value.");
        }
    }

    public String substringData(int offset, int count) throws DOMException {
        checkOffset(offset, count);
        return buffer.substring(offset, (int) Math.min((long) offset + count, buffer.length()));
    }

    public void appendData(String arg) throws DOMException {
        buffer.append(arg);
    }

    public void insertData(int offset, String arg) throws DOMException {
        checkOffset(offset, 0);
        buffer.insert(offset, arg);
    }

    public void deleteData(int offset, int count) throws DOMException {
        checkOffset(offset, count);
        buffer.delete(offset, (int) Math.min((long) offset + count, buffer.length()));
    }

    public void replaceData(int offset, int count, String arg) throws DOMException {
        checkOffset(offset, count);
        buffer.replace(offset, (int) Math.min((long) offset + count, buffer.length()), arg);
    }
}
