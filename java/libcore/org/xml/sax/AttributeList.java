package org.xml.sax;

/** SAX1 attribute list (deprecated in SAX2; kept for HandlerBase/Parser signatures). */
@Deprecated
public interface AttributeList {
    int getLength();
    String getName(int i);
    String getType(int i);
    String getValue(int i);
    String getType(String name);
    String getValue(String name);
}
