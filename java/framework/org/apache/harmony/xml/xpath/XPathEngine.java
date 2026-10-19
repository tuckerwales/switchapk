package org.apache.harmony.xml.xpath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;
import org.apache.harmony.xml.dom.NodeListImpl;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An XPath 1.0 evaluator over DOM nodes. Values are Double, String, Boolean or a node-set (a
 * {@code List<Node>} in document order). The namespace axis is not supported (it is always empty).
 */
final class XPathEngine {
    private XPathEngine() {
    }

    static final int AXIS_CHILD = 0, AXIS_DESCENDANT = 1, AXIS_PARENT = 2, AXIS_ANCESTOR = 3,
            AXIS_FOLLOWING_SIBLING = 4, AXIS_PRECEDING_SIBLING = 5, AXIS_FOLLOWING = 6, AXIS_PRECEDING = 7,
            AXIS_ATTRIBUTE = 8, AXIS_NAMESPACE = 9, AXIS_SELF = 10, AXIS_DESCENDANT_OR_SELF = 11,
            AXIS_ANCESTOR_OR_SELF = 12;

    private static final String[] AXIS_NAMES = {"child", "descendant", "parent", "ancestor", "following-sibling",
        "preceding-sibling", "following", "preceding", "attribute", "namespace", "self", "descendant-or-self",
        "ancestor-or-self"};

    static final int TEST_NAME = 0, TEST_NS_ANY = 1, TEST_ANY_NAME = 2, TEST_NODE = 3, TEST_TEXT = 4,
            TEST_COMMENT = 5, TEST_PI = 6;

    static int axisByName(String name) {
        for (int i = 0; i < AXIS_NAMES.length; i++) {
            if (AXIS_NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    static boolean isReverse(int axis) {
        return axis == AXIS_ANCESTOR || axis == AXIS_ANCESTOR_OR_SELF || axis == AXIS_PRECEDING
                || axis == AXIS_PRECEDING_SIBLING;
    }

    /** Evaluation context. */
    static final class Context {
        final Node node;
        final int position;
        final int size;
        final Env env;

        Context(Node node, int position, int size, Env env) {
            this.node = node;
            this.position = position;
            this.size = size;
            this.env = env;
        }
    }

    static final class Env {
        final XPathVariableResolver variables;
        final XPathFunctionResolver functions;

        Env(XPathVariableResolver variables, XPathFunctionResolver functions) {
            this.variables = variables;
            this.functions = functions;
        }
    }

    abstract static class Expr {
        abstract Object eval(Context c) throws XPathExpressionException;
    }

    // ---- conversions ----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<Node> asNodeSet(Object v) {
        return (List<Node>) v;
    }

    static boolean isNodeSet(Object v) {
        return v instanceof List;
    }

    static String toStringValue(Object v) {
        if (v instanceof String) {
            return (String) v;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? "true" : "false";
        }
        if (v instanceof Double) {
            return numberToString((Double) v);
        }
        List<Node> nodes = asNodeSet(v);
        return nodes.isEmpty() ? "" : stringValue(nodes.get(0));
    }

    static double toNumber(Object v) {
        if (v instanceof Double) {
            return (Double) v;
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1 : 0;
        }
        return stringToNumber(toStringValue(v));
    }

    static boolean toBoolean(Object v) {
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Double) {
            double d = (Double) v;
            return d != 0 && !Double.isNaN(d);
        }
        if (v instanceof String) {
            return !((String) v).isEmpty();
        }
        return !asNodeSet(v).isEmpty();
    }

    static String numberToString(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "Infinity" : "-Infinity";
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e18) {
            return Long.toString((long) d);
        }
        String s = new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
        return s;
    }

