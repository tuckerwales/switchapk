package org.apache.harmony.xml.xpath;

import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathExpressionException;

/** Tokenizes and parses an XPath 1.0 expression into an {@link XPathEngine.Expr} tree. */
final class XPathParser {
    private static final int LPAREN = 1, RPAREN = 2, LBRACKET = 3, RBRACKET = 4, DOT = 5, DOTDOT = 6, AT = 7,
            COMMA = 8, DCOLON = 9, NAMETEST = 10, NODETYPE = 11, OPERATOR = 12, FUNCNAME = 13, AXISNAME = 14,
            LITERAL = 15, NUMBER = 16, VARREF = 17, EOF = 18;

    private static final class Token {
        final int type;
        final String text;

        Token(int type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private final String source;
    private final NamespaceContext namespaces;
    private final List<Token> tokens = new ArrayList<Token>();
    private int index;

    private XPathParser(String source, NamespaceContext namespaces) {
        this.source = source;
        this.namespaces = namespaces;
    }

    static XPathEngine.Expr parse(String expression, NamespaceContext namespaces) throws XPathExpressionException {
        if (expression == null) {
            throw new NullPointerException("expression == null");
        }
        XPathParser p = new XPathParser(expression, namespaces);
        p.tokenize();
        XPathEngine.Expr e = p.parseOr();
        if (p.peek().type != EOF) {
            throw p.error("Extra illegal tokens: '" + p.peek().text + "'");
        }
        return e;
    }

    private XPathExpressionException error(String message) {
        return new XPathExpressionException(message + " in expression: " + source);
    }

    // ---- lexer ----------------------------------------------------------------------------

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c > 0x7f;
    }

    private static boolean isNameChar(char c) {
        return isNameStart(c) || Character.isDigit(c) || c == '-' || c == '.' || c == 0xB7;
    }

    /** Whether the previous token makes '*' a multiply operator and a name an operator name. */
    private boolean operatorContext() {
        if (tokens.isEmpty()) {
            return false;
        }
        Token t = tokens.get(tokens.size() - 1);
        switch (t.type) {
            case AT:
            case DCOLON:
            case LPAREN:
            case LBRACKET:
            case COMMA:
            case OPERATOR:
                return false;
            default:
                return true;
        }
    }

