package android.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class Xml {
    public static String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";

    public static XmlPullParser newPullParser() {
        XmlPullParser p = new org.xmlpull.v1.SimpleXmlPullParser();
        try {
            p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true);
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (XmlPullParserException ignored) {}
        return p;
    }

    public static void parse(String xml, org.xml.sax.ContentHandler contentHandler) throws org.xml.sax.SAXException {
        try {
            parse(new StringReader(xml), contentHandler);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static void parse(Reader in, org.xml.sax.ContentHandler contentHandler) throws IOException, org.xml.sax.SAXException {
        org.xml.sax.XMLReader reader = new libcore.xml.PullSaxReader();
        reader.setContentHandler(contentHandler);
        reader.parse(new org.xml.sax.InputSource(in));
    }

    public static void parse(InputStream in, Encoding encoding, org.xml.sax.ContentHandler contentHandler)
            throws IOException, org.xml.sax.SAXException {
        org.xml.sax.XMLReader reader = new libcore.xml.PullSaxReader();
        reader.setContentHandler(contentHandler);
        org.xml.sax.InputSource source = new org.xml.sax.InputSource(in);
        source.setEncoding(encoding.expatName);
        reader.parse(source);
    }

    public static XmlSerializer newSerializer() { return new org.xmlpull.v1.SimpleXmlSerializer(); }

    public static AttributeSet asAttributeSet(XmlPullParser parser) {
        if (parser instanceof AttributeSet) return (AttributeSet) parser;
        return new XmlPullAttributes(parser);
    }

    public enum Encoding {
        US_ASCII("US-ASCII"), UTF_8("UTF-8"), UTF_16("UTF-16"), ISO_8859_1("ISO-8859-1");
        final String expatName;
        Encoding(String expatName) { this.expatName = expatName; }
    }

    public static Encoding findEncodingByName(String encodingName) throws java.io.UnsupportedEncodingException {
        if (encodingName == null) return Encoding.UTF_8;
        for (Encoding encoding : Encoding.values()) if (encoding.expatName.equalsIgnoreCase(encodingName)) return encoding;
        throw new java.io.UnsupportedEncodingException(encodingName);
    }
}
