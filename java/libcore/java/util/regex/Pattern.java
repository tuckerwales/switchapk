package java.util.regex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Predicate;

public final class Pattern implements java.io.Serializable {
    public static final int UNIX_LINES = 0x01;
    public static final int CASE_INSENSITIVE = 0x02;
    public static final int COMMENTS = 0x04;
    public static final int MULTILINE = 0x08;
    public static final int LITERAL = 0x10;
    public static final int DOTALL = 0x20;
    public static final int UNICODE_CASE = 0x40;
    public static final int CANON_EQ = 0x80;
    public static final int UNICODE_CHARACTER_CLASS = 0x100;

    private final String pattern;
    private final int flags;
    Node root;
    int groupCount;
    int repeatCount;
    HashMap<String, Integer> namedGroups = new HashMap<String, Integer>();

    // parser state
    private transient String src;
    private transient int pos;
    private transient int curFlags;

    private Pattern(String p, int f) {
        pattern = p;
        flags = f;
        compileIt();
    }

    public static Pattern compile(String regex) {
        return new Pattern(regex, 0);
    }

    public static Pattern compile(String regex, int flags) {
        return new Pattern(regex, flags);
    }

    public String pattern() {
        return pattern;
    }

    public String toString() {
        return pattern;
    }

    public int flags() {
        return flags;
    }

    public Matcher matcher(CharSequence input) {
        return new Matcher(this, input);
    }

    public static boolean matches(String regex, CharSequence input) {
        return compile(regex).matcher(input).matches();
    }

    public Predicate<String> asPredicate() {
        return new Predicate<String>() {
            public boolean test(String s) {
                return matcher(s).find();
            }
        };
    }

    public Predicate<String> asMatchPredicate() {
        return new Predicate<String>() {
            public boolean test(String s) {
                return matcher(s).matches();
            }
        };
    }

    public String[] split(CharSequence input) {
        return split(input, 0);
    }

    public String[] split(CharSequence input, int limit) {
        int index = 0;
        boolean matchLimited = limit > 0;
        ArrayList<String> matchList = new ArrayList<String>();
        Matcher m = matcher(input);
        while (m.find()) {
            if (!matchLimited || matchList.size() < limit - 1) {
                if (index == 0 && index == m.start() && m.start() == m.end()) {
                    continue;
                }
                matchList.add(input.subSequence(index, m.start()).toString());
                index = m.end();
            } else if (matchList.size() == limit - 1) {
                break;
            }
        }
        if (index == 0) {
            return new String[] {input.toString()};
        }
        matchList.add(input.subSequence(index, input.length()).toString());
        int resultSize = matchList.size();
        if (limit == 0) {
            while (resultSize > 0 && matchList.get(resultSize - 1).isEmpty()) {
                resultSize--;
            }
        }
        String[] result = new String[resultSize];
        return matchList.subList(0, resultSize).toArray(result);
    }

    public java.util.stream.Stream<String> splitAsStream(CharSequence input) {
        return java.util.Arrays.stream(split(input));
    }

    public static String quote(String s) {
        int slashEIndex = s.indexOf("\\E");
        if (slashEIndex == -1) {
            return "\\Q" + s + "\\E";
        }
        StringBuilder sb = new StringBuilder(s.length() * 2);
        sb.append("\\Q");
        int current = 0;
        while ((slashEIndex = s.indexOf("\\E", current)) != -1) {
            sb.append(s, current, slashEIndex);
            current = slashEIndex + 2;
            sb.append("\\E\\\\E\\Q");
        }
        sb.append(s, current, s.length());
        sb.append("\\E");
        return sb.toString();
    }

    /* ================= compiler ================= */

    private PatternSyntaxException error(String msg) {
        return new PatternSyntaxException(msg, pattern, pos);
    }

    private void compileIt() {
        src = pattern;
        pos = 0;
        curFlags = flags;
        if ((flags & LITERAL) != 0) {
            Node head = null;
            Node tail = null;
            for (int i = 0; i < src.length(); i++) {
                Node n = new CharNode(src.charAt(i), (flags & CASE_INSENSITIVE) != 0);
                if (head == null) {
                    head = n;
                } else {
                    tail.next = n;
                }
                tail = n;
            }
            Node acc = new Accept();
            if (head == null) {
                head = acc;
            } else {
                tail.next = acc;
            }
            root = head;
            return;
        }
        Node acc = new Accept();
        root = parseAlternation(acc);
        if (pos < src.length()) {
            throw error("Unmatched closing ')'");
        }
    }

    private boolean more() {
        return pos < src.length();
    }

    private char peek() {
        return src.charAt(pos);
    }

    /** Parses alternatives until ')' or end; every branch continues at 'next'. */
    private Node parseAlternation(Node next) {
        ArrayList<Node> alts = new ArrayList<Node>();
        alts.add(parseSequence(next));
        while (more() && peek() == '|') {
            pos++;
            alts.add(parseSequence(next));
        }
        if (alts.size() == 1) {
            return alts.get(0);
        }
        return new Branch(alts.toArray(new Node[alts.size()]));
    }

    private Node parseSequence(Node next) {
        ArrayList<Object[]> terms = new ArrayList<Object[]>();
        while (more() && peek() != '|' && peek() != ')') {
            Object[] t = parseTerm();
            if (t != null) {
                terms.add(t);
            }
        }
        Node cur = next;
        for (int i = terms.size() - 1; i >= 0; i--) {
            cur = buildTerm(terms.get(i), cur);
        }
        return cur;
    }

    /** A term is {kind, data, min, max, mode}. Built back-to-front so each node knows its continuation. */
    private static final int T_SIMPLE = 0; // single-char matcher (CharPredicate)
    private static final int T_GROUP = 1;
    private static final int T_ANCHOR = 2;
    private static final int T_BACKREF = 3;
    private static final int T_LOOK = 4;

