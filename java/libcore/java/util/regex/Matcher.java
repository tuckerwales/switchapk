package java.util.regex;

public final class Matcher implements MatchResult {
    private Pattern pattern;
    private CharSequence text;
    int from, to;
    int[] groups;
    int[] tmpStart;
    int[] repCount;
    int[] repStart;
    int acceptMode; // 0 = any end, 1 = must reach 'to'
    int matchEnd;
    boolean hitEnd;
    int lastAppendPos = 0;
    int searchStart;
    private boolean matched;
    private int first = -1, last = 0;
    private int regionStart, regionEnd;

    Matcher(Pattern p, CharSequence text) {
        this.pattern = p;
        this.text = text;
        allocate();
        reset();
    }

    private void allocate() {
        int gc = pattern.groupCount + 1;
        groups = new int[gc * 2];
        tmpStart = new int[gc];
        repCount = new int[pattern.repeatCount + 1];
        repStart = new int[pattern.repeatCount + 1];
    }

    public Pattern pattern() {
        return pattern;
    }

    public Matcher usePattern(Pattern newPattern) {
        pattern = newPattern;
        allocate();
        matched = false;
        return this;
    }

    public Matcher reset() {
        first = -1;
        last = 0;
        lastAppendPos = 0;
        regionStart = 0;
        regionEnd = text.length();
        matched = false;
        clearGroups();
        return this;
    }

    public Matcher reset(CharSequence input) {
        text = input;
        return reset();
    }

    public Matcher region(int start, int end) {
        reset();
        regionStart = start;
        regionEnd = end;
        return this;
    }

    public int regionStart() {
        return regionStart;
    }

    public int regionEnd() {
        return regionEnd;
    }

    private void clearGroups() {
        for (int i = 0; i < groups.length; i++) {
            groups[i] = -1;
        }
    }

    private boolean run(int start, int mode) {
        from = regionStart;
        to = regionEnd;
        acceptMode = mode;
        hitEnd = false;
        clearGroups();
        boolean ok = pattern.root.match(this, start, text);
        if (ok) {
            groups[0] = start;
            groups[1] = matchEnd;
            first = start;
            last = matchEnd;
        }
        matched = ok;
        return ok;
    }

    public boolean matches() {
        return run(regionStart, 1);
    }

    public boolean lookingAt() {
        return run(regionStart, 0);
    }

    public boolean find() {
        int start = last;
        if (first >= 0 && last == first) {
            start++;
        }
        if (first < 0) {
            start = regionStart;
        }
        return findFrom(start);
    }

    public boolean find(int start) {
        if (start < 0 || start > text.length()) {
            throw new IndexOutOfBoundsException("Illegal start index");
        }
        reset();
        return findFrom(start);
    }

    private boolean findFrom(int start) {
        searchStart = start;
        for (int i = start; i <= regionEnd; i++) {
            if (run(i, 0)) {
                return true;
            }
        }
        matched = false;
        first = -1;
        last = regionEnd + 1;
        return false;
    }

    public boolean hitEnd() {
        return hitEnd;
    }

    public boolean requireEnd() {
        return false;
    }

    private void ensureMatch() {
        if (!matched) {
            throw new IllegalStateException("No match found");
        }
    }

    public int start() {
        ensureMatch();
        return groups[0];
    }

    public int start(int group) {
        ensureMatch();
        if (group < 0 || group > groupCount()) {
            throw new IndexOutOfBoundsException("No group " + group);
        }
        return groups[group * 2];
    }

    public int start(String name) {
        return start(groupIndex(name));
    }

    public int end() {
        ensureMatch();
        return groups[1];
    }

    public int end(int group) {
        ensureMatch();
        if (group < 0 || group > groupCount()) {
            throw new IndexOutOfBoundsException("No group " + group);
        }
        return groups[group * 2 + 1];
    }

    public int end(String name) {
        return end(groupIndex(name));
    }

    public String group() {
        return group(0);
    }

    public String group(int group) {
        ensureMatch();
        if (group < 0 || group > groupCount()) {
            throw new IndexOutOfBoundsException("No group " + group);
        }
        if (groups[group * 2] == -1 || groups[group * 2 + 1] == -1) {
            return null;
        }
        return text.subSequence(groups[group * 2], groups[group * 2 + 1]).toString();
    }

    private int groupIndex(String name) {
        Integer g = pattern.namedGroups.get(name);
        if (g == null) {
            throw new IllegalArgumentException("No group with name <" + name + ">");
        }
        return g;
    }

    public String group(String name) {
        return group(groupIndex(name));
    }