    private void tokenize() throws XPathExpressionException {
        String s = source;
        int n = s.length();
        int i = 0;
        while (true) {
            while (i < n && isSpace(s.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            char c = s.charAt(i);
            char c1 = i + 1 < n ? s.charAt(i + 1) : 0;
            switch (c) {
                case '(':
                    tokens.add(new Token(LPAREN, "("));
                    i++;
                    continue;
                case ')':
                    tokens.add(new Token(RPAREN, ")"));
                    i++;
                    continue;
                case '[':
                    tokens.add(new Token(LBRACKET, "["));
                    i++;
                    continue;
                case ']':
                    tokens.add(new Token(RBRACKET, "]"));
                    i++;
                    continue;
                case '@':
                    tokens.add(new Token(AT, "@"));
                    i++;
                    continue;
                case ',':
                    tokens.add(new Token(COMMA, ","));
                    i++;
                    continue;
                case ':':
                    if (c1 == ':') {
                        tokens.add(new Token(DCOLON, "::"));
                        i += 2;
                        continue;
                    }
                    throw error("Unexpected ':'");
                case '.':
                    if (c1 == '.') {
                        tokens.add(new Token(DOTDOT, ".."));
                        i += 2;
                        continue;
                    }
                    if (c1 >= '0' && c1 <= '9') {
                        break;
                    }
                    tokens.add(new Token(DOT, "."));
                    i++;
                    continue;
                case '"':
                case '\'': {
                    int end = s.indexOf(c, i + 1);
                    if (end < 0) {
                        throw error("misquoted literal... expected " + (c == '"' ? "double" : "single") + " quote!");
                    }
                    tokens.add(new Token(LITERAL, s.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
                case '/':
                    if (c1 == '/') {
                        tokens.add(new Token(OPERATOR, "//"));
                        i += 2;
                    } else {
                        tokens.add(new Token(OPERATOR, "/"));
                        i++;
                    }
                    continue;
                case '|':
                case '+':
                case '-':
                case '=':
                    tokens.add(new Token(OPERATOR, String.valueOf(c)));
                    i++;
                    continue;
                case '!':
                    if (c1 == '=') {
                        tokens.add(new Token(OPERATOR, "!="));
                        i += 2;
                        continue;
                    }
                    throw error("Unexpected '!'");
                case '<':
                case '>':
                    if (c1 == '=') {
                        tokens.add(new Token(OPERATOR, c + "="));
                        i += 2;
                    } else {
                        tokens.add(new Token(OPERATOR, String.valueOf(c)));
                        i++;
                    }
                    continue;
                case '*':
                    if (operatorContext()) {
                        tokens.add(new Token(OPERATOR, "*"));
                    } else {
                        tokens.add(new Token(NAMETEST, "*"));
                    }
                    i++;
                    continue;
                case '$': {
                    int start = ++i;
                    i = scanQName(s, i);
                    if (i == start) {
                        throw error("Expected a variable name");
                    }
                    tokens.add(new Token(VARREF, s.substring(start, i)));
                    continue;
                }
                default:
                    break;
            }
            if ((c >= '0' && c <= '9') || c == '.') {
                int start = i;
                while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                    i++;
                }
                if (i < n && s.charAt(i) == '.') {
                    i++;
                    while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                        i++;
                    }
                }
                tokens.add(new Token(NUMBER, s.substring(start, i)));
                continue;
            }
            if (isNameStart(c)) {
                int start = i;
                while (i < n && isNameChar(s.charAt(i))) {
                    i++;
                }
                String ncname = s.substring(start, i);
                if (operatorContext()) {
                    if (ncname.equals("and") || ncname.equals("or") || ncname.equals("mod") || ncname.equals("div")) {
                        tokens.add(new Token(OPERATOR, ncname));
                        continue;
                    }
                    throw error("Expected an operator but found '" + ncname + "'");
                }
                // prefix:* or prefix:local
                if (i + 1 < n && s.charAt(i) == ':' && s.charAt(i + 1) == '*') {
                    tokens.add(new Token(NAMETEST, ncname + ":*"));
                    i += 2;
                    continue;
                }
                String name = ncname;
                if (i + 1 < n && s.charAt(i) == ':' && s.charAt(i + 1) != ':' && isNameStart(s.charAt(i + 1))) {
                    int localStart = i + 1;
                    i = localStart;
                    while (i < n && isNameChar(s.charAt(i))) {
                        i++;
                    }
                    name = ncname + ":" + s.substring(localStart, i);
                }
                int j = i;
                while (j < n && isSpace(s.charAt(j))) {
                    j++;
                }
                if (j + 1 < n && s.charAt(j) == ':' && s.charAt(j + 1) == ':' && name.indexOf(':') < 0) {
                    tokens.add(new Token(AXISNAME, name));
                } else if (j < n && s.charAt(j) == '(') {
                    if (name.equals("comment") || name.equals("text") || name.equals("node")
                            || name.equals("processing-instruction")) {
                        tokens.add(new Token(NODETYPE, name));
                    } else {
                        tokens.add(new Token(FUNCNAME, name));
                    }
                } else {
                    tokens.add(new Token(NAMETEST, name));
                }
                continue;
            }
            throw error("Unexpected character '" + c + "'");
        }
        tokens.add(new Token(EOF, "<end>"));
    }

    private static int scanQName(String s, int i) {
        int n = s.length();
        while (i < n && isNameChar(s.charAt(i))) {
            i++;
        }
        if (i + 1 < n && s.charAt(i) == ':' && isNameStart(s.charAt(i + 1))) {
            i++;
            while (i < n && isNameChar(s.charAt(i))) {
                i++;
            }
        }
        return i;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    // ---- parser ---------------------------------------------------------------------------

    private Token peek() {
        return tokens.get(index);
    }

    private Token next() {
        return tokens.get(index++);
    }

    private boolean isOp(String op) {
        Token t = peek();
        return t.type == OPERATOR && t.text.equals(op);
    }

    private void expect(int type, String what) throws XPathExpressionException {
        if (peek().type != type) {
            throw error("Expected " + what + ", but found: " + peek().text);
        }
        index++;
    }

    private XPathEngine.Expr parseOr() throws XPathExpressionException {
        XPathEngine.Expr e = parseAnd();
        while (isOp("or")) {
            next();
            e = new XPathEngine.Binary("or", e, parseAnd());
        }
        return e;
    }

    private XPathEngine.Expr parseAnd() throws XPathExpressionException {
        XPathEngine.Expr e = parseEquality();
        while (isOp("and")) {
            next();
            e = new XPathEngine.Binary("and", e, parseEquality());
        }
        return e;
    }

    private XPathEngine.Expr parseEquality() throws XPathExpressionException {
        XPathEngine.Expr e = parseRelational();
        while (isOp("=") || isOp("!=")) {
            String op = next().text;
            e = new XPathEngine.Binary(op, e, parseRelational());
        }
        return e;
    }

    private XPathEngine.Expr parseRelational() throws XPathExpressionException {
        XPathEngine.Expr e = parseAdditive();
        while (isOp("<") || isOp("<=") || isOp(">") || isOp(">=")) {
            String op = next().text;
            e = new XPathEngine.Binary(op, e, parseAdditive());
        }
        return e;
    }

    private XPathEngine.Expr parseAdditive() throws XPathExpressionException {
        XPathEngine.Expr e = parseMultiplicative();
        while (isOp("+") || isOp("-")) {
            String op = next().text;
            e = new XPathEngine.Binary(op, e, parseMultiplicative());
        }
        return e;
    }

    private XPathEngine.Expr parseMultiplicative() throws XPathExpressionException {
        XPathEngine.Expr e = parseUnary();
        while (isOp("*") || isOp("div") || isOp("mod")) {
            String op = next().text;
            e = new XPathEngine.Binary(op, e, parseUnary());
        }
        return e;
    }

    private XPathEngine.Expr parseUnary() throws XPathExpressionException {
        if (isOp("-")) {
            next();
            return new XPathEngine.Negate(parseUnary());
        }
        return parseUnion();
    }

    private XPathEngine.Expr parseUnion() throws XPathExpressionException {
        XPathEngine.Expr e = parsePath();
        while (isOp("|")) {
            next();
            e = new XPathEngine.Union(e, parsePath());
        }
        return e;
    }

    private boolean startsStep() {
        int t = peek().type;
        return t == NAMETEST || t == NODETYPE || t == AXISNAME || t == AT || t == DOT || t == DOTDOT;
    }

    private XPathEngine.Expr parsePath() throws XPathExpressionException {
        List<XPathEngine.Step> steps = new ArrayList<XPathEngine.Step>();
        if (isOp("/")) {
            next();
            if (startsStep()) {
                parseRelative(steps);
            }
            return new XPathEngine.Path(null, true, steps);
        }
        if (isOp("//")) {
            next();
            steps.add(XPathEngine.Step.descendantOrSelf());
            parseRelative(steps);
            return new XPathEngine.Path(null, true, steps);
        }
        if (startsStep()) {
            parseRelative(steps);
            return new XPathEngine.Path(null, false, steps);
        }
        XPathEngine.Expr filter = parsePrimary();
        List<XPathEngine.Expr> predicates = parsePredicates();
        if (!predicates.isEmpty()) {
            filter = new XPathEngine.Filter(filter, predicates);
        }
        if (isOp("/") || isOp("//")) {
            if (next().text.equals("//")) {
                steps.add(XPathEngine.Step.descendantOrSelf());
            }
            parseRelative(steps);
            return new XPathEngine.Path(filter, false, steps);
        }
        return filter;
    }

    private void parseRelative(List<XPathEngine.Step> steps) throws XPathExpressionException {
        steps.add(parseStep());
        while (isOp("/") || isOp("//")) {
            if (next().text.equals("//")) {
                steps.add(XPathEngine.Step.descendantOrSelf());
            }
            steps.add(parseStep());
        }
    }

    private XPathEngine.Step parseStep() throws XPathExpressionException {
        Token t = peek();
        if (t.type == DOT) {
            next();
            return new XPathEngine.Step(XPathEngine.AXIS_SELF, XPathEngine.TEST_NODE, null, null,
                    new ArrayList<XPathEngine.Expr>());
        }
        if (t.type == DOTDOT) {
            next();
            return new XPathEngine.Step(XPathEngine.AXIS_PARENT, XPathEngine.TEST_NODE, null, null,
                    new ArrayList<XPathEngine.Expr>());
        }
        int axis = XPathEngine.AXIS_CHILD;
        if (t.type == AT) {
            next();
            axis = XPathEngine.AXIS_ATTRIBUTE;
        } else if (t.type == AXISNAME) {
            next();
            axis = XPathEngine.axisByName(t.text);
            if (axis < 0) {
                throw error("illegal axis name: " + t.text);
            }
            expect(DCOLON, "::");
        }
        Token test = next();
        int testType;
        String ns = null;
        String local = null;
        if (test.type == NAMETEST) {
            String name = test.text;
            if (name.equals("*")) {
                testType = XPathEngine.TEST_ANY_NAME;
            } else {
                int colon = name.indexOf(':');
                if (colon >= 0) {
                    ns = resolvePrefix(name.substring(0, colon));
                    local = name.substring(colon + 1);
                    testType = local.equals("*") ? XPathEngine.TEST_NS_ANY : XPathEngine.TEST_NAME;
                } else {
                    ns = "";
                    local = name;
                    testType = XPathEngine.TEST_NAME;
                }
            }
        } else if (test.type == NODETYPE) {
            expect(LPAREN, "(");
            if (test.text.equals("processing-instruction")) {
                testType = XPathEngine.TEST_PI;
                if (peek().type == LITERAL) {
                    local = next().text;
                }
            } else if (test.text.equals("text")) {
                testType = XPathEngine.TEST_TEXT;
            } else if (test.text.equals("comment")) {
                testType = XPathEngine.TEST_COMMENT;
            } else {
                testType = XPathEngine.TEST_NODE;
            }
            expect(RPAREN, ")");
        } else {
            throw error("A location step was expected following the '/' or '//' token");
        }
        return new XPathEngine.Step(axis, testType, ns, local, parsePredicates());
    }

    private List<XPathEngine.Expr> parsePredicates() throws XPathExpressionException {
        List<XPathEngine.Expr> predicates = new ArrayList<XPathEngine.Expr>();
        while (peek().type == LBRACKET) {
            next();
            predicates.add(parseOr());
            expect(RBRACKET, "]");
        }
        return predicates;
    }

    String resolvePrefix(String prefix) throws XPathExpressionException {
        String ns = namespaces == null ? null : namespaces.getNamespaceURI(prefix);
        if (ns == null || ns.isEmpty()) {
            if (prefix.equals("xml")) {
                return "http://www.w3.org/XML/1998/namespace";
            }
            throw error("Prefix must resolve to a namespace: " + prefix);
        }
        return ns;
    }

    private XPathEngine.Expr parsePrimary() throws XPathExpressionException {
        Token t = next();
        switch (t.type) {
            case VARREF: {
                int colon = t.text.indexOf(':');
                String ns = colon < 0 ? "" : resolvePrefix(t.text.substring(0, colon));
                return new XPathEngine.Variable(ns, colon < 0 ? t.text : t.text.substring(colon + 1));
            }
            case LPAREN: {
                XPathEngine.Expr e = parseOr();
                expect(RPAREN, ")");
                return e;
            }
            case LITERAL:
                return new XPathEngine.Literal(t.text);
            case NUMBER:
                return new XPathEngine.Literal(Double.valueOf(t.text));
            case FUNCNAME: {
                expect(LPAREN, "(");
                List<XPathEngine.Expr> args = new ArrayList<XPathEngine.Expr>();
                if (peek().type != RPAREN) {
                    args.add(parseOr());
                    while (peek().type == COMMA) {
                        next();
                        args.add(parseOr());
                    }
                }
                expect(RPAREN, ")");
                int colon = t.text.indexOf(':');
                if (colon >= 0) {
                    return new XPathEngine.ExtensionCall(resolvePrefix(t.text.substring(0, colon)),
                            t.text.substring(colon + 1), args);
                }
                return XPathEngine.Call.create(t.text, args, this);
            }
            default:
                throw error("Unexpected token: " + t.text);
        }
    }

    XPathExpressionException errorFor(String message) {
        return error(message);
    }
}