    private Object[] parseTerm() {
        char c = peek();
        Object[] atom;
        switch (c) {
            case '^':
                pos++;
                atom = new Object[] {T_ANCHOR, (curFlags & MULTILINE) != 0 ? "^m" : "^"};
                break;
            case '$':
                pos++;
                atom = new Object[] {T_ANCHOR, (curFlags & MULTILINE) != 0 ? "$m" : "$"};
                break;
            case '.':
                pos++;
                final boolean dotall = (curFlags & DOTALL) != 0;
                atom = new Object[] {T_SIMPLE, new CharPredicate() {
                    boolean is(int ch) {
                        return dotall || (ch != '\n' && ch != '\r' && ch != '\u0085' && ch != ' ' && ch != ' ');
                    }
                }};
                break;
            case '[':
                pos++;
                atom = new Object[] {T_SIMPLE, parseClass()};
                break;
            case '(':
                pos++;
                atom = parseGroup();
                if (atom == null) {
                    return null; // inline flags
                }
                break;
            case '\\':
                pos++;
                atom = parseEscape(false);
                if (atom == null) {
                    return null;
                }
                break;
            case '*':
            case '+':
            case '?':
                throw error("Dangling meta character '" + c + "'");
            default:
                pos++;
                if ((curFlags & COMMENTS) != 0) {
                    if (Character.isWhitespace(c)) {
                        return null;
                    }
                    if (c == '#') {
                        while (more() && peek() != '\n') {
                            pos++;
                        }
                        return null;
                    }
                }
                atom = new Object[] {T_SIMPLE, literal(c)};
        }
        // quantifier
        int min = 1, max = 1;
        int mode = 0; // 0 greedy, 1 lazy, 2 possessive
        if (more()) {
            char q = peek();
            if (q == '*' || q == '+' || q == '?' || (q == '{' && isQuantBrace())) {
                pos++;
                if (q == '*') {
                    min = 0;
                    max = Integer.MAX_VALUE;
                } else if (q == '+') {
                    min = 1;
                    max = Integer.MAX_VALUE;
                } else if (q == '?') {
                    min = 0;
                    max = 1;
                } else {
                    int start = pos;
                    while (more() && peek() != '}') {
                        pos++;
                    }
                    if (!more()) {
                        throw error("Unclosed counted closure");
                    }
                    String spec = src.substring(start, pos);
                    pos++;
                    int comma = spec.indexOf(',');
                    if (comma < 0) {
                        min = max = Integer.parseInt(spec.trim());
                    } else {
                        min = Integer.parseInt(spec.substring(0, comma).trim());
                        String hi = spec.substring(comma + 1).trim();
                        max = hi.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(hi);
                    }
                }
                if (more() && peek() == '?') {
                    pos++;
                    mode = 1;
                } else if (more() && peek() == '+') {
                    pos++;
                    mode = 2;
                }
            }
        }
        return new Object[] {atom[0], atom[1], min, max, mode, atom.length > 2 ? atom[2] : null, atom.length > 3 ? atom[3] : null};
    }

    private boolean isQuantBrace() {
        int p = pos + 1;
        boolean digit = false;
        while (p < src.length() && Character.isDigit(src.charAt(p))) {
            p++;
            digit = true;
        }
        return digit && p < src.length() && (src.charAt(p) == '}' || src.charAt(p) == ',');
    }

    private CharPredicate literal(final int c) {
        return new LiteralPred(c, (curFlags & CASE_INSENSITIVE) != 0);
    }

    private Object[] parseGroup() {
        int kind = 0; // 0 capturing, 1 non-capturing, 2 lookahead, 3 neg lookahead, 4 lookbehind, 5 neg lookbehind
        String name = null;
        int savedFlags = curFlags;
        if (more() && peek() == '?') {
            pos++;
            char c = src.charAt(pos++);
            if (c == ':') {
                kind = 1;
            } else if (c == '=') {
                kind = 2;
            } else if (c == '!') {
                kind = 3;
            } else if (c == '>') {
                kind = 1; // atomic group approximated as non-capturing
            } else if (c == '<') {
                char d = src.charAt(pos);
                if (d == '=') {
                    pos++;
                    kind = 4;
                } else if (d == '!') {
                    pos++;
                    kind = 5;
                } else {
                    int start = pos;
                    while (more() && peek() != '>') {
                        pos++;
                    }
                    name = src.substring(start, pos);
                    pos++;
                    kind = 0;
                }
            } else {
                // inline flags (?i) (?-i) (?i:...)
                pos--;
                boolean on = true;
                int f = curFlags;
                while (more()) {
                    char fc = peek();
                    if (fc == ')' || fc == ':') {
                        break;
                    }
                    pos++;
                    int bit = 0;
                    switch (fc) {
                        case '-': on = false; continue;
                        case 'i': bit = CASE_INSENSITIVE; break;
                        case 'm': bit = MULTILINE; break;
                        case 's': bit = DOTALL; break;
                        case 'x': bit = COMMENTS; break;
                        case 'u': bit = UNICODE_CASE; break;
                        case 'd': bit = UNIX_LINES; break;
                        case 'U': bit = UNICODE_CHARACTER_CLASS; break;
                        default: throw error("Unknown inline modifier");
                    }
                    f = on ? (f | bit) : (f & ~bit);
                }
                if (!more()) {
                    throw error("Unclosed group");
                }
                if (peek() == ')') {
                    pos++;
                    curFlags = f;
                    return null;
                }
                pos++; // ':'
                curFlags = f;
                kind = 1;
            }
        }
        int groupIndex = -1;
        if (kind == 0) {
            groupIndex = ++groupCount;
            if (name != null) {
                namedGroups.put(name, groupIndex);
            }
        }
        // parse body now with a placeholder continuation; built later
        int bodyStart = pos;
        int depth = 0;
        // We need the raw body to rebuild with continuation; parse with a GroupTail sentinel.
        GroupTail tail = new GroupTail();
        Node body = parseAlternation(tail);
        if (!more() || peek() != ')') {
            throw error("Unclosed group");
        }
        pos++;
        if (kind == 1 && savedFlags != curFlags && name == null) {
            curFlags = savedFlags;
        }
        return new Object[] {kind >= 2 ? T_LOOK : T_GROUP, body, groupIndex, new Object[] {kind, tail}};
    }

