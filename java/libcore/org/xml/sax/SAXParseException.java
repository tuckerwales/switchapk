package org.xml.sax;

public class SAXParseException extends SAXException {
    private static final long serialVersionUID = -5651165872476709336L;
    private String publicId;
    private String systemId;
    private int lineNumber = -1;
    private int columnNumber = -1;

    public SAXParseException(String message, Locator locator) {
        super(message);
        if (locator != null) init(locator.getPublicId(), locator.getSystemId(), locator.getLineNumber(),
                locator.getColumnNumber());
    }

    public SAXParseException(String message, Locator locator, Exception e) {
        super(message, e);
        if (locator != null) init(locator.getPublicId(), locator.getSystemId(), locator.getLineNumber(),
                locator.getColumnNumber());
    }

    public SAXParseException(String message, String publicId, String systemId, int lineNumber, int columnNumber) {
        super(message);
        init(publicId, systemId, lineNumber, columnNumber);
    }

    public SAXParseException(String message, String publicId, String systemId, int lineNumber, int columnNumber,
            Exception e) {
        super(message, e);
        init(publicId, systemId, lineNumber, columnNumber);
    }

    private void init(String publicId, String systemId, int lineNumber, int columnNumber) {
        this.publicId = publicId;
        this.systemId = systemId;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    public String getPublicId() { return publicId; }
    public String getSystemId() { return systemId; }
    public int getLineNumber() { return lineNumber; }
    public int getColumnNumber() { return columnNumber; }

    public String toString() {
        StringBuilder buf = new StringBuilder(getClass().getName());
        String message = getLocalizedMessage();
        if (publicId != null) buf.append("publicId: ").append(publicId);
        if (systemId != null) buf.append("; systemId: ").append(systemId);
        if (lineNumber != -1) buf.append("; lineNumber: ").append(lineNumber);
        if (columnNumber != -1) buf.append("; columnNumber: ").append(columnNumber);
        if (message != null) buf.append("; ").append(message);
        return buf.toString();
    }
}