    static double stringToNumber(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(s.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return Double.NaN;
        }
        int i = start;
        if (s.charAt(i) == '-') {
            i++;
        }
        boolean digits = false;
        while (i < end && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
            digits = true;
        }
        if (i < end && s.charAt(i) == '.') {
            i++;
            while (i < end && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
                digits = true;
            }
        }
        if (!digits || i != end) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s.substring(start, end));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    static boolean isText(Node n) {
        if (n == null) {
            return false;
        }
        short t = n.getNodeType();
        return t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE;
    }

    static String stringValue(Node n) {
        switch (n.getNodeType()) {
            case Node.DOCUMENT_NODE: {
                Element root = ((Document) n).getDocumentElement();
                return root == null ? "" : root.getTextContent();
            }
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE: {
                // Adjacent text nodes form a single XPath text node.
                StringBuilder sb = new StringBuilder();
                for (Node t = n; isText(t); t = t.getNextSibling()) {
                    sb.append(t.getNodeValue());
                }
                return sb.toString();
            }
            default: {
                String s = n.getTextContent();
                return s == null ? "" : s;
            }
        }
    }

    // ---- node-set helpers -----------------------------------------------------------------

    static final Comparator<Node> DOCUMENT_ORDER = new Comparator<Node>() {
        public int compare(Node a, Node b) {
            if (a == b) {
                return 0;
            }
            return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) != 0 ? -1 : 1;
        }
    };

    static List<Node> sortUnique(List<Node> nodes) {
        IdentityHashMap<Node, Boolean> seen = new IdentityHashMap<Node, Boolean>();
        List<Node> out = new ArrayList<Node>(nodes.size());
        for (Node n : nodes) {
            if (seen.put(n, Boolean.TRUE) == null) {
                out.add(n);
            }
        }
        if (out.size() < 2) {
            return out;
        }
        if (out.size() < 16) {
            Collections.sort(out, DOCUMENT_ORDER);
            return out;
        }
        // Number every node of the tree once, then sort by that number.
        final IdentityHashMap<Node, Integer> order = new IdentityHashMap<Node, Integer>();
        Node r = root(out.get(0));
        number(r, order);
        for (Node n : out) {
            if (!order.containsKey(n)) {
                Collections.sort(out, DOCUMENT_ORDER);
                return out;
            }
        }
        Collections.sort(out, new Comparator<Node>() {
            public int compare(Node a, Node b) {
                return order.get(a) - order.get(b);
            }
        });
        return out;
    }

    private static void number(Node n, IdentityHashMap<Node, Integer> order) {
        order.put(n, order.size());
        NamedNodeMap attrs = n.getNodeType() == Node.ELEMENT_NODE ? n.getAttributes() : null;
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                order.put(attrs.item(i), order.size());
            }
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
            number(c, order);
        }
    }

    static Node root(Node n) {
        while (true) {
            Node p = n.getNodeType() == Node.ATTRIBUTE_NODE ? ((Attr) n).getOwnerElement() : n.getParentNode();
            if (p == null) {
                return n;
            }
            n = p;
        }
    }

    static boolean isNamespaceDecl(Node attr) {
        String name = attr.getNodeName();
        return name.equals("xmlns") || name.startsWith("xmlns:");
    }

    /** Children visible to XPath: no doctype, and a run of adjacent text nodes counts once. */
    static void addChildren(Node n, List<Node> out) {
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
            short t = c.getNodeType();
            if (t == Node.DOCUMENT_TYPE_NODE) {
                continue;
            }
            if (isText(c) && isText(c.getPreviousSibling())) {
                continue;
            }
            if (t == Node.ENTITY_REFERENCE_NODE) {
                addChildren(c, out);
                continue;
            }
            out.add(c);
        }
    }

    static void addDescendants(Node n, List<Node> out) {
        List<Node> kids = new ArrayList<Node>();
        addChildren(n, kids);
        for (Node c : kids) {
            out.add(c);
            addDescendants(c, out);
        }
    }

    static Node parentOf(Node n) {
        if (n.getNodeType() == Node.ATTRIBUTE_NODE) {
            return ((Attr) n).getOwnerElement();
        }
        Node p = n.getParentNode();
        while (p != null && p.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
            p = p.getParentNode();
        }
        return p;
    }

    static List<Node> siblings(Node n) {
        List<Node> out = new ArrayList<Node>();
        Node p = parentOf(n);
        if (p != null) {
            addChildren(p, out);
        }
        return out;
    }

    static List<Node> axis(Node n, int axis) {
        List<Node> out = new ArrayList<Node>();
        boolean isAttr = n.getNodeType() == Node.ATTRIBUTE_NODE;
        switch (axis) {
            case AXIS_CHILD:
                if (!isAttr) {
                    addChildren(n, out);
                }
                break;
            case AXIS_DESCENDANT_OR_SELF:
                out.add(n);
                // fall through
            case AXIS_DESCENDANT:
                if (!isAttr) {
                    addDescendants(n, out);
                }
                break;
            case AXIS_PARENT: {
                Node p = parentOf(n);
                if (p != null) {
                    out.add(p);
                }
                break;
            }
            case AXIS_ANCESTOR_OR_SELF:
                out.add(n);
                // fall through
            case AXIS_ANCESTOR:
                for (Node p = parentOf(n); p != null; p = parentOf(p)) {
                    out.add(p);
                }
                break;
            case AXIS_FOLLOWING_SIBLING:
            case AXIS_PRECEDING_SIBLING: {
                if (isAttr) {
                    break;
                }
                List<Node> sibs = siblings(n);
                int i = indexOfIdentity(sibs, n);
                if (i < 0) {
                    break;
                }
                if (axis == AXIS_FOLLOWING_SIBLING) {
                    out.addAll(sibs.subList(i + 1, sibs.size()));
                } else {
                    for (int j = i - 1; j >= 0; j--) {
                        out.add(sibs.get(j));
                    }
                }
                break;
            }
            case AXIS_FOLLOWING: {
                Node start = n;
                if (isAttr) {
                    start = parentOf(n);
                    if (start == null) {
                        break;
                    }
                    addDescendants(start, out);
                }
                for (Node m = start; m != null; m = parentOf(m)) {
                    List<Node> sibs = siblings(m);
                    int i = indexOfIdentity(sibs, m);
                    for (int j = i + 1; j > 0 && j < sibs.size(); j++) {
                        out.add(sibs.get(j));
                        addDescendants(sibs.get(j), out);
                    }
                }
                break;
            }
            case AXIS_PRECEDING: {
                Node target = isAttr ? parentOf(n) : n;
                if (target == null) {
                    break;
                }
                IdentityHashMap<Node, Boolean> ancestors = new IdentityHashMap<Node, Boolean>();
                for (Node p = parentOf(target); p != null; p = parentOf(p)) {
                    ancestors.put(p, Boolean.TRUE);
                }
                List<Node> all = new ArrayList<Node>();
                Node r = root(target);
                all.add(r);
                addDescendants(r, all);
                for (Node m : all) {
                    if (m == target) {
                        break;
                    }
                    if (!ancestors.containsKey(m)) {
                        out.add(m);
                    }
                }
                Collections.reverse(out);
                break;
            }
            case AXIS_ATTRIBUTE: {
                NamedNodeMap attrs = n.getNodeType() == Node.ELEMENT_NODE ? n.getAttributes() : null;
                if (attrs != null) {
                    for (int i = 0; i < attrs.getLength(); i++) {
                        Node a = attrs.item(i);
                        if (!isNamespaceDecl(a)) {
                            out.add(a);
                        }
                    }
                }
                break;
            }
            case AXIS_SELF:
                out.add(n);
                break;
            default:
                break;
        }
        return out;
    }

    private static int indexOfIdentity(List<Node> list, Node n) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == n) {
                return i;
            }
        }
        // n may be a text node hidden behind an earlier adjacent text node
        for (int i = 0; i < list.size(); i++) {
            if (isText(n) && isText(list.get(i))) {
                for (Node t = list.get(i); isText(t); t = t.getNextSibling()) {
                    if (t == n) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    static String localNameOf(Node n) {
        String local = n.getLocalName();
        if (local != null) {
            return local;
        }
        String name = n.getNodeName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    static String namespaceOf(Node n) {
        String ns = n.getNamespaceURI();
        return ns == null ? "" : ns;
    }

    // ---- expressions ----------------------------------------------------------------------

    static final class Step {
        final int axis;
        final int test;
        final String namespace;
        final String local;
        final List<Expr> predicates;

        Step(int axis, int test, String namespace, String local, List<Expr> predicates) {
            this.axis = axis;
            this.test = test;
            this.namespace = namespace;
            this.local = local;
            this.predicates = predicates;
        }

        static Step descendantOrSelf() {
            return new Step(AXIS_DESCENDANT_OR_SELF, TEST_NODE, null, null, new ArrayList<Expr>());
        }

        boolean matches(Node n) {
            short type = n.getNodeType();
            switch (test) {
                case TEST_NODE:
                    return true;
                case TEST_TEXT:
                    return type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE;
                case TEST_COMMENT:
                    return type == Node.COMMENT_NODE;
                case TEST_PI:
                    return type == Node.PROCESSING_INSTRUCTION_NODE && (local == null || local.equals(n.getNodeName()));
                default:
                    break;
            }
            short principal = axis == AXIS_ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE;
            if (type != principal) {
                return false;
            }
            if (test == TEST_ANY_NAME) {
                return true;
            }
            if (!namespace.equals(namespaceOf(n))) {
                return false;
            }
            return test == TEST_NS_ANY || local.equals(localNameOf(n));
        }

        List<Node> apply(Node context, Env env) throws XPathExpressionException {
            List<Node> nodes = new ArrayList<Node>();
            for (Node n : axis(context, axis)) {
                if (matches(n)) {
                    nodes.add(n);
                }
            }
            for (Expr predicate : predicates) {
                nodes = filter(nodes, predicate, env);
            }
            return nodes;
        }
    }

    /** Applies a predicate to nodes listed in axis order (positions count in that order). */
    static List<Node> filter(List<Node> nodes, Expr predicate, Env env) throws XPathExpressionException {
        List<Node> out = new ArrayList<Node>();
        int size = nodes.size();
        for (int i = 0; i < size; i++) {
            Object v = predicate.eval(new Context(nodes.get(i), i + 1, size, env));
            boolean keep = v instanceof Double ? ((Double) v) == i + 1 : toBoolean(v);
            if (keep) {
                out.add(nodes.get(i));
            }
        }
        return out;
    }

    static final class Path extends Expr {
        final Expr filter;
        final boolean absolute;
        final List<Step> steps;

        Path(Expr filter, boolean absolute, List<Step> steps) {
            this.filter = filter;
            this.absolute = absolute;
            // "//x" without predicates on x is the same as descendant::x, which needs no sorting.
            List<Step> optimized = new ArrayList<Step>();
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (s.axis == AXIS_DESCENDANT_OR_SELF && s.test == TEST_NODE && s.predicates.isEmpty()
                        && i + 1 < steps.size()) {
                    Step t = steps.get(i + 1);
                    if (t.axis == AXIS_CHILD && t.predicates.isEmpty()) {
                        optimized.add(new Step(AXIS_DESCENDANT, t.test, t.namespace, t.local, t.predicates));
                        i++;
                        continue;
                    }
                }
                optimized.add(s);
            }
            this.steps = optimized;
        }

        Object eval(Context c) throws XPathExpressionException {
            List<Node> current;
            if (filter != null) {
                Object v = filter.eval(c);
                if (!isNodeSet(v)) {
                    throw new XPathExpressionException("Can not convert #" + typeName(v) + " to a NodeList!");
                }
                current = asNodeSet(v);
            } else {
                current = new ArrayList<Node>();
                current.add(absolute ? root(c.node) : c.node);
            }
            for (Step step : steps) {
                List<Node> next = new ArrayList<Node>();
                for (Node n : current) {
                    List<Node> r = step.apply(n, c.env);
                    if (isReverse(step.axis)) {
                        Collections.reverse(r);
                    }
                    next.addAll(r);
                }
                current = current.size() > 1 ? sortUnique(next) : next;
            }
            return current;
        }
    }

    static final class Filter extends Expr {
        final Expr primary;
        final List<Expr> predicates;

        Filter(Expr primary, List<Expr> predicates) {
            this.primary = primary;
            this.predicates = predicates;
        }

        Object eval(Context c) throws XPathExpressionException {
            Object v = primary.eval(c);
            if (!isNodeSet(v)) {
                throw new XPathExpressionException("Can not convert #" + typeName(v) + " to a NodeList!");
            }
            List<Node> nodes = asNodeSet(v);
            for (Expr p : predicates) {
                nodes = filter(nodes, p, c.env);
            }
            return nodes;
        }
    }

    static String typeName(Object v) {
        if (v instanceof Double) {
            return "NUMBER";
        }
        if (v instanceof String) {
            return "STRING";
        }
        if (v instanceof Boolean) {
            return "BOOLEAN";
        }
        return "NODESET";
    }

    static final class Union extends Expr {
        final Expr left;
        final Expr right;

        Union(Expr left, Expr right) {
            this.left = left;
            this.right = right;
        }

        Object eval(Context c) throws XPathExpressionException {
            Object a = left.eval(c);
            Object b = right.eval(c);
            if (!isNodeSet(a) || !isNodeSet(b)) {
                throw new XPathExpressionException("Can not convert #" + typeName(isNodeSet(a) ? b : a)
                        + " to a NodeList!");
            }
            List<Node> all = new ArrayList<Node>(asNodeSet(a));
            all.addAll(asNodeSet(b));
            return sortUnique(all);
        }
    }

    static final class Negate extends Expr {
        final Expr operand;

        Negate(Expr operand) {
            this.operand = operand;
        }

        Object eval(Context c) throws XPathExpressionException {
            return -toNumber(operand.eval(c));
        }
    }

    static final class Literal extends Expr {
        final Object value;

        Literal(Object value) {
            this.value = value;
        }

        Object eval(Context c) {
            return value;
        }
    }

    static final class Variable extends Expr {
        final String namespace;
        final String local;

        Variable(String namespace, String local) {
            this.namespace = namespace;
            this.local = local;
        }

        Object eval(Context c) throws XPathExpressionException {
            if (c.env.variables == null) {
                throw new XPathExpressionException("Could not resolve variable: " + local);
            }
            Object v = c.env.variables.resolveVariable(new QName(namespace, local));
            if (v == null) {
                throw new XPathExpressionException("Variable " + local + " resolved to null");
            }
            return fromJava(v);
        }
    }

    /** Converts a Java object returned by a resolver into an XPath value. */
    static Object fromJava(Object v) {
        if (v instanceof Node) {
            List<Node> list = new ArrayList<Node>();
            list.add((Node) v);
            return list;
        }
        if (v instanceof NodeList) {
            NodeList nl = (NodeList) v;
            List<Node> list = new ArrayList<Node>();
            for (int i = 0; i < nl.getLength(); i++) {
                list.add(nl.item(i));
            }
            return sortUnique(list);
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof Boolean || v instanceof String) {
            return v;
        }
        return String.valueOf(v);
    }

    static final class Binary extends Expr {
        final String op;
        final Expr left;
        final Expr right;

        Binary(String op, Expr left, Expr right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        Object eval(Context c) throws XPathExpressionException {
            if (op.equals("or")) {
                return toBoolean(left.eval(c)) || toBoolean(right.eval(c));
            }
            if (op.equals("and")) {
                return toBoolean(left.eval(c)) && toBoolean(right.eval(c));
            }
            Object a = left.eval(c);
            Object b = right.eval(c);
            switch (op) {
                case "+":
                    return toNumber(a) + toNumber(b);
                case "-":
                    return toNumber(a) - toNumber(b);
                case "*":
                    return toNumber(a) * toNumber(b);
                case "div":
                    return toNumber(a) / toNumber(b);
                case "mod":
                    return toNumber(a) % toNumber(b);
                default:
                    return compare(op, a, b);
            }
        }
    }

    static boolean compare(String op, Object a, Object b) {
        boolean aSet = isNodeSet(a);
        boolean bSet = isNodeSet(b);
        if (aSet && bSet) {
            for (Node x : asNodeSet(a)) {
                String sx = stringValue(x);
                for (Node y : asNodeSet(b)) {
                    if (compareAtoms(op, sx, stringValue(y))) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (aSet || bSet) {
            Object set = aSet ? a : b;
            Object other = aSet ? b : a;
            String effectiveOp = aSet ? op : flip(op);
            if (other instanceof Boolean) {
                return compareAtoms(effectiveOp, toBoolean(set), other);
            }
            for (Node x : asNodeSet(set)) {
                Object atom = other instanceof Double ? (Object) stringToNumber(stringValue(x)) : stringValue(x);
                if (compareAtoms(effectiveOp, atom, other)) {
                    return true;
                }
            }
            return false;
        }
        return compareAtoms(op, a, b);
    }

    private static String flip(String op) {
        switch (op) {
            case "<":
                return ">";
            case ">":
                return "<";
            case "<=":
                return ">=";
            case ">=":
                return "<=";
            default:
                return op;
        }
    }

    private static boolean compareAtoms(String op, Object a, Object b) {
        if (op.equals("=") || op.equals("!=")) {
            boolean eq;
            if (a instanceof Boolean || b instanceof Boolean) {
                eq = toBoolean(a) == toBoolean(b);
            } else if (a instanceof Double || b instanceof Double) {
                eq = toNumber(a) == toNumber(b);
            } else {
                eq = toStringValue(a).equals(toStringValue(b));
            }
            return op.equals("=") ? eq : !eq;
        }
        double x = toNumber(a);
        double y = toNumber(b);
        switch (op) {
            case "<":
                return x < y;
            case "<=":
                return x <= y;
            case ">":
                return x > y;
            default:
                return x >= y;
        }
    }

    // ---- functions ------------------------------------------------------------------------

    static final class ExtensionCall extends Expr {
        final String namespace;
        final String local;
        final List<Expr> args;

        ExtensionCall(String namespace, String local, List<Expr> args) {
            this.namespace = namespace;
            this.local = local;
            this.args = args;
        }

        Object eval(Context c) throws XPathExpressionException {
            XPathFunction f = c.env.functions == null ? null
                    : c.env.functions.resolveFunction(new QName(namespace, local), args.size());
            if (f == null) {
                throw new XPathExpressionException("Could not find function: {" + namespace + "}" + local);
            }
            List<Object> values = new ArrayList<Object>();
            for (Expr e : args) {
                Object v = e.eval(c);
                values.add(isNodeSet(v) ? new NodeListImpl(asNodeSet(v)) : v);
            }
            try {
                return fromJava(f.evaluate(values));
            } catch (XPathFunctionException e) {
                throw new XPathExpressionException(e);
            }
        }
    }

    static final class Call extends Expr {
        final String name;
        final List<Expr> args;

        private Call(String name, List<Expr> args) {
            this.name = name;
            this.args = args;
        }

        static Call create(String name, List<Expr> args, XPathParser parser) throws XPathExpressionException {
            int n = args.size();
            int min;
            int max;
            switch (name) {
                case "last":
                case "position":
                case "true":
                case "false":
                    min = 0;
                    max = 0;
                    break;
                case "count":
                case "id":
                case "not":
                case "boolean":
                case "sum":
                case "floor":
                case "ceiling":
                case "round":
                case "lang":
                    min = 1;
                    max = 1;
                    break;
                case "local-name":
                case "namespace-uri":
                case "name":
                case "string":
                case "string-length":
                case "normalize-space":
                case "number":
                    min = 0;
                    max = 1;
                    break;
                case "starts-with":
                case "contains":
                case "substring-before":
                case "substring-after":
                    min = 2;
                    max = 2;
                    break;
                case "substring":
                    min = 2;
                    max = 3;
                    break;
                case "translate":
                    min = 3;
                    max = 3;
                    break;
                case "concat":
                    min = 2;
                    max = Integer.MAX_VALUE;
                    break;
                default:
                    throw parser.errorFor("Could not find function: " + name);
            }
            if (n < min || n > max) {
                throw parser.errorFor("FuncName " + name + " has the wrong number of arguments");
            }
            return new Call(name, args);
        }

        private Object arg(Context c, int i) throws XPathExpressionException {
            return args.get(i).eval(c);
        }

        private String str(Context c, int i) throws XPathExpressionException {
            return toStringValue(arg(c, i));
        }

        private List<Node> nodes(Context c, int i) throws XPathExpressionException {
            Object v = arg(c, i);
            if (!isNodeSet(v)) {
                throw new XPathExpressionException("Can not convert #" + typeName(v) + " to a NodeList!");
            }
            return asNodeSet(v);
        }

        /** The first node of the optional node-set argument, or the context node. */
        private Node nodeArg(Context c) throws XPathExpressionException {
            if (args.isEmpty()) {
                return c.node;
            }
            List<Node> ns = nodes(c, 0);
            return ns.isEmpty() ? null : ns.get(0);
        }

        Object eval(Context c) throws XPathExpressionException {
            switch (name) {
                case "last":
                    return (double) c.size;
                case "position":
                    return (double) c.position;
                case "count":
                    return (double) nodes(c, 0).size();
                case "id": {
                    Object v = arg(c, 0);
                    List<String> ids = new ArrayList<String>();
                    if (isNodeSet(v)) {
                        for (Node n : asNodeSet(v)) {
                            splitTokens(stringValue(n), ids);
                        }
                    } else {
                        splitTokens(toStringValue(v), ids);
                    }
                    Node r = root(c.node);
                    List<Node> out = new ArrayList<Node>();
                    if (r instanceof Document) {
                        for (String id : ids) {
                            Element e = ((Document) r).getElementById(id);
                            if (e != null) {
                                out.add(e);
                            }
                        }
                    }
                    return sortUnique(out);
                }
                case "local-name": {
                    Node n = nodeArg(c);
                    if (n == null) {
                        return "";
                    }
                    short t = n.getNodeType();
                    if (t == Node.ELEMENT_NODE || t == Node.ATTRIBUTE_NODE) {
                        return localNameOf(n);
                    }
                    return t == Node.PROCESSING_INSTRUCTION_NODE ? n.getNodeName() : "";
                }
                case "namespace-uri": {
                    Node n = nodeArg(c);
                    if (n == null) {
                        return "";
                    }
                    short t = n.getNodeType();
                    return t == Node.ELEMENT_NODE || t == Node.ATTRIBUTE_NODE ? namespaceOf(n) : "";
                }
                case "name": {
                    Node n = nodeArg(c);
                    if (n == null) {
                        return "";
                    }
                    short t = n.getNodeType();
                    if (t == Node.ELEMENT_NODE || t == Node.ATTRIBUTE_NODE || t == Node.PROCESSING_INSTRUCTION_NODE) {
                        return n.getNodeName();
                    }
                    return "";
                }
                case "string":
                    return args.isEmpty() ? stringValue(c.node) : str(c, 0);
                case "concat": {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < args.size(); i++) {
                        sb.append(str(c, i));
                    }
                    return sb.toString();
                }
                case "starts-with":
                    return str(c, 0).startsWith(str(c, 1));
                case "contains":
                    return str(c, 0).contains(str(c, 1));
                case "substring-before": {
                    String s = str(c, 0);
                    int i = s.indexOf(str(c, 1));
                    return i < 0 ? "" : s.substring(0, i);
                }
                case "substring-after": {
                    String s = str(c, 0);
                    String t = str(c, 1);
                    int i = s.indexOf(t);
                    return i < 0 ? "" : s.substring(i + t.length());
                }
                case "substring": {
                    String s = str(c, 0);
                    double start = round(toNumber(arg(c, 1)));
                    double end = args.size() > 2 ? start + round(toNumber(arg(c, 2))) : Double.POSITIVE_INFINITY;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < s.length(); i++) {
                        double p = i + 1;
                        if (p >= start && p < end) {
                            sb.append(s.charAt(i));
                        }
                    }
                    return sb.toString();
                }
                case "string-length":
                    return (double) (args.isEmpty() ? stringValue(c.node) : str(c, 0)).length();
                case "normalize-space": {
                    String s = args.isEmpty() ? stringValue(c.node) : str(c, 0);
                    StringBuilder sb = new StringBuilder();
                    boolean space = false;
                    for (int i = 0; i < s.length(); i++) {
                        char ch = s.charAt(i);
                        if (isSpace(ch)) {
                            space = sb.length() > 0;
                        } else {
                            if (space) {
                                sb.append(' ');
                                space = false;
                            }
                            sb.append(ch);
                        }
                    }
                    return sb.toString();
                }
                case "translate": {
                    String s = str(c, 0);
                    String from = str(c, 1);
                    String to = str(c, 2);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < s.length(); i++) {
                        char ch = s.charAt(i);
                        int k = from.indexOf(ch);
                        if (k < 0) {
                            sb.append(ch);
                        } else if (k < to.length()) {
                            sb.append(to.charAt(k));
                        }
                    }
                    return sb.toString();
                }
                case "boolean":
                    return toBoolean(arg(c, 0));
                case "not":
                    return !toBoolean(arg(c, 0));
                case "true":
                    return Boolean.TRUE;
                case "false":
                    return Boolean.FALSE;
                case "lang": {
                    String lang = str(c, 0).toLowerCase(java.util.Locale.ROOT);
                    for (Node n = c.node; n != null; n = parentOf(n)) {
                        if (n.getNodeType() == Node.ELEMENT_NODE) {
                            Element e = (Element) n;
                            Attr a = e.getAttributeNode("xml:lang");
                            if (a != null) {
                                String v = a.getValue().toLowerCase(java.util.Locale.ROOT);
                                return v.equals(lang) || v.startsWith(lang + "-");
                            }
                        }
                    }
                    return Boolean.FALSE;
                }
                case "number":
                    return args.isEmpty() ? stringToNumber(stringValue(c.node)) : toNumber(arg(c, 0));
                case "sum": {
                    double sum = 0;
                    for (Node n : nodes(c, 0)) {
                        sum += stringToNumber(stringValue(n));
                    }
                    return sum;
                }
                case "floor":
                    return Math.floor(toNumber(arg(c, 0)));
                case "ceiling":
                    return Math.ceil(toNumber(arg(c, 0)));
                case "round":
                    return round(toNumber(arg(c, 0)));
                default:
                    throw new XPathExpressionException("Could not find function: " + name);
            }
        }
    }

    static double round(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return d;
        }
        if (d < 0 && d >= -0.5) {
            return -0.0;
        }
        return Math.floor(d + 0.5);
    }

    private static void splitTokens(String s, List<String> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && isSpace(s.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < n && !isSpace(s.charAt(i))) {
                i++;
            }
            if (i > start) {
                out.add(s.substring(start, i));
            }
        }
    }
}