    private Object[] parseEscape(boolean inClass) {
        char c = src.charAt(pos++);
        switch (c) {
            case 'd': return new Object[] {T_SIMPLE, CharPredicate.DIGIT};
            case 'D': return new Object[] {T_SIMPLE, CharPredicate.DIGIT.negate()};
            case 'w': return new Object[] {T_SIMPLE, CharPredicate.WORD};
            case 'W': return new Object[] {T_SIMPLE, CharPredicate.WORD.negate()};
            case 's': return new Object[] {T_SIMPLE, CharPredicate.SPACE};
            case 'S': return new Object[] {T_SIMPLE, CharPredicate.SPACE.negate()};
            case 'h': return new Object[] {T_SIMPLE, CharPredicate.HSPACE};
            case 'H': return new Object[] {T_SIMPLE, CharPredicate.HSPACE.negate()};
            case 'v': return new Object[] {T_SIMPLE, CharPredicate.VSPACE};
            case 'V': return new Object[] {T_SIMPLE, CharPredicate.VSPACE.negate()};
            case 'R': return new Object[] {T_SIMPLE, CharPredicate.VSPACE};
            case 'p':
            case 'P': {
                String prop;
                if (peek() == '{') {
                    int end = src.indexOf('}', pos);
                    prop = src.substring(pos + 1, end);
                    pos = end + 1;
                } else {
                    prop = String.valueOf(src.charAt(pos++));
                }
                CharPredicate p = CharPredicate.property(prop);
                if (p == null) {
                    throw error("Unknown character property name {" + prop + "}");
                }
                return new Object[] {T_SIMPLE, c == 'P' ? p.negate() : p};
            }
            case 'b':
                if (inClass) {
                    return new Object[] {T_SIMPLE, literal('\b')};
                }
                return new Object[] {T_ANCHOR, "\\b"};
            case 'B': return new Object[] {T_ANCHOR, "\\B"};
            case 'A': return new Object[] {T_ANCHOR, "\\A"};
            case 'G': return new Object[] {T_ANCHOR, "\\G"};
            case 'z': return new Object[] {T_ANCHOR, "\\z"};
            case 'Z': return new Object[] {T_ANCHOR, "\\Z"};
            case 'Q': {
                int end = src.indexOf("\\E", pos);
                String lit = end < 0 ? src.substring(pos) : src.substring(pos, end);
                pos = end < 0 ? src.length() : end + 2;
                return new Object[] {T_GROUP, literalChain(lit), -1, new Object[] {-1, null}};
            }
            case 'k': {
                int end = src.indexOf('>', pos);
                String name = src.substring(pos + 1, end);
                pos = end + 1;
                Integer g = namedGroups.get(name);
                if (g == null) {
                    throw error("named capturing group <" + name + "> does not exist");
                }
                return new Object[] {T_BACKREF, g};
            }
            case 't': return new Object[] {T_SIMPLE, literal('\t')};
            case 'n': return new Object[] {T_SIMPLE, literal('\n')};
            case 'r': return new Object[] {T_SIMPLE, literal('\r')};
            case 'f': return new Object[] {T_SIMPLE, literal('\f')};
            case 'a': return new Object[] {T_SIMPLE, literal('\u0007')};
            case 'e': return new Object[] {T_SIMPLE, literal('\u001b')};
            case '0': {
                int v = 0;
                int n = 0;
                while (more() && n < 3 && peek() >= '0' && peek() <= '7') {
                    v = v * 8 + (src.charAt(pos++) - '0');
                    n++;
                }
                return new Object[] {T_SIMPLE, literal(v)};
            }
            case 'x': {
                int v;
                if (peek() == '{') {
                    int end = src.indexOf('}', pos);
                    v = Integer.parseInt(src.substring(pos + 1, end), 16);
                    pos = end + 1;
                } else {
                    v = Integer.parseInt(src.substring(pos, pos + 2), 16);
                    pos += 2;
                }
                return new Object[] {T_SIMPLE, literal(v)};
            }
            case 'u': {
                int v = Integer.parseInt(src.substring(pos, pos + 4), 16);
                pos += 4;
                return new Object[] {T_SIMPLE, literal(v)};
            }
            case 'c': {
                char cc = src.charAt(pos++);
                return new Object[] {T_SIMPLE, literal(cc ^ 64)};
            }
            default:
                if (c >= '1' && c <= '9' && !inClass) {
                    int g = c - '0';
                    while (more() && Character.isDigit(peek()) && g * 10 + (peek() - '0') <= groupCount) {
                        g = g * 10 + (src.charAt(pos++) - '0');
                    }
                    return new Object[] {T_BACKREF, g};
                }
                if (Character.isLetterOrDigit(c)) {
                    throw error("Illegal/unsupported escape sequence");
                }
                return new Object[] {T_SIMPLE, literal(c)};
        }
    }

    private Node literalChain(String lit) {
        GroupTail tail = new GroupTail();
        Node cur = tail;
        for (int i = lit.length() - 1; i >= 0; i--) {
            CharNode n = new CharNode(lit.charAt(i), (curFlags & CASE_INSENSITIVE) != 0);
            n.next = cur;
            cur = n;
        }
        lastLiteralTail = tail;
        return cur;
    }

    private transient GroupTail lastLiteralTail;