    public int groupCount() {
        return pattern.groupCount;
    }

    public MatchResult toMatchResult() {
        final int[] g = groups.clone();
        final String t = text.toString();
        final int gc = groupCount();
        return new MatchResult() {
            public int start() {
                return g[0];
            }

            public int start(int group) {
                return g[group * 2];
            }

            public int end() {
                return g[1];
            }

            public int end(int group) {
                return g[group * 2 + 1];
            }

            public String group() {
                return group(0);
            }

            public String group(int group) {
                return g[group * 2] < 0 ? null : t.substring(g[group * 2], g[group * 2 + 1]);
            }

            public int groupCount() {
                return gc;
            }
        };
    }

    public java.util.stream.Stream<MatchResult> results() {
        java.util.ArrayList<MatchResult> l = new java.util.ArrayList<MatchResult>();
        reset();
        while (find()) {
            l.add(toMatchResult());
        }
        return l.stream();
    }

    public static String quoteReplacement(String s) {
        if ((s.indexOf('\\') == -1) && (s.indexOf('$') == -1)) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '$') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void appendExpanded(StringBuilder sb, String replacement) {
        int cursor = 0;
        while (cursor < replacement.length()) {
            char nextChar = replacement.charAt(cursor);
            if (nextChar == '\\') {
                cursor++;
                if (cursor == replacement.length()) {
                    throw new IllegalArgumentException("character to be escaped is missing");
                }
                sb.append(replacement.charAt(cursor));
                cursor++;
            } else if (nextChar == '$') {
                cursor++;
                if (cursor == replacement.length()) {
                    throw new IllegalArgumentException("Illegal group reference: group index is missing");
                }
                int refNum;
                if (replacement.charAt(cursor) == '{') {
                    int end = replacement.indexOf('}', cursor);
                    String name = replacement.substring(cursor + 1, end);
                    cursor = end + 1;
                    refNum = groupIndex(name);
                } else {
                    refNum = replacement.charAt(cursor) - '0';
                    if (refNum < 0 || refNum > 9) {
                        throw new IllegalArgumentException("Illegal group reference");
                    }
                    cursor++;
                    while (cursor < replacement.length()) {
                        int nextDigit = replacement.charAt(cursor) - '0';
                        if (nextDigit < 0 || nextDigit > 9) {
                            break;
                        }
                        int newRefNum = refNum * 10 + nextDigit;
                        if (groupCount() < newRefNum) {
                            break;
                        }
                        refNum = newRefNum;
                        cursor++;
                    }
                }
                if (refNum > groupCount()) {
                    throw new IndexOutOfBoundsException("No group " + refNum);
                }
                String g = group(refNum);
                if (g != null) {
                    sb.append(g);
                }
            } else {
                sb.append(nextChar);
                cursor++;
            }
        }
    }

    public Matcher appendReplacement(StringBuffer sb, String replacement) {
        StringBuilder b = new StringBuilder();
        appendReplacement(b, replacement);
        sb.append(b);
        return this;
    }

    public Matcher appendReplacement(StringBuilder sb, String replacement) {
        ensureMatch();
        sb.append(text, lastAppendPos, start());
        appendExpanded(sb, replacement);
        lastAppendPos = end();
        return this;
    }

    public StringBuffer appendTail(StringBuffer sb) {
        sb.append(text, lastAppendPos, text.length());
        return sb;
    }

    public StringBuilder appendTail(StringBuilder sb) {
        sb.append(text, lastAppendPos, text.length());
        return sb;
    }

    public String replaceAll(String replacement) {
        reset();
        boolean result = find();
        if (result) {
            StringBuilder sb = new StringBuilder();
            do {
                appendReplacement(sb, replacement);
                result = find();
            } while (result);
            appendTail(sb);
            return sb.toString();
        }
        return text.toString();
    }

    public String replaceAll(java.util.function.Function<MatchResult, String> replacer) {
        reset();
        StringBuilder sb = new StringBuilder();
        while (find()) {
            appendReplacement(sb, quoteReplacement(replacer.apply(this)));
        }
        appendTail(sb);
        return sb.toString();
    }

    public String replaceFirst(String replacement) {
        reset();
        if (!find()) {
            return text.toString();
        }
        StringBuilder sb = new StringBuilder();
        appendReplacement(sb, replacement);
        appendTail(sb);
        return sb.toString();
    }

    public String toString() {
        return "java.util.regex.Matcher[pattern=" + pattern + " region=" + regionStart + "," + regionEnd + " lastmatch="
                + (matched ? group() : "") + "]";
    }
}
