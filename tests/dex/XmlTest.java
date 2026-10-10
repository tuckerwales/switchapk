import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX through javax.xml.parsers: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 * Adjacent character events are joined, since parsers may split text differently.
 */
public class XmlTest {
    static final String DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!-- top comment -->\n"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"10\">\n"
            + "  <g id=\"a\" style=\"fill:red\">text &amp; more &lt;&#65;&#x42;&gt;</g>\n"
            + "  <use xlink:href=\"#a\"/>\n"
            + "  <![CDATA[raw <data>]]>\n"
            + "  <?pi target data?>\n"
            + "  <text>café 中</text>\n"
            + "</svg>\n";

    static class Recorder extends DefaultHandler2 {
        final StringBuilder out = new StringBuilder();
        final StringBuilder text = new StringBuilder();

        void flush() {
            if (text.length() > 0) {
                String t = text.toString().trim();
                if (!t.isEmpty()) {
                    out.append("  chars [").append(t).append("]\n");
                }
                text.setLength(0);
            }
        }

        public void startDocument() {
            out.append("  startDocument\n");
        }

        public void endDocument() {
            flush();
            out.append("  endDocument\n");
        }

        public void startPrefixMapping(String prefix, String uri) {
            flush();
            out.append("  prefix [").append(prefix).append("] ").append(uri).append('\n');
        }

        public void endPrefixMapping(String prefix) {
            flush();
            out.append("  endPrefix [").append(prefix).append("]\n");
        }

        public void startElement(String uri, String local, String qName, Attributes a) {
            flush();
            out.append("  start uri=").append(uri).append(" local=").append(local).append(" q=").append(qName);
            for (int i = 0; i < a.getLength(); i++) {
                out.append(" {").append(a.getURI(i)).append('|').append(a.getLocalName(i)).append('|')
                        .append(a.getQName(i)).append('=').append(a.getValue(i)).append('}');
            }
            out.append('\n');
        }

        public void endElement(String uri, String local, String qName) {
            flush();
            out.append("  end ").append(qName).append('\n');
        }

        public void characters(char[] ch, int start, int length) {
            text.append(ch, start, length);
        }

        public void processingInstruction(String target, String data) {
            flush();
            out.append("  pi ").append(target).append(" [").append(data).append("]\n");
        }

        public void comment(char[] ch, int start, int length) {
            flush();
            out.append("  comment [").append(new String(ch, start, length).trim()).append("]\n");
        }

        public void startCDATA() {
            flush();
            out.append("  startCDATA\n");
        }

        public void endCDATA() {
            flush();
            out.append("  endCDATA\n");
        }
    }

    static void run(String label, boolean ns, boolean prefixes, boolean lexical) throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(ns);
        SAXParser p = f.newSAXParser();
        XMLReader r = p.getXMLReader();
        if (prefixes) {
            r.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
        }
        Recorder rec = new Recorder();
        r.setContentHandler(rec);
        if (lexical) {
            r.setProperty("http://xml.org/sax/properties/lexical-handler", rec);
        }
        r.parse(new InputSource(new ByteArrayInputStream(DOC.getBytes(StandardCharsets.UTF_8))));
        System.out.println(label + " aware=" + p.isNamespaceAware());
        System.out.print(rec.out);
    }

    public static void main(String[] args) throws Exception {
        run("plain", false, false, false);
        run("namespaces", true, false, true);
        run("prefixes", true, true, false);

        SAXParserFactory f = SAXParserFactory.newInstance();
        System.out.println("factory aware=" + f.isNamespaceAware() + " validating=" + f.isValidating());
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        System.out.println("feature " + f.getFeature("http://xml.org/sax/features/external-general-entities"));

        final StringBuilder sb = new StringBuilder();
        SAXParser p = f.newSAXParser();
        p.parse(new InputSource(new StringReader("<a x='1'><b>hi</b><b/></a>")), new DefaultHandler() {
            public void startElement(String uri, String local, String qName, Attributes a) {
                sb.append('<').append(qName).append(a.getLength() > 0 ? " x=" + a.getValue("x") : "").append('>');
            }

            public void characters(char[] ch, int s, int l) {
                sb.append(ch, s, l);
            }
        });
        System.out.println("reader " + sb);

        try {
            f.newSAXParser().parse(new InputSource(new StringReader("<a><b></a>")), new DefaultHandler());
        } catch (SAXParseException e) {
            System.out.println("malformed " + e.getClass().getName());
        }
        try {
            f.newSAXParser().getXMLReader().setProperty("http://example.com/nope", "x");
        } catch (SAXException e) {
            System.out.println("property " + e.getClass().getName());
        }

        AttributesImpl ai = new AttributesImpl();
        ai.addAttribute("u", "l", "p:l", "CDATA", "v");
        ai.addAttribute("", "m", "m", "CDATA", "w");
        System.out.println("attrs " + ai.getLength() + " " + ai.getIndex("u", "l") + " " + ai.getValue("m") + " "
                + ai.getIndex("p:l"));
        ai.removeAttribute(0);
        System.out.println("removed " + ai.getLength() + " " + ai.getQName(0));
        System.out.println(new SAXException("msg").getMessage() + " " + new SAXParseException("pe", null, null, 3, 4)
                .getLineNumber());
    }
}