    private CharPredicate parseClass() {
        boolean negate = false;
        if (more() && peek() == '^') {
            pos++;
            negate = true;
        }
        CharPredicate result = null;
        boolean first = true;
        while (more() && (peek() != ']' || first)) {
            first = false;
            CharPredicate item;
            char c = src.charAt(pos);
            if (c == '[') {
                pos++;
                item = parseClass();
            } else if (c == '&' && pos + 1 < src.length() && src.charAt(pos + 1) == '&') {
                pos += 2;
                CharPredicate rhs;
                if (more() && peek() == '[') {
                    pos++;
                    rhs = parseClass();
                } else {
                    // intersection with remainder of this class
                    StringBuilder rest = new StringBuilder();
                    int depth = 0;
                    while (more() && !(peek() == ']' && depth == 0)) {
                        if (peek() == '[') {
                            depth++;
                        } else if (peek() == ']') {
                            depth--;
                        }
                        rest.append(src.charAt(pos++));
                    }
                    Pattern sub = new Pattern("[" + rest + "]", curFlags);
                    rhs = ((SingleNode) sub.root).pred;
                }
                result = (result == null ? CharPredicate.NONE : result).and(rhs);
                continue;
            } else {
                int lo;
                if (c == '\\') {
                    pos++;
                    Object[] esc = parseEscape(true);
                    CharPredicate ep = (CharPredicate) esc[1];
                    Integer lit = ep.literalValue();
                    if (lit == null) {
                        result = result == null ? ep : result.or(ep);
                        continue;
                    }
                    lo = lit;
                } else {
                    pos++;
                    lo = c;
                }
                if (more() && peek() == '-' && pos + 1 < src.length() && src.charAt(pos + 1) != ']') {
                    pos++;
                    int hi;
                    char hc = src.charAt(pos);
                    if (hc == '\\') {
                        pos++;
                        Object[] esc = parseEscape(true);
                        Integer lit = ((CharPredicate) esc[1]).literalValue();
                        if (lit == null) {
                            throw error("Illegal character range");
                        }
                        hi = lit;
                    } else {
                        pos++;
                        hi = hc;
                    }
                    if (hi < lo) {
                        throw error("Illegal character range");
                    }
                    item = CharPredicate.range(lo, hi, (curFlags & CASE_INSENSITIVE) != 0);
                } else {
                    item = CharPredicate.range(lo, lo, (curFlags & CASE_INSENSITIVE) != 0);
                }
            }
            result = result == null ? item : result.or(item);
        }
        if (!more()) {
            throw error("Unclosed character class");
        }
        pos++; // ]
        if (result == null) {
            result = CharPredicate.NONE;
        }
        return negate ? result.negate() : result;
    }

    private Node buildTerm(Object[] t, Node next) {
        int kind = (Integer) t[0];
        int min = (Integer) t[2];
        int max = (Integer) t[3];
        int mode = (Integer) t[4];
        switch (kind) {
            case T_SIMPLE: {
                CharPredicate p = (CharPredicate) t[1];
                if (min == 1 && max == 1) {
                    SingleNode n = new SingleNode(p);
                    n.next = next;
                    return n;
                }
                CharRepeat r = new CharRepeat(p, min, max, mode);
                r.next = next;
                return r;
            }
            case T_ANCHOR: {
                Node n = new Anchor((String) t[1]);
                n.next = next;
                return n;
            }
            case T_BACKREF: {
                Node n = new BackRef((Integer) t[1], (curFlags & CASE_INSENSITIVE) != 0);
                n.next = next;
                return wrapRepeat(n, n, min, max, mode, next);
            }
            case T_LOOK: {
                Object[] info = (Object[]) t[6];
                int lk = (Integer) info[0];
                GroupTail tail = (GroupTail) info[1];
                tail.next = new Accept();
                Look n = new Look((Node) t[1], lk == 2 || lk == 4, lk >= 4);
                n.next = next;
                return n;
            }
            default: {
                Node body = (Node) t[1];
                int groupIndex = t[5] == null ? -1 : (Integer) t[5];
                Object[] info = (Object[]) t[6];
                GroupTail tail = info[1] != null ? (GroupTail) info[1] : lastLiteralTail;
                if (min == 1 && max == 1) {
                    if (groupIndex > 0) {
                        GroupStart gs = new GroupStart(groupIndex);
                        GroupEnd ge = new GroupEnd(groupIndex);
                        gs.next = body;
                        tail.next = ge;
                        ge.next = next;
                        return gs;
                    }
                    tail.next = next;
                    return body;
                }
                Repeat r = new Repeat(repeatCount++, min, max, mode);
                Node start = body;
                if (groupIndex > 0) {
                    GroupStart gs = new GroupStart(groupIndex);
                    gs.next = body;
                    start = gs;
                    GroupEnd ge = new GroupEnd(groupIndex);
                    tail.next = ge;
                    ge.next = new RepeatTail(r);
                } else {
                    tail.next = new RepeatTail(r);
                }
                r.body = start;
                r.next = next;
                return r;
            }
        }
    }

    private Node wrapRepeat(Node single, Node last, int min, int max, int mode, Node next) {
        if (min == 1 && max == 1) {
            return single;
        }
        Repeat r = new Repeat(repeatCount++, min, max, mode);
        last.next = new RepeatTail(r);
        r.body = single;
        r.next = next;
        return r;
    }

    /* ================= nodes ================= */

    abstract static class Node {
        Node next;

        abstract boolean match(Matcher m, int i, CharSequence s);
    }

    static final class Accept extends Node {
        boolean match(Matcher m, int i, CharSequence s) {
            if (m.acceptMode == 1 && i != m.to) {
                return false;
            }
            m.matchEnd = i;
            return true;
        }
    }

    static final class GroupTail extends Node {
        boolean match(Matcher m, int i, CharSequence s) {
            return next.match(m, i, s);
        }
    }

    static final class CharNode extends Node {
        final char c;
        final boolean ci;

        CharNode(char c, boolean ci) {
            this.c = c;
            this.ci = ci;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            if (i >= m.to) {
                m.hitEnd = true;
                return false;
            }
            char ch = s.charAt(i);
            if (ch != c && !(ci && Character.toLowerCase(ch) == Character.toLowerCase(c))) {
                return false;
            }
            return next.match(m, i + 1, s);
        }
    }

