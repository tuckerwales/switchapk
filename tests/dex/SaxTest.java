// SAX2 over javax.xml.parsers: namespaces, prefixes, attributes, text, CDATA, comments, PIs, errors.
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.AttributesImpl;

public class SaxTest {
    static final String DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- head -->\n"
            + "<r:root xmlns:r=\"urn:r\" xmlns=\"urn:d\" a=\"1\" r:b=\"x &amp; y\">\n"
            + "  <child id='c1'>text &lt;here&gt; café</child>\n"
            + "  <?target some data?>\n"
            + "  <empty/>\n"
            + "  <![CDATA[<raw>]]>\n"
            + "  <inner xmlns:q=\"urn:q\"><q:x q:k=\"v\"/></inner>\n"
            + "</r:root>\n";

    static class Log extends DefaultHandler2 {
        final StringBuilder text = new StringBuilder();
        void flush() {
            if (text.length() > 0) {
                System.out.println("  chars [" + text.toString().replace("\n", "\\n") + "]");
                text.setLength(0);
            }
        }
        public void startDocument() { System.out.println("  startDocument"); }
        public void endDocument() { flush(); System.out.println("  endDocument"); }
        public void startPrefixMapping(String p, String u) { flush(); System.out.println("  startPrefix '" + p + "' " + u); }
        public void endPrefixMapping(String p) { flush(); System.out.println("  endPrefix '" + p + "'"); }
        public void startElement(String uri, String local, String q, Attributes a) {
            flush();
            StringBuilder sb = new StringBuilder("  start {" + uri + "}" + local + " q=" + q);
            for (int i = 0; i < a.getLength(); i++) {
                sb.append(" [").append(a.getURI(i)).append('|').append(a.getLocalName(i)).append('|')
                        .append(a.getQName(i)).append('|').append(a.getType(i)).append('=').append(a.getValue(i)).append(']');
            }
            System.out.println(sb);
        }
        public void endElement(String uri, String local, String q) { flush(); System.out.println("  end {" + uri + "}" + local + " q=" + q); }
        public void characters(char[] ch, int s, int l) { text.append(ch, s, l); }
        public void processingInstruction(String t, String d) { flush(); System.out.println("  pi " + t + " [" + d + "]"); }
        public void comment(char[] ch, int s, int l) { flush(); System.out.println("  comment [" + new String(ch, s, l) + "]"); }
        public void startCDATA() { flush(); System.out.println("  startCDATA"); }
        public void endCDATA() { flush(); System.out.println("  endCDATA"); }
    }

    static void run(String label, boolean ns, boolean prefixes, boolean lexical) throws Exception {
        System.out.println(label);
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(ns);
        if (prefixes) f.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
        SAXParser p = f.newSAXParser();
        System.out.println("  namespaceAware=" + p.isNamespaceAware() + " validating=" + p.isValidating());
        XMLReader r = p.getXMLReader();
        Log log = new Log();
        r.setContentHandler(log);
        if (lexical) r.setProperty("http://xml.org/sax/properties/lexical-handler", log);
        r.parse(new InputSource(new ByteArrayInputStream(DOC.getBytes("UTF-8"))));
    }

    public static void main(String[] args) throws Exception {
        run("ns", true, false, true);
        run("ns+prefixes", true, true, false);
        run("no-ns", false, false, false);

        System.out.println("SAXParser.parse(InputStream, DefaultHandler)");
        SAXParserFactory.newInstance().newSAXParser()
                .parse(new ByteArrayInputStream("<a x='1'>hi</a>".getBytes("UTF-8")), new Log());

        System.out.println("reader parse");
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        XMLReader r = f.newSAXParser().getXMLReader();
        r.setContentHandler(new Log());
        r.parse(new InputSource(new StringReader("<a><b>1</b><b>2</b></a>")));

        System.out.println("errors");
        final int[] fatal = {0};
        r.setErrorHandler(new DefaultHandler2() {
            public void fatalError(SAXParseException e) { fatal[0]++; }
        });
        try {
            r.parse(new InputSource(new StringReader("<a><b></a>")));
            System.out.println("  no error");
        } catch (SAXParseException e) {
            System.out.println("  SAXParseException fatal=" + fatal[0]);
        }

        System.out.println("features");
        System.out.println("  namespaces=" + r.getFeature("http://xml.org/sax/features/namespaces"));
        System.out.println("  prefixes=" + r.getFeature("http://xml.org/sax/features/namespace-prefixes"));
        try {
            r.getFeature("http://example.com/nope");
            System.out.println("  recognized?");
        } catch (org.xml.sax.SAXNotRecognizedException e) {
            System.out.println("  SAXNotRecognizedException");
        }

        System.out.println("AttributesImpl");
        AttributesImpl a = new AttributesImpl();
        a.addAttribute("u", "l", "p:l", "CDATA", "v");
        a.addAttribute("", "m", "m", "ID", "w");
        System.out.println("  " + a.getLength() + " " + a.getIndex("u", "l") + " " + a.getIndex("m") + " "
                + a.getValue("p:l") + " " + a.getType("m") + " " + a.getValue("u", "l"));
        a.removeAttribute(0);
        System.out.println("  " + a.getLength() + " " + a.getQName(0) + " " + a.getIndex("p:l"));

        System.out.println("SAXException");
        SAXException e = new SAXException("outer", new IllegalStateException("inner"));
        System.out.println("  " + e.getMessage() + " " + e.getException().getMessage() + " " + (e.getCause() == e.getException()));
        System.out.println("  " + new SAXException(new IllegalStateException("only")).getException().getMessage());
        SAXParseException pe = new SAXParseException("msg", "pub", "sys", 3, 7);
        System.out.println("  " + pe.getLineNumber() + ":" + pe.getColumnNumber() + " " + pe.getPublicId() + " " + pe.getSystemId());
    }
}
