package org.xml.sax;

import java.io.IOException;
import java.util.Locale;

/** SAX1 parser (deprecated in SAX2; no SAX1 implementation is provided, see SAXParser.getParser). */
@Deprecated
public interface Parser {
    void setLocale(Locale locale) throws SAXException;
    void setEntityResolver(EntityResolver resolver);
    void setDTDHandler(DTDHandler handler);
    void setDocumentHandler(DocumentHandler handler);
    void setErrorHandler(ErrorHandler handler);
    void parse(InputSource source) throws SAXException, IOException;
    void parse(String systemId) throws SAXException, IOException;
}