    static final class SingleNode extends Node {
        final CharPredicate pred;

        SingleNode(CharPredicate p) {
            pred = p;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            if (i >= m.to) {
                m.hitEnd = true;
                return false;
            }
            char ch = s.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < m.to && Character.isLowSurrogate(s.charAt(i + 1))) {
                int cp = Character.toCodePoint(ch, s.charAt(i + 1));
                return pred.is(cp) && next.match(m, i + 2, s);
            }
            return pred.is(ch) && next.match(m, i + 1, s);
        }
    }

    static final class CharRepeat extends Node {
        final CharPredicate pred;
        final int min, max, mode;

        CharRepeat(CharPredicate p, int min, int max, int mode) {
            pred = p;
            this.min = min;
            this.max = max;
            this.mode = mode;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int end = m.to;
            if (mode == 1) {
                int j = i;
                int count = 0;
                while (count < min) {
                    if (j >= end || !pred.is(s.charAt(j))) {
                        return false;
                    }
                    j++;
                    count++;
                }
                for (;;) {
                    if (next.match(m, j, s)) {
                        return true;
                    }
                    if (count >= max || j >= end || !pred.is(s.charAt(j))) {
                        return false;
                    }
                    j++;
                    count++;
                }
            }
            int j = i;
            int count = 0;
            while (count < max && j < end && pred.is(s.charAt(j))) {
                j++;
                count++;
            }
            if (j >= end) {
                m.hitEnd = true;
            }
            if (count < min) {
                return false;
            }
            if (mode == 2) {
                return next.match(m, j, s);
            }
            while (true) {
                if (next.match(m, j, s)) {
                    return true;
                }
                if (count == min) {
                    return false;
                }
                j--;
                count--;
            }
        }
    }

    static final class Branch extends Node {
        final Node[] alts;

        Branch(Node[] alts) {
            this.alts = alts;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            for (Node a : alts) {
                if (a.match(m, i, s)) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class GroupStart extends Node {
        final int g;

        GroupStart(int g) {
            this.g = g;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int saved = m.tmpStart[g];
            m.tmpStart[g] = i;
            if (next.match(m, i, s)) {
                return true;
            }
            m.tmpStart[g] = saved;
            return false;
        }
    }

    static final class GroupEnd extends Node {
        final int g;

        GroupEnd(int g) {
            this.g = g;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int os = m.groups[g * 2], oe = m.groups[g * 2 + 1];
            m.groups[g * 2] = m.tmpStart[g];
            m.groups[g * 2 + 1] = i;
            if (next.match(m, i, s)) {
                return true;
            }
            m.groups[g * 2] = os;
            m.groups[g * 2 + 1] = oe;
            return false;
        }
    }

    static final class Repeat extends Node {
        final int id, min, max, mode;
        Node body;

        Repeat(int id, int min, int max, int mode) {
            this.id = id;
            this.min = min;
            this.max = max;
            this.mode = mode;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int savedCount = m.repCount[id];
            int savedStart = m.repStart[id];
            m.repCount[id] = 0;
            m.repStart[id] = i;
            boolean r = iterate(m, i, s);
            m.repCount[id] = savedCount;
            m.repStart[id] = savedStart;
            return r;
        }

        boolean iterate(Matcher m, int i, CharSequence s) {
            int count = m.repCount[id];
            if (count < min) {
                m.repStart[id] = i;
                return body.match(m, i, s);
            }
            if (mode == 1) {
                if (next.match(m, i, s)) {
                    return true;
                }
                if (count < max) {
                    m.repStart[id] = i;
                    return body.match(m, i, s);
                }
                return false;
            }
            if (count < max) {
                m.repStart[id] = i;
                if (body.match(m, i, s)) {
                    return true;
                }
            }
            return next.match(m, i, s);
        }
    }

    static final class RepeatTail extends Node {
        final Repeat r;

        RepeatTail(Repeat r) {
            this.r = r;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int id = r.id;
            int start = m.repStart[id];
            int count = m.repCount[id];
            if (i == start && count >= r.min) {
                return false; // zero-length iteration: stop looping
            }
            m.repCount[id] = count + 1;
            boolean ok = r.iterate(m, i, s);
            m.repCount[id] = count;
            m.repStart[id] = start;
            return ok;
        }
    }

    static final class BackRef extends Node {
        final int g;
        final boolean ci;

        BackRef(int g, boolean ci) {
            this.g = g;
            this.ci = ci;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int gs = m.groups[g * 2], ge = m.groups[g * 2 + 1];
            if (gs < 0) {
                return false;
            }
            int len = ge - gs;
            if (i + len > m.to) {
                m.hitEnd = true;
                return false;
            }
            for (int k = 0; k < len; k++) {
                char a = s.charAt(gs + k), b = s.charAt(i + k);
                if (a != b && !(ci && Character.toLowerCase(a) == Character.toLowerCase(b))) {
                    return false;
                }
            }
            return next.match(m, i + len, s);
        }
    }

    static final class Look extends Node {
        final Node body;
        final boolean positive;
        final boolean behind;

        Look(Node body, boolean positive, boolean behind) {
            this.body = body;
            this.positive = positive;
            this.behind = behind;
        }

        boolean match(Matcher m, int i, CharSequence s) {
            int savedEnd = m.matchEnd;
            int savedMode = m.acceptMode;
            int savedTo = m.to;
            boolean found = false;
            if (behind) {
                m.acceptMode = 1;
                m.to = i;
                for (int st = i; st >= m.from && !found; st--) {
                    found = body.match(m, st, s);
                }
            } else {
                m.acceptMode = 0;
                found = body.match(m, i, s);
            }
            m.to = savedTo;
            m.acceptMode = savedMode;
            m.matchEnd = savedEnd;
            if (found != positive) {
                return false;
            }
            return next.match(m, i, s);
        }
    }

    static final class Anchor extends Node {
        final String kind;

        Anchor(String kind) {
            this.kind = kind;
        }

        private static boolean isWord(CharSequence s, int i, int from, int to) {
            return i >= from && i < to && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_');
        }

        private static boolean isLineTerm(char c) {
            return c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' ';
        }

        boolean match(Matcher m, int i, CharSequence s) {
            boolean ok;
            switch (kind) {
                case "^": ok = i == m.from; break;
                case "^m": ok = i == m.from || (i < m.to && i > m.from && isLineTerm(s.charAt(i - 1))); break;
                case "$":
                    ok = i == m.to || (i == m.to - 1 && isLineTerm(s.charAt(i)))
                            || (i == m.to - 2 && s.charAt(i) == '\r' && s.charAt(i + 1) == '\n');
                    break;
                case "$m": ok = i == m.to || isLineTerm(s.charAt(i)); break;
                case "\\A": ok = i == m.from; break;
                case "\\G": ok = i == m.lastAppendPos || i == m.searchStart; break;
                case "\\z": ok = i == m.to; break;
                case "\\Z": ok = i == m.to || (i == m.to - 1 && isLineTerm(s.charAt(i))); break;
                case "\\b": ok = isWord(s, i - 1, m.from, m.to) != isWord(s, i, m.from, m.to); break;
                case "\\B": ok = isWord(s, i - 1, m.from, m.to) == isWord(s, i, m.from, m.to); break;
                default: ok = false;
            }
            if (i >= m.to) {
                m.hitEnd = true;
            }
            return ok && next.match(m, i, s);
        }
    }

    /* ================= character predicates ================= */

    abstract static class CharPredicate {
        abstract boolean is(int ch);

        Integer literalValue() {
            return null;
        }

        CharPredicate negate() {
            final CharPredicate self = this;
            return new CharPredicate() {
                boolean is(int ch) {
                    return !self.is(ch);
                }
            };
        }

        CharPredicate or(final CharPredicate other) {
            final CharPredicate self = this;
            return new CharPredicate() {
                boolean is(int ch) {
                    return self.is(ch) || other.is(ch);
                }
            };
        }

        CharPredicate and(final CharPredicate other) {
            final CharPredicate self = this;
            return new CharPredicate() {
                boolean is(int ch) {
                    return self.is(ch) && other.is(ch);
                }
            };
        }

        static CharPredicate range(final int lo, final int hi, final boolean ci) {
            if (lo == hi) {
                return new LiteralPred(lo, ci);
            }
            return new CharPredicate() {
                boolean is(int ch) {
                    if (ch >= lo && ch <= hi) {
                        return true;
                    }
                    if (ci) {
                        int u = Character.toUpperCase(ch), l = Character.toLowerCase(ch);
                        return (u >= lo && u <= hi) || (l >= lo && l <= hi);
                    }
                    return false;
                }
            };
        }

        static final CharPredicate NONE = new CharPredicate() {
            boolean is(int ch) {
                return false;
            }
        };
        static final CharPredicate DIGIT = new CharPredicate() {
            boolean is(int ch) {
                return ch >= '0' && ch <= '9';
            }
        };
        static final CharPredicate WORD = new CharPredicate() {
            boolean is(int ch) {
                return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_';
            }
        };
        static final CharPredicate SPACE = new CharPredicate() {
            boolean is(int ch) {
                return ch == ' ' || ch == '\t' || ch == '\n' || ch == 0x0b || ch == '\f' || ch == '\r';
            }
        };
        static final CharPredicate HSPACE = new CharPredicate() {
            boolean is(int ch) {
                return ch == ' ' || ch == '\t' || ch == 0xa0 || ch == 0x1680 || ch == 0x180e || (ch >= 0x2000 && ch <= 0x200a)
                        || ch == 0x202f || ch == 0x205f || ch == 0x3000;
            }
        };
        static final CharPredicate VSPACE = new CharPredicate() {
            boolean is(int ch) {
                return ch == '\n' || ch == 0x0b || ch == '\f' || ch == '\r' || ch == 0x85 || ch == 0x2028 || ch == 0x2029;
            }
        };

        /* Java's rules: In/blk= is a block, Is/sc= a script, binary property or category, gc= a
         * category, java* a Character.isX method, a bare name a POSIX class (ASCII) or category. */
        static CharPredicate property(String name) {
            int eq = name.indexOf('=');
            if (eq > 0) {
                String key = name.substring(0, eq).toLowerCase();
                String value = name.substring(eq + 1);
                switch (key) {
                    case "blk": case "block":
                        return block(value);
                    case "sc": case "script":
                        return script(value);
                    case "gc": case "general_category":
                        return category(value);
                    default:
                        return null;
                }
            }
            if (name.startsWith("In")) {
                return block(name.substring(2));
            }
            if (name.startsWith("Is")) {
                String rest = name.substring(2);
                CharPredicate p = binary(rest);
                if (p == null) {
                    p = category(rest);
                }
                return p != null ? p : script(rest);
            }
            if (name.startsWith("java")) {
                return javaMethod(name.substring(4));
            }
            CharPredicate p = posix(name);
            if (p == null) {
                p = category(name);
            }
            // Lenient beyond Java: a bare script name such as Han.
            return p != null ? p : script(name);
        }

        static CharPredicate block(String name) {
            final Character.UnicodeBlock b;
            try {
                b = Character.UnicodeBlock.forName(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new CharPredicate() {
                boolean is(int ch) {
                    return Character.UnicodeBlock.of(ch) == b;
                }
            };
        }

        static CharPredicate category(String name) {
            int mask = 0;
            switch (name) {
                case "Cn": mask = 1 << Character.UNASSIGNED; break;
                case "Lu": mask = 1 << Character.UPPERCASE_LETTER; break;
                case "Ll": mask = 1 << Character.LOWERCASE_LETTER; break;
                case "Lt": mask = 1 << Character.TITLECASE_LETTER; break;
                case "Lm": mask = 1 << Character.MODIFIER_LETTER; break;
                case "Lo": mask = 1 << Character.OTHER_LETTER; break;
                case "Mn": mask = 1 << Character.NON_SPACING_MARK; break;
                case "Me": mask = 1 << Character.ENCLOSING_MARK; break;
                case "Mc": mask = 1 << Character.COMBINING_SPACING_MARK; break;
                case "Nd": mask = 1 << Character.DECIMAL_DIGIT_NUMBER; break;
                case "Nl": mask = 1 << Character.LETTER_NUMBER; break;
                case "No": mask = 1 << Character.OTHER_NUMBER; break;
                case "Zs": mask = 1 << Character.SPACE_SEPARATOR; break;
                case "Zl": mask = 1 << Character.LINE_SEPARATOR; break;
                case "Zp": mask = 1 << Character.PARAGRAPH_SEPARATOR; break;
                case "Cc": mask = 1 << Character.CONTROL; break;
                case "Cf": mask = 1 << Character.FORMAT; break;
                case "Co": mask = 1 << Character.PRIVATE_USE; break;
                case "Cs": mask = 1 << Character.SURROGATE; break;
                case "Pd": mask = 1 << Character.DASH_PUNCTUATION; break;
                case "Ps": mask = 1 << Character.START_PUNCTUATION; break;
                case "Pe": mask = 1 << Character.END_PUNCTUATION; break;
                case "Pc": mask = 1 << Character.CONNECTOR_PUNCTUATION; break;
                case "Po": mask = 1 << Character.OTHER_PUNCTUATION; break;
                case "Sm": mask = 1 << Character.MATH_SYMBOL; break;
                case "Sc": mask = 1 << Character.CURRENCY_SYMBOL; break;
                case "Sk": mask = 1 << Character.MODIFIER_SYMBOL; break;
                case "So": mask = 1 << Character.OTHER_SYMBOL; break;
                case "Pi": mask = 1 << Character.INITIAL_QUOTE_PUNCTUATION; break;
                case "Pf": mask = 1 << Character.FINAL_QUOTE_PUNCTUATION; break;
                case "L":
                    mask = (1 << Character.UPPERCASE_LETTER) | (1 << Character.LOWERCASE_LETTER)
                            | (1 << Character.TITLECASE_LETTER) | (1 << Character.MODIFIER_LETTER)
                            | (1 << Character.OTHER_LETTER);
                    break;
                case "LC":
                    mask = (1 << Character.UPPERCASE_LETTER) | (1 << Character.LOWERCASE_LETTER)
                            | (1 << Character.TITLECASE_LETTER);
                    break;
                case "M":
                    mask = (1 << Character.NON_SPACING_MARK) | (1 << Character.ENCLOSING_MARK)
                            | (1 << Character.COMBINING_SPACING_MARK);
                    break;
                case "N":
                    mask = (1 << Character.DECIMAL_DIGIT_NUMBER) | (1 << Character.LETTER_NUMBER)
                            | (1 << Character.OTHER_NUMBER);
                    break;
                case "Z":
                    mask = (1 << Character.SPACE_SEPARATOR) | (1 << Character.LINE_SEPARATOR)
                            | (1 << Character.PARAGRAPH_SEPARATOR);
                    break;
                case "C":
                    mask = (1 << Character.CONTROL) | (1 << Character.FORMAT) | (1 << Character.PRIVATE_USE)
                            | (1 << Character.SURROGATE) | (1 << Character.UNASSIGNED);
                    break;
                case "P":
                    mask = (1 << Character.DASH_PUNCTUATION) | (1 << Character.START_PUNCTUATION)
                            | (1 << Character.END_PUNCTUATION) | (1 << Character.CONNECTOR_PUNCTUATION)
                            | (1 << Character.OTHER_PUNCTUATION) | (1 << Character.INITIAL_QUOTE_PUNCTUATION)
                            | (1 << Character.FINAL_QUOTE_PUNCTUATION);
                    break;
                case "S":
                    mask = (1 << Character.MATH_SYMBOL) | (1 << Character.CURRENCY_SYMBOL)
                            | (1 << Character.MODIFIER_SYMBOL) | (1 << Character.OTHER_SYMBOL);
                    break;
                default:
                    return null;
            }
            final int m = mask;
            return new CharPredicate() {
                boolean is(int ch) {
                    return (m & (1 << Character.getType(ch))) != 0;
                }
            };
        }

        static CharPredicate binary(String name) {
            switch (name.toLowerCase().replace("_", "").replace(" ", "")) {
                case "alphabetic":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isAlphabetic(ch);
                        }
                    };
                case "ideographic":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isIdeographic(ch);
                        }
                    };
                case "letter":
                    return category("L");
                case "lowercase":
                    return javaMethod("LowerCase");
                case "uppercase":
                    return javaMethod("UpperCase");
                case "titlecase":
                    return category("Lt");
                case "punctuation":
                    return category("P");
                case "control":
                    return category("Cc");
                case "whitespace":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return (ch >= 0x9 && ch <= 0xd) || ch == 0x85 || Character.isSpaceChar(ch);
                        }
                    };
                case "digit":
                    return category("Nd");
                case "hexdigit":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.digit(ch, 16) >= 0;
                        }
                    };
                case "joincontrol":
                    return range(0x200c, 0x200d, false);
                case "noncharactercodepoint":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return (ch & 0xfffe) == 0xfffe || (ch >= 0xfdd0 && ch <= 0xfdef);
                        }
                    };
                case "assigned":
                    return category("Cn").negate();
                default:
                    return null;
            }
        }

        static CharPredicate javaMethod(String name) {
            switch (name) {
                case "LowerCase":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isLowerCase(ch);
                        }
                    };
                case "UpperCase":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isUpperCase(ch);
                        }
                    };
                case "TitleCase":
                    return category("Lt");
                case "Alphabetic":
                    return binary("Alphabetic");
                case "Ideographic":
                    return binary("Ideographic");
                case "Letter":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isLetter(ch);
                        }
                    };
                case "Digit":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isDigit(ch);
                        }
                    };
                case "LetterOrDigit":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isLetterOrDigit(ch);
                        }
                    };
                case "Whitespace":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isWhitespace(ch);
                        }
                    };
                case "SpaceChar":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isSpaceChar(ch);
                        }
                    };
                case "ISOControl":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isISOControl(ch);
                        }
                    };
                case "Defined":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isDefined(ch);
                        }
                    };
                case "Mirrored":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isMirrored(ch);
                        }
                    };
                case "IdentifierIgnorable":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isIdentifierIgnorable(ch);
                        }
                    };
                case "JavaIdentifierStart":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isJavaIdentifierStart(ch);
                        }
                    };
                case "JavaIdentifierPart":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isJavaIdentifierPart(ch);
                        }
                    };
                case "UnicodeIdentifierStart":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isUnicodeIdentifierStart(ch);
                        }
                    };
                case "UnicodeIdentifierPart":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return Character.isUnicodeIdentifierPart(ch);
                        }
                    };
                default:
                    return null;
            }
        }

        /* POSIX classes are US-ASCII only, as in Java without UNICODE_CHARACTER_CLASS. */
        static CharPredicate posix(String name) {
            switch (name) {
                case "Lower":
                    return range('a', 'z', false);
                case "Upper":
                    return range('A', 'Z', false);
                case "ASCII":
                    return range(0, 0x7f, false);
                case "Alpha":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
                        }
                    };
                case "Digit":
                    return range('0', '9', false);
                case "Alnum":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
                        }
                    };
                case "Punct":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return (ch >= 0x21 && ch <= 0x2f) || (ch >= 0x3a && ch <= 0x40) || (ch >= 0x5b && ch <= 0x60)
                                    || (ch >= 0x7b && ch <= 0x7e);
                        }
                    };
                case "Graph":
                    return range(0x21, 0x7e, false);
                case "Print":
                    return range(0x20, 0x7e, false);
                case "Blank":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return ch == ' ' || ch == '\t';
                        }
                    };
                case "Cntrl":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return ch < 0x20 || ch == 0x7f;
                        }
                    };
                case "XDigit":
                    return new CharPredicate() {
                        boolean is(int ch) {
                            return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
                        }
                    };
                case "Space":
                    return SPACE;
                default:
                    return null;
            }
        }

        /* No UnicodeScript tables: a script is the letters, marks and letter numbers of the blocks
         * named after it (Latin: BASIC_LATIN, LATIN_EXTENDED_A, ...), Common everything else. */
        static CharPredicate script(String name) {
            final String key = name.toUpperCase().replace(' ', '_');
            if (key.equals("COMMON") || key.equals("ZYYY")) {
                return new CharPredicate() {
                    boolean is(int ch) {
                        return !inScript(ch);
                    }
                };
            }
            final String blockKey;
            switch (key) {
                case "HAN": case "HANI": blockKey = "CJK"; break;
                case "LATN": blockKey = "LATIN"; break;
                case "GREK": blockKey = "GREEK"; break;
                case "CYRL": blockKey = "CYRILLIC"; break;
                case "HIRA": blockKey = "HIRAGANA"; break;
                case "KANA": blockKey = "KATAKANA"; break;
                case "HANG": case "HANGUL": blockKey = "HANGUL"; break;
                case "ARAB": blockKey = "ARABIC"; break;
                case "HEBR": blockKey = "HEBREW"; break;
                case "THAI": blockKey = "THAI"; break;
                default: blockKey = key;
            }
            boolean known;
            try {
                Character.UnicodeBlock.forName(blockKey);
                known = true;
            } catch (IllegalArgumentException e) {
                known = false;
            }
            for (int cp = 0; cp < 0x20000 && !known; cp += 0x80) {
                Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
                known = b != null && b.toString().contains(blockKey);
            }
            if (!known) {
                return null;
            }
            return new CharPredicate() {
                boolean is(int ch) {
                    if (!inScript(ch)) {
                        return false;
                    }
                    Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
                    if (b == null) {
                        return false;
                    }
                    String n = b.toString();
                    if (blockKey.equals("CJK")) {
                        return n.startsWith("CJK_UNIFIED") || n.startsWith("CJK_COMPATIBILITY_IDEOGRAPHS")
                                || n.equals("KANGXI_RADICALS") || n.equals("CJK_RADICALS_SUPPLEMENT")
                                || ch == 0x3005 || ch == 0x3007 || (ch >= 0x3021 && ch <= 0x3029);
                    }
                    if (blockKey.equals("LATIN") && n.equals("HALFWIDTH_AND_FULLWIDTH_FORMS")) {
                        return (ch >= 0xff21 && ch <= 0xff3a) || (ch >= 0xff41 && ch <= 0xff5a);
                    }
                    return n.contains(blockKey);
                }
            };
        }

        private static boolean inScript(int ch) {
            int t = Character.getType(ch);
            return t == Character.UPPERCASE_LETTER || t == Character.LOWERCASE_LETTER || t == Character.TITLECASE_LETTER
                    || t == Character.MODIFIER_LETTER || t == Character.OTHER_LETTER || t == Character.LETTER_NUMBER
                    || t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK
                    || t == Character.ENCLOSING_MARK || (ch >= 0x3005 && ch <= 0x3007) || (ch >= 0x3021 && ch <= 0x3029);
        }
    }

    static final class LiteralPred extends CharPredicate {
        final int c;
        final boolean ci;

        LiteralPred(int c, boolean ci) {
            this.c = c;
            this.ci = ci;
        }

        boolean is(int ch) {
            return ch == c || (ci && (Character.toLowerCase(ch) == Character.toLowerCase(c)
                    || Character.toUpperCase(ch) == Character.toUpperCase(c)));
        }

        Integer literalValue() {
            return c;
        }
    }
}
