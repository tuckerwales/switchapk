// DOM (org.w3c.dom + DocumentBuilder), the identity Transformer and XPath over the DOM.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;
import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;

public class DomTest {
    static final String DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE shop [\n"
            + "  <!ENTITY brand \"Acme &amp; Co\">\n"
            + "  <!ENTITY promo \"<b>sale</b> now\">\n"
            + "  <!-- a comment with > inside -->\n"
            + "]>\n"
            + "<!-- top comment -->\n"
            + "<shop xmlns=\"urn:shop\" xmlns:p=\"urn:price\" id=\"s1\" name=\"&brand;\">\n"
            + "  <item code=\"A&#65;\" p:currency=\"EUR\"><title>Apple &lt;red&gt;</title><p:amount>1.50</p:amount></item>\n"
            + "  <item code=\"B\" p:currency=\"USD\"><title>Banana &#x263A;</title><p:amount>0.25</p:amount></item>\n"
            + "  <item code=\"C\"><title>Cherry</title><p:amount>12</p:amount><note>&promo;</note></item>\n"
            + "  <![CDATA[raw <data> & more]]>\n"
            + "  <?audit level=\"2\"?>\n"
            + "  <empty/>\n"
            + "</shop>\n";

    static String esc(String s) {
        if (s == null) {
            return "null";
        }
        return s.replace("\n", "\\n").replace("\t", "\\t");
    }

    static void dump(Node n, String indent) {
        StringBuilder sb = new StringBuilder(indent);
        sb.append(n.getNodeType()).append(' ').append(n.getNodeName());
        if (n.getNodeType() == Node.ELEMENT_NODE) {
            sb.append(" local=").append(n.getLocalName()).append(" ns=").append(n.getNamespaceURI())
                    .append(" prefix=").append(n.getPrefix());
            NamedNodeMap attrs = n.getAttributes();
            List<String> list = new ArrayList<String>();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr a = (Attr) attrs.item(i);
                list.add(a.getName() + "{" + a.getNamespaceURI() + "|" + a.getLocalName() + "}=" + esc(a.getValue()));
            }
            Collections.sort(list);
            sb.append(" attrs=").append(list);
        } else if (n.getNodeValue() != null) {
            sb.append(" [").append(esc(n.getNodeValue())).append(']');
        }
        System.out.println(sb);
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
            dump(c, indent + "  ");
        }
    }

    static Document parse(String xml, boolean ns) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(ns);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    static String serialize(Node node, String... props) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        for (int i = 0; i < props.length; i += 2) {
            t.setOutputProperty(props[i], props[i + 1]);
        }
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(node), new StreamResult(w));
        // The JDK adds standalone="no" from Document.getXmlStandalone(); Android's serializer does not.
        // The JDK also breaks the line after the declaration when indenting; Android's Xalan does not.
        String s = w.toString().replace(" standalone=\"no\"", "");
        return s.replaceFirst("^(<\\?xml[^>]*\\?>)\n", "$1");
    }

    static void section(String name) {
        System.out.println("== " + name);
    }

    public static void main(String[] args) throws Exception {
        parsing();
        nonNamespaceAware();
        builderOptions();
        mutation();
        cloneImportAdopt();
        namespaces();
        exceptions();
        transform();
        xpath();
        encodings();
        System.out.println("done");
    }

    static void parsing() throws Exception {
        section("parse namespace aware");
        Document doc = parse(DOC, true);
        System.out.println("version=" + doc.getXmlVersion() + " encoding=" + doc.getXmlEncoding() + " standalone="
                + doc.getXmlStandalone() + " input=" + doc.getInputEncoding());
        System.out.println("doctype=" + doc.getDoctype().getName() + " public=" + doc.getDoctype().getPublicId()
                + " system=" + doc.getDoctype().getSystemId());
        NamedNodeMap ents = doc.getDoctype().getEntities();
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < ents.getLength(); i++) {
            names.add(ents.item(i).getNodeName());
        }
        Collections.sort(names);
        System.out.println("entities=" + names + " brand=" + ents.getNamedItem("brand").getTextContent());
        for (Node c = doc.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() != Node.DOCUMENT_TYPE_NODE) {
                dump(c, "");
            }
        }
        Element root = doc.getDocumentElement();
        System.out.println("root=" + root.getTagName() + " text=" + esc(root.getTextContent()));
        System.out.println("byTag item=" + doc.getElementsByTagName("item").getLength()
                + " p:amount=" + doc.getElementsByTagName("p:amount").getLength()
                + " *=" + doc.getElementsByTagName("*").getLength()
                + " NS amount=" + doc.getElementsByTagNameNS("urn:price", "amount").getLength()
                + " NS *:title=" + doc.getElementsByTagNameNS("*", "title").getLength()
                + " NS shop:*=" + root.getElementsByTagNameNS("urn:shop", "*").getLength());
        Element item = (Element) doc.getElementsByTagName("item").item(0);
        System.out.println("code=" + item.getAttribute("code") + " currency=" + item.getAttributeNS("urn:price", "currency")
                + " missing=[" + item.getAttribute("nope") + "] hasNS=" + item.hasAttributeNS("urn:price", "currency")
                + " has=" + item.hasAttribute("p:currency") + " node=" + item.getAttributeNode("p:currency").getName()
                + " owner=" + item.getAttributeNode("code").getOwnerElement().getTagName());
        System.out.println("first=" + item.getFirstChild().getNodeName() + " last=" + item.getLastChild().getNodeName()
                + " next=" + item.getFirstChild().getNextSibling().getNodeName()
                + " prev=" + item.getLastChild().getPreviousSibling().getNodeName()
                + " parent=" + item.getParentNode().getNodeName() + " owner=" + (item.getOwnerDocument() == doc)
                + " docOwner=" + doc.getOwnerDocument());
    }

    static void nonNamespaceAware() throws Exception {
        section("parse not namespace aware");
        Document doc = parse("<a:r xmlns:a=\"urn:a\" x=\"1\"><a:c y=\"2\">t</a:c></a:r>", false);
        dump(doc.getDocumentElement(), "");
        System.out.println("byTag=" + doc.getElementsByTagName("a:c").getLength());
    }

    static void builderOptions() throws Exception {
        section("builder options");
        String xml = "<r>a<!--c-->b<![CDATA[c]]>d<x/>  </r>";
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        System.out.println("defaults ns=" + f.isNamespaceAware() + " valid=" + f.isValidating() + " coalesce="
                + f.isCoalescing() + " comments=" + f.isIgnoringComments() + " expand=" + f.isExpandEntityReferences()
                + " ws=" + f.isIgnoringElementContentWhitespace());
        dump(f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement(), "");
        f.setIgnoringComments(true);
        dump(f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement(), "");
        f.setCoalescing(true);
        dump(f.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement(), "");
        DocumentBuilder b = f.newDocumentBuilder();
        System.out.println("builder ns=" + b.isNamespaceAware() + " valid=" + b.isValidating());
        Document empty = b.newDocument();
        System.out.println("newDocument children=" + empty.getChildNodes().getLength() + " element="
                + empty.getDocumentElement() + " version=" + empty.getXmlVersion() + " encoding="
                + empty.getXmlEncoding());
        System.out.println("impl Core=" + b.getDOMImplementation().hasFeature("Core", "2.0") + " XML="
                + b.getDOMImplementation().hasFeature("XML", "1.0") + " bogus="
                + b.getDOMImplementation().hasFeature("Bogus", "1.0"));
        Document created = b.getDOMImplementation().createDocument("urn:x", "x:root", null);
        System.out.println("createDocument root=" + created.getDocumentElement().getNodeName() + " ns="
                + created.getDocumentElement().getNamespaceURI());

        File file = new File(System.getProperty("java.io.tmpdir"), "domtest-" + 4242 + ".xml");
        FileOutputStream out = new FileOutputStream(file);
        out.write("<?xml version='1.0'?><file a='b'>from file</file>".getBytes("UTF-8"));
        out.close();
        Document fromFile = b.parse(file);
        System.out.println("file root=" + fromFile.getDocumentElement().getTagName() + " text="
                + fromFile.getDocumentElement().getTextContent() + " uri=" + (fromFile.getDocumentURI() != null));
        Document fromUri = b.parse(file.toURI().toString());
        System.out.println("uri root=" + fromUri.getDocumentElement().getAttribute("a"));
        file.delete();
    }

    static void mutation() throws Exception {
        section("mutation");
        Document doc = parse("<list><a>1</a><b>2</b><c>3</c></list>", false);
        Element list = doc.getDocumentElement();
        Element d = doc.createElement("d");
        d.setAttribute("k", "v");
        d.setAttribute("k", "w");
        d.setAttribute("z", "1");
        d.appendChild(doc.createTextNode("4"));
        list.appendChild(d);
        Element first = doc.createElement("first");
        list.insertBefore(first, list.getFirstChild());
        Element b = (Element) list.getElementsByTagName("b").item(0);
        Element bee = doc.createElement("bee");
        Node old = list.replaceChild(bee, b);
        System.out.println("replaced=" + old.getNodeName() + " parent=" + old.getParentNode());
        list.removeChild(list.getElementsByTagName("c").item(0));
        d.removeAttribute("z");
        d.removeAttribute("absent");
        System.out.println(serialize(list, OutputKeys.OMIT_XML_DECLARATION, "yes"));
        System.out.println("hasChildNodes=" + list.hasChildNodes() + " count=" + list.getChildNodes().getLength()
                + " d.attrs=" + d.getAttributes().getLength() + " hasAttributes=" + first.hasAttributes());

        // moving a node that is already in the tree
        list.appendChild(first);
        System.out.println(serialize(list, OutputKeys.OMIT_XML_DECLARATION, "yes"));

        // fragments
        DocumentFragment frag = doc.createDocumentFragment();
        frag.appendChild(doc.createElement("f1"));
        frag.appendChild(doc.createElement("f2"));
        list.insertBefore(frag, bee);
        System.out.println("fragment children after=" + frag.getChildNodes().getLength());
        System.out.println(serialize(list, OutputKeys.OMIT_XML_DECLARATION, "yes"));

        // text content
        bee.setTextContent("buzz");
        System.out.println("bee text=" + bee.getTextContent() + " list text=" + list.getTextContent());
        list.setTextContent("gone");
        System.out.println("after setTextContent children=" + list.getChildNodes().getLength() + " first="
                + list.getFirstChild().getNodeType());
        list.setTextContent(null);
        System.out.println("after null children=" + list.getChildNodes().getLength());

        // normalize
        Element n = doc.createElement("n");
        n.appendChild(doc.createTextNode("a"));
        n.appendChild(doc.createTextNode(""));
        n.appendChild(doc.createTextNode("b"));
        n.appendChild(doc.createCDATASection("c"));
        n.appendChild(doc.createTextNode("d"));
        Element inner = doc.createElement("i");
        inner.appendChild(doc.createTextNode("x"));
        inner.appendChild(doc.createTextNode("y"));
        n.appendChild(inner);
        Text whole = (Text) n.getChildNodes().item(4);
        System.out.println("wholeText=" + whole.getWholeText());
        n.normalize();
        System.out.println("normalized count=" + n.getChildNodes().getLength() + " first=" + n.getFirstChild().getNodeValue()
                + " inner=" + inner.getChildNodes().getLength());

        // character data
        Text t = doc.createTextNode("hello world");
        n.appendChild(t);
        t.appendData("!");
        t.insertData(0, ">");
        t.deleteData(1, 1);
        t.replaceData(0, 1, "<<");
        System.out.println("data=" + t.getData() + " len=" + t.getLength() + " sub=" + t.substringData(2, 4)
                + " subPastEnd=" + t.substringData(8, 100));
        Text tail = t.splitText(7);
        System.out.println("split=" + t.getData() + "|" + tail.getData() + " sibling=" + (t.getNextSibling() == tail));
        Text replaced = tail.replaceWholeText("whole");
        System.out.println("replaceWholeText=" + replaced.getData() + " count=" + n.getChildNodes().getLength());
        Comment comment = doc.createComment("note");
        ProcessingInstruction pi = doc.createProcessingInstruction("target", "some data");
        System.out.println("comment=" + comment.getNodeName() + "/" + comment.getData() + " pi=" + pi.getTarget()
                + "/" + pi.getData() + " piValue=" + pi.getNodeValue() + " text=" + pi.getTextContent());
        CharacterData cd = doc.createCDATASection("x]]>y");
        System.out.println("cdata=" + cd.getNodeName() + " type=" + cd.getNodeType() + " value=" + cd.getNodeValue());
    }

    static void cloneImportAdopt() throws Exception {
        section("clone import adopt");
        Document doc = parse("<r xmlns:q=\"urn:q\"><e a=\"1\" q:b=\"2\">text<k/></e></r>", true);
        Element e = (Element) doc.getDocumentElement().getFirstChild();
        final List<String> events = new ArrayList<String>();
        UserDataHandler handler = new UserDataHandler() {
            public void handle(short operation, String key, Object data, Node src, Node dst) {
                events.add(operation + ":" + key + "=" + data + " src=" + src.getNodeName() + " dst="
                        + (dst == null ? "null" : dst.getNodeName()));
            }
        };
        System.out.println("setUserData=" + e.setUserData("key", "v1", handler) + " again="
                + e.setUserData("key", "v2", handler) + " get=" + e.getUserData("key") + " missing="
                + e.getUserData("other"));
        Element shallow = (Element) e.cloneNode(false);
        Element deep = (Element) e.cloneNode(true);
        System.out.println("shallow children=" + shallow.getChildNodes().getLength() + " attrs="
                + shallow.getAttributes().getLength() + " parent=" + shallow.getParentNode());
        System.out.println("deep equal=" + deep.isEqualNode(e) + " same=" + deep.isSameNode(e) + " shallowEqual="
                + shallow.isEqualNode(e) + " userData copied=" + deep.getUserData("key"));
        deep.setAttribute("a", "changed");
        System.out.println("after change equal=" + deep.isEqualNode(e));
        Document other = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Node imported = other.importNode(e, true);
        System.out.println("imported owner=" + (imported.getOwnerDocument() == other) + " parent="
                + imported.getParentNode() + " text=" + imported.getTextContent() + " ns attr="
                + ((Element) imported).getAttributeNS("urn:q", "b"));
        other.appendChild(imported);
        Element k = (Element) doc.getElementsByTagName("k").item(0);
        Node adopted = other.adoptNode(k);
        System.out.println("adopted owner=" + (adopted.getOwnerDocument() == other) + " parent=" + adopted.getParentNode()
                + " e children=" + e.getChildNodes().getLength());
        imported.appendChild(adopted);
        System.out.println(serialize(other, OutputKeys.OMIT_XML_DECLARATION, "yes"));
        Document copy = (Document) doc.cloneNode(true);
        System.out.println("doc clone root=" + copy.getDocumentElement().getNodeName() + " equal="
                + copy.isEqualNode(doc));
        for (String s : events) {
            System.out.println("event " + s);
        }
        // positions
        Element r = doc.getDocumentElement();
        Node text = e.getFirstChild();
        System.out.println("pos r->e=" + r.compareDocumentPosition(e) + " e->r=" + e.compareDocumentPosition(r)
                + " text->e=" + text.compareDocumentPosition(e) + " e->self=" + e.compareDocumentPosition(e));
        Element s1 = doc.createElement("s1");
        Element s2 = doc.createElement("s2");
        r.appendChild(s1);
        r.appendChild(s2);
        System.out.println("pos s1->s2=" + s1.compareDocumentPosition(s2) + " s2->s1=" + s2.compareDocumentPosition(s1)
                + " text->s2=" + text.compareDocumentPosition(s2));
    }

    static void namespaces() throws Exception {
        section("namespaces");
        Document doc = parse("<r xmlns=\"urn:d\" xmlns:a=\"urn:a\"><a:x><y xmlns=\"\"/></a:x></r>", true);
        Element r = doc.getDocumentElement();
        Element x = (Element) r.getFirstChild();
        Element y = (Element) x.getFirstChild();
        System.out.println("x ns=" + x.getNamespaceURI() + " y ns=" + y.getNamespaceURI());
        System.out.println("lookupNamespaceURI a=" + y.lookupNamespaceURI("a") + " default@x=" + x.lookupNamespaceURI(null)
                + " default@y=" + y.lookupNamespaceURI(null) + " none=" + y.lookupNamespaceURI("zz"));
        System.out.println("lookupPrefix urn:a=" + y.lookupPrefix("urn:a") + " urn:d=" + x.lookupPrefix("urn:d"));
        System.out.println("isDefaultNamespace x urn:d=" + x.isDefaultNamespace("urn:d") + " y urn:d="
                + y.isDefaultNamespace("urn:d") + " r urn:d=" + r.isDefaultNamespace("urn:d"));
        Element made = doc.createElementNS("urn:m", "m:made");
        made.setAttributeNS("urn:m", "m:attr", "v");
        made.setAttributeNS(null, "plain", "p");
        made.setAttributeNS("urn:m", "other:attr", "v2");
        System.out.println("made name=" + made.getNodeName() + " local=" + made.getLocalName() + " prefix="
                + made.getPrefix() + " attrs=" + made.getAttributes().getLength() + " attrPrefix="
                + made.getAttributeNodeNS("urn:m", "attr").getPrefix() + " value=" + made.getAttributeNS("urn:m", "attr"));
        made.setPrefix("n");
        System.out.println("renamed=" + made.getTagName());
        made.removeAttributeNS("urn:m", "attr");
        System.out.println("after removeNS attrs=" + made.getAttributes().getLength());
        Attr at = doc.createAttributeNS("urn:z", "z:at");
        at.setValue("zv");
        made.setAttributeNodeNS(at);
        System.out.println("attr owner=" + at.getOwnerElement().getLocalName() + " specified=" + at.getSpecified()
                + " byNS=" + made.getAttributeNS("urn:z", "at") + " removed=" + made.removeAttributeNode(at).getName()
                + " owner after=" + at.getOwnerElement());
        Element plain = doc.createElement("plain");
        System.out.println("plain local=" + plain.getLocalName() + " ns=" + plain.getNamespaceURI());
        Element renamed = (Element) doc.renameNode(plain, "urn:r", "r:renamed");
        System.out.println("renameNode=" + renamed.getNodeName() + " ns=" + renamed.getNamespaceURI());
        // serialized with namespace fixup
        Document built = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = built.createElementNS("urn:a", "a:root");
        built.appendChild(root);
        Element child = built.createElementNS("urn:a", "a:child");
        root.appendChild(child);
        child.appendChild(built.createElementNS("urn:b", "b:leaf"));
        System.out.println(serialize(built));
    }

    static void expectDom(String what, Runnable r) {
        try {
            r.run();
            System.out.println(what + ": no exception");
        } catch (DOMException e) {
            System.out.println(what + ": DOMException code=" + e.code);
        }
    }

    static void expectParseError(String what, String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            DocumentBuilder b = f.newDocumentBuilder();
            final boolean[] called = new boolean[1];
            b.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) {
                }

                public void error(SAXParseException e) {
                }

                public void fatalError(SAXParseException e) throws SAXException {
                    called[0] = true;
                    throw e;
                }
            });
            b.parse(new InputSource(new StringReader(xml)));
            System.out.println(what + ": parsed");
        } catch (SAXParseException e) {
            System.out.println(what + ": SAXParseException line>0=" + (e.getLineNumber() > 0));
        } catch (Exception e) {
            System.out.println(what + ": " + e.getClass().getName());
        }
    }

    static void exceptions() throws Exception {
        section("exceptions");
        expectParseError("mismatched", "<a><b></a>");
        expectParseError("unclosed", "<a><b></b>");
        expectParseError("two roots", "<a/><b/>");
        expectParseError("text in prolog", "junk<a/>");
        expectParseError("undefined entity", "<a>&nope;</a>");
        expectParseError("empty", "");
        expectParseError("bad attribute", "<a x=1/>");
        expectParseError("duplicate attribute", "<a x='1' x='2'/>");
        expectParseError("unbound prefix", "<p:a/>");
        expectParseError("ok", "<a/>");

        final Document doc = parse("<r><c/></r>", false);
        final Document other = parse("<o/>", false);
        final Element r = doc.getDocumentElement();
        expectDom("bad name", new Runnable() {
            public void run() {
                doc.createElement("bad name");
            }
        });
        expectDom("wrong document", new Runnable() {
            public void run() {
                r.appendChild(other.getDocumentElement());
            }
        });
        expectDom("not found", new Runnable() {
            public void run() {
                r.removeChild(other.getDocumentElement());
            }
        });
        expectDom("second root", new Runnable() {
            public void run() {
                doc.appendChild(doc.createElement("x"));
            }
        });
        expectDom("text in document", new Runnable() {
            public void run() {
                doc.appendChild(doc.createTextNode("x"));
            }
        });
        expectDom("cycle", new Runnable() {
            public void run() {
                r.getFirstChild().appendChild(r);
            }
        });
        expectDom("split range", new Runnable() {
            public void run() {
                doc.createTextNode("abc").splitText(4);
            }
        });
        expectDom("namespace prefix without uri", new Runnable() {
            public void run() {
                doc.createElementNS(null, "p:x");
            }
        });
        expectDom("xmlns misuse", new Runnable() {
            public void run() {
                doc.createAttributeNS("urn:x", "xmlns:x");
            }
        });
        expectDom("inuse attribute", new Runnable() {
            public void run() {
                Element a = doc.createElement("a");
                Element b = doc.createElement("b");
                Attr at = doc.createAttribute("at");
                a.setAttributeNode(at);
                b.setAttributeNode(at);
            }
        });
        expectDom("remove missing attr node", new Runnable() {
            public void run() {
                r.removeAttributeNode(doc.createAttribute("zz"));
            }
        });
        expectDom("leaf child", new Runnable() {
            public void run() {
                doc.createTextNode("t").appendChild(doc.createTextNode("u"));
            }
        });
        expectDom("import document", new Runnable() {
            public void run() {
                doc.importNode(other, true);
            }
        });
        expectDom("valid", new Runnable() {
            public void run() {
                doc.createElement("good-name_1.x");
            }
        });
    }

    static void transform() throws Exception {
        section("transform");
        Document doc = parse(DOC, true);
        System.out.println(serialize(doc));
        System.out.println(serialize(doc.getDocumentElement().getElementsByTagName("item").item(2)));

        Document built = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = built.createElement("config");
        built.appendChild(root);
        root.appendChild(built.createComment(" settings "));
        Element entry = built.createElement("entry");
        entry.setAttribute("key", "a<b & \"c\" 'd'\n\tnext");
        entry.appendChild(built.createTextNode("x < y && z > w \u00e9\u2603"));
        root.appendChild(entry);
        Element group = built.createElement("group");
        root.appendChild(group);
        group.appendChild(built.createElement("empty"));
        Element leaf = built.createElement("leaf");
        leaf.appendChild(built.createTextNode("value"));
        group.appendChild(leaf);
        root.appendChild(built.createProcessingInstruction("pi", "data"));
        Element cdata = built.createElement("raw");
        cdata.appendChild(built.createCDATASection("<a>]]>b"));
        root.appendChild(cdata);

        System.out.println("[" + serialize(built) + "]");
        System.out.println("[" + serialize(built, OutputKeys.INDENT, "yes", "{http://xml.apache.org/xslt}indent-amount",
                "2") + "]");
        System.out.println("[" + serialize(built, OutputKeys.INDENT, "yes", "{http://xml.apache.org/xslt}indent-amount",
                "4", OutputKeys.OMIT_XML_DECLARATION, "yes") + "]");
        System.out.println("[" + serialize(built, OutputKeys.METHOD, "xml", OutputKeys.ENCODING, "ISO-8859-1") + "]");

        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        t.transform(new DOMSource(entry), new StreamResult(bytes));
        byte[] raw = bytes.toByteArray();
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) {
            if (b < 0) {
                hex.append(String.format("<%02x>", b & 0xff));
            } else {
                hex.append((char) b);
            }
        }
        System.out.println("latin1 bytes=" + esc(hex.toString()));
        System.out.println("getOutputProperty indent=" + t.getOutputProperty(OutputKeys.INDENT) + " encoding="
                + t.getOutputProperty(OutputKeys.ENCODING) + " method=" + t.getOutputProperty(OutputKeys.METHOD));
        try {
            t.setOutputProperty("bogus", "1");
            System.out.println("bogus accepted");
        } catch (IllegalArgumentException e) {
            System.out.println("bogus rejected");
        }

        // stream to stream
        Transformer copy = TransformerFactory.newInstance().newTransformer();
        copy.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter w = new StringWriter();
        copy.transform(new StreamSource(new StringReader("<a x='1'><b>t</b><!--c--></a>")), new StreamResult(w));
        System.out.println("stream copy=" + w);

        // DOM result
        DOMResult result = new DOMResult();
        copy.transform(new DOMSource(built), result);
        Document resultDoc = (Document) result.getNode();
        System.out.println("dom result root=" + resultDoc.getDocumentElement().getNodeName() + " children="
                + resultDoc.getDocumentElement().getChildNodes().getLength());

        // SAX events to a stream through a TransformerHandler
        SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactory.newInstance();
        TransformerHandler th = stf.newTransformerHandler();
        th.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        th.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
        StringWriter hw = new StringWriter();
        th.setResult(new StreamResult(hw));
        th.startDocument();
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "v", "v", "CDATA", "1");
        th.startElement("", "doc", "doc", atts);
        th.startElement("", "item", "item", new AttributesImpl());
        th.characters("hi".toCharArray(), 0, 2);
        th.endElement("", "item", "item");
        th.endElement("", "doc", "doc");
        th.endDocument();
        System.out.println("handler=[" + hw.toString().replaceFirst("^(<\\?xml[^>]*\\?>)\n", "$1") + "]");
    }

    static void xpath() throws Exception {
        section("xpath");
        String books = "<catalog>"
                + "<book id=\"b1\" lang=\"en\"><title>XML Basics</title><price>10.5</price><tag>a</tag><tag>b</tag></book>"
                + "<book id=\"b2\" lang=\"fr\"><title>  Le   Livre </title><price>20</price></book>"
                + "<book id=\"b3\" lang=\"en\"><title>Data</title><price>5</price><tag>c</tag></book>"
                + "<!-- end --><?pi x?>"
                + "</catalog>";
        Document doc = parse(books, false);
        XPath xp = XPathFactory.newInstance().newXPath();
        String[] exprs = {
            "/catalog/book", "//book[@lang='en']", "//book[price > 10]/title", "//book[1]/@id", "//book[last()]/@id",
            "//book[position() < 3]/@id", "count(//book)", "count(//tag)", "sum(//price)", "string(//book[2]/title)",
            "normalize-space(//book[2]/title)", "concat(//book[1]/@id, '-', //book[3]/@id)",
            "contains(//book[1]/title, 'Basic')", "starts-with(//book[3]/title, 'Da')", "string-length('hello')",
            "substring('12345', 2, 3)", "substring('12345', 1.5, 2.6)", "substring('12345', 0, 3)",
            "substring-before('2024-01-02', '-')", "substring-after('2024-01-02', '-')",
            "translate('bar', 'abc', 'ABC')", "translate('--aaa--', 'abc-', 'ABC')", "name(/*)", "local-name(//book)",
            "not(//book[@id='zz'])", "boolean(//book)", "number('  42 ')", "number('abc')", "1 div 0", "-1 div 0",
            "0 div 0", "7 mod 3", "2 + 3 * 4", "(2 + 3) * 4", "1 div 3", "0.1 + 0.2", "-(3)", "10 - -2", "round(2.5)",
            "round(-2.5)", "floor(-1.5)", "ceiling(1.2)", "//book[tag='b']/@id", "//tag[. = 'c']/../@id",
            "//book[@id='b3']/preceding-sibling::book/@id", "//book[@id='b1']/following-sibling::book[1]/@id",
            "//title[ancestor::book/@id='b2']", "//price[. = 20]", "//price[. >= 10]", "//book/price[. < '6']",
            "//book[@lang='en'] | //book[@id='b2']", "//comment()", "//processing-instruction()",
            "//processing-instruction('pi')", "//text()[. = 'a']", "//book/node()", "//book[2]/*",
            "count(//book[1]/descendant::*)", "count(//book[1]/descendant-or-self::node())",
            "count(//title/ancestor-or-self::*)", "count(//book[3]/preceding::*)", "count(//book[1]/following::*)",
            "//book[1]/attribute::*", "string(//book[1]/self::book/@id)", "//book[@id='b2']/child::title",
            "//*[@lang][2]/@id", "//book[tag][last()]/@id", "true() and false()", "true() or false()",
            "1 = 1.0", "'a' != 'b'", "//book/@id = 'b2'", "//book/@id != 'b2'", "//price > 15", "//price < 6",
            "count(//book[@lang='en' and price < 10])", ".//title", "string(.)", "string(1000000)", "string(-0.5)",
            "string(1 = 1)", "lang('en')", "id('b1')", "//book[number(price) = 20]/title",
        };
        for (String e : exprs) {
            evalAll(xp, e, doc);
        }
        // relative to a context node
        Node book2 = (Node) xp.evaluate("//book[2]", doc, XPathConstants.NODE);
        System.out.println("ctx title=" + esc(xp.evaluate("title", book2)) + " parent=" + xp.evaluate("name(..)", book2)
                + " pos=" + xp.evaluate("count(preceding-sibling::*)", book2));
        // compiled expression reuse
        XPathExpression compiled = xp.compile("price * 2");
        NodeList all = (NodeList) xp.evaluate("//book", doc, XPathConstants.NODESET);
        for (int i = 0; i < all.getLength(); i++) {
            System.out.println("compiled " + i + "=" + compiled.evaluate(all.item(i), XPathConstants.NUMBER) + " str="
                    + compiled.evaluate(all.item(i)));
        }
        // namespaces
        Document nsDoc = parse(DOC, true);
        xp.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                if (prefix.equals("s")) {
                    return "urn:shop";
                }
                if (prefix.equals("pr")) {
                    return "urn:price";
                }
                return XMLConstants.NULL_NS_URI;
            }

            public String getPrefix(String uri) {
                return null;
            }

            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        evalAll(xp, "//s:item/@code", nsDoc);
        evalAll(xp, "//s:item[@pr:currency='USD']/s:title", nsDoc);
        evalAll(xp, "sum(//pr:amount)", nsDoc);
        evalAll(xp, "//pr:*", nsDoc);
        evalAll(xp, "count(//item)", nsDoc);
        evalAll(xp, "namespace-uri(/*)", nsDoc);
        evalAll(xp, "name(//s:item[1]/@pr:currency)", nsDoc);
        evalAll(xp, "local-name(//s:item[1]/@pr:currency)", nsDoc);
        evalAll(xp, "count(/s:shop/@*)", nsDoc);
        evalAll(xp, "//s:note", nsDoc);
        evalAll(xp, "/s:shop/text()[normalize-space()]", nsDoc);
        // variables and functions
        XPath vx = XPathFactory.newInstance().newXPath();
        vx.setXPathVariableResolver(new XPathVariableResolver() {
            public Object resolveVariable(QName name) {
                if (name.getLocalPart().equals("min")) {
                    return 10;
                }
                return "b3";
            }
        });
        vx.setXPathFunctionResolver(new XPathFunctionResolver() {
            public XPathFunction resolveFunction(QName name, int arity) {
                return new XPathFunction() {
                    public Object evaluate(List<?> args) {
                        Object a = args.get(0);
                        String s = a instanceof NodeList ? ((NodeList) a).item(0).getTextContent() : String.valueOf(a);
                        return s.toUpperCase();
                    }
                };
            }
        });
        vx.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return prefix.equals("f") ? "urn:fn" : XMLConstants.NULL_NS_URI;
            }

            public String getPrefix(String uri) {
                return null;
            }

            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        System.out.println("var=" + vx.evaluate("count(//book[price >= $min])", doc) + " var2="
                + vx.evaluate("//book[@id=$which]/title", doc));
        System.out.println("fn=" + vx.evaluate("f:upper(//book[1]/title)", doc));
        // input source
        System.out.println("inputsource=" + xp.evaluate("count(//x)", new InputSource(new StringReader("<r><x/><x/></r>"))));
        // errors
        String[] bad = {"//[", "foo(", "unknown-fn()", "1 +", "//book[", "count()", "@", "///a"};
        for (String e : bad) {
            try {
                xp.compile(e);
                System.out.println("compiled bad " + e);
            } catch (XPathExpressionException ex) {
                System.out.println("bad " + e + ": XPathExpressionException");
            }
        }
        try {
            xp.evaluate("count(//book)", doc, XPathConstants.NODESET);
            System.out.println("number as nodeset accepted");
        } catch (XPathExpressionException ex) {
            System.out.println("number as nodeset: XPathExpressionException");
        }
        try {
            XPathFactory.newInstance("urn:unknown-model");
            System.out.println("unknown model accepted");
        } catch (Exception ex) {
            System.out.println("unknown model: " + ex.getClass().getSimpleName());
        }
        System.out.println("QName " + XPathConstants.NODESET + " " + new QName("urn:x", "l", "p").getPrefix() + " "
                + QName.valueOf("{urn:y}z").getNamespaceURI() + " " + new QName("a").equals(new QName("", "a")));
    }

    static void evalAll(XPath xp, String expr, Object ctx) {
        StringBuilder sb = new StringBuilder("xp " + expr + " =>");
        try {
            sb.append(" str=[").append(esc(xp.evaluate(expr, ctx))).append(']');
            sb.append(" num=").append(xp.evaluate(expr, ctx, XPathConstants.NUMBER));
            sb.append(" bool=").append(xp.evaluate(expr, ctx, XPathConstants.BOOLEAN));
            try {
                NodeList nl = (NodeList) xp.evaluate(expr, ctx, XPathConstants.NODESET);
                sb.append(" nodes=").append(nl.getLength()).append('{');
                for (int i = 0; i < nl.getLength(); i++) {
                    Node n = nl.item(i);
                    sb.append(i == 0 ? "" : ",").append(n.getNodeName());
                    if (n.getNodeType() != Node.ELEMENT_NODE) {
                        sb.append('=').append(esc(n.getNodeValue()));
                    }
                }
                sb.append('}');
                Node first = (Node) xp.evaluate(expr, ctx, XPathConstants.NODE);
                sb.append(" node=").append(first == null ? "null" : first.getNodeName());
            } catch (XPathExpressionException e) {
                sb.append(" (not a node-set)");
            }
        } catch (XPathExpressionException e) {
            sb.append(" XPathExpressionException");
        }
        System.out.println(sb);
    }

    static void encodings() throws Exception {
        section("encodings");
        String text = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><r a=\"\u00e9\">caf\u00e9 \u2603 \ud83d\ude00</r>";
        Document utf16 = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(text.getBytes("UTF-16")));
        System.out.println("utf16 text=" + utf16.getDocumentElement().getTextContent().length() + " attr="
                + (int) utf16.getDocumentElement().getAttribute("a").charAt(0) + " xmlEncoding=" + utf16.getXmlEncoding());
        byte[] le = "<r>little</r>".getBytes("UTF-16LE");
        byte[] withBom = new byte[le.length + 2];
        withBom[0] = (byte) 0xff;
        withBom[1] = (byte) 0xfe;
        System.arraycopy(le, 0, withBom, 2, le.length);
        System.out.println("utf16le bom=" + DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(withBom)).getDocumentElement().getTextContent());
        String latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r>caf\u00e9 \u00ff</r>";
        Document latin1 = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(latin.getBytes("ISO-8859-1")));
        String lt = latin1.getDocumentElement().getTextContent();
        System.out.println("latin1 chars=" + (int) lt.charAt(3) + "," + (int) lt.charAt(5) + " xmlEncoding="
                + latin1.getXmlEncoding());
        byte[] u8 = "<r>bom\u00e9</r>".getBytes("UTF-8");
        byte[] u8bom = new byte[u8.length + 3];
        u8bom[0] = (byte) 0xef;
        u8bom[1] = (byte) 0xbb;
        u8bom[2] = (byte) 0xbf;
        System.arraycopy(u8, 0, u8bom, 3, u8.length);
        System.out.println("utf8 bom=" + DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(u8bom)).getDocumentElement().getTextContent().length());
        Document crlf = parse("<r a=\"x\r\ny\">line1\r\nline2\rline3</r>", false);
        System.out.println("crlf text=" + esc(crlf.getDocumentElement().getTextContent()) + " attr="
                + esc(crlf.getDocumentElement().getAttribute("a")));
        InputSource is = new InputSource(new ByteArrayInputStream("<r>\u00e9</r>".getBytes("ISO-8859-1")));
        is.setEncoding("ISO-8859-1");
        System.out.println("explicit encoding=" + (int) DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(is).getDocumentElement().getTextContent().charAt(0));
    }
}
