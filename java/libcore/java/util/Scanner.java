package java.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormatSymbols;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Breaks text into tokens separated by a delimiter pattern (whitespace by default) and parses primitives from them,
 * as java.util.Scanner. Input is read on demand into a buffer; an operation reads more until its answer cannot change
 * (a delimiter or line end that does not touch the buffer's end) or the source is exhausted. Numbers accept the
 * locale's grouping separator and decimal separator. An IOException from the source ends the input and is kept for
 * ioException().
 */
public final class Scanner implements Iterator<String>, Closeable {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\p{javaWhitespace}+");
    private static final Pattern FIND_ANY_PATTERN = Pattern.compile("(?s).*");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);
    private static final int BUFFER_SIZE = 1024;

    private Readable source;
    private final StringBuilder buf = new StringBuilder();
    private int position;
    private boolean sourceClosed;
    private boolean closed;
    private IOException lastException;
    private Pattern delimPattern = WHITESPACE_PATTERN;
    private Locale locale;
    private char groupSeparator = ',';
    private char decimalSeparator = '.';
    private int defaultRadix = 10;
    private MatchResult lastMatch;
    private boolean matchValid;
    /** A token already found by hasNext: its start, end, and the position after it. */
    private int cachedStart = -1, cachedEnd = -1;

    public Scanner(Readable source) {
        this.source = Objects.requireNonNull(source, "source");
        useLocale(Locale.getDefault(Locale.Category.FORMAT));
    }

    public Scanner(InputStream source) {
        this(new InputStreamReader(source));
    }

    public Scanner(InputStream source, String charsetName) {
        this(new InputStreamReader(Objects.requireNonNull(source, "source"), toCharset(charsetName)));
    }

    public Scanner(InputStream source, Charset charset) {
        this(new InputStreamReader(Objects.requireNonNull(source, "source"), Objects.requireNonNull(charset, "charset")));
    }

    public Scanner(File source) throws FileNotFoundException {
        this(new FileInputStream(source));
    }

    public Scanner(File source, String charsetName) throws FileNotFoundException {
        this(new FileInputStream(Objects.requireNonNull(source)), toCharset(charsetName));
    }

    public Scanner(File source, Charset charset) throws IOException {
        this(new FileInputStream(Objects.requireNonNull(source)), charset);
    }

    public Scanner(Path source) throws IOException {
        this(Files.newInputStream(source));
    }

    public Scanner(Path source, String charsetName) throws IOException {
        this(Files.newInputStream(Objects.requireNonNull(source)), toCharset(charsetName));
    }

    public Scanner(Path source, Charset charset) throws IOException {
        this(Files.newInputStream(Objects.requireNonNull(source)), charset);
    }

    public Scanner(String source) {
        this(new StringReader(source));
    }

    public Scanner(ReadableByteChannel source) {
        this(Channels.newInputStream(Objects.requireNonNull(source, "source")));
    }

    public Scanner(ReadableByteChannel source, String charsetName) {
        this(Channels.newInputStream(Objects.requireNonNull(source, "source")), toCharset(charsetName));
    }

    public Scanner(ReadableByteChannel source, Charset charset) {
        this(Channels.newInputStream(Objects.requireNonNull(source, "source")), charset);
    }

    private static Charset toCharset(String csn) {
        Objects.requireNonNull(csn, "charsetName");
        try {
            return Charset.forName(csn);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // ---- input

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Scanner closed");
        }
    }

    /** Reads one more chunk; false once the source is exhausted. */
    private boolean readMore() {
        if (sourceClosed) {
            return false;
        }
        // drop consumed input so the buffer does not grow without bound
        if (position > BUFFER_SIZE * 4 && position > buf.length() / 2) {
            buf.delete(0, position);
            if (cachedStart >= 0) {
                cachedStart -= position;
                cachedEnd -= position;
            }
            position = 0;
        }
        CharBuffer cb = CharBuffer.allocate(BUFFER_SIZE);
        int n;
        try {
            n = source.read(cb);
        } catch (IOException ioe) {
            lastException = ioe;
            n = -1;
        }
        if (n == -1) {
            sourceClosed = true;
            return false;
        }
        cb.flip();
        buf.append(cb);
        return true;
    }

    // ---- tokens

    /** Finds the next token without consuming it: sets cachedStart/cachedEnd, false if there is none. */
    private boolean findToken() {
        if (cachedStart >= 0) {
            return true;
        }
        for (;;) {
            // skip leading delimiters
            int start = position;
            Matcher m = delimPattern.matcher(buf);
            m.region(position, buf.length());
            if (m.lookingAt()) {
                if (m.end() == buf.length() && !sourceClosed) {
                    readMore(); // the delimiter run may continue; once the source ends this settles
                    continue;
                }
                start = m.end();
            }
            if (start == buf.length()) {
                if (readMore()) {
                    continue;
                }
                return false;
            }
            // the token runs to the next delimiter
            m = delimPattern.matcher(buf);
            m.region(start, buf.length());
            int end = -1;
            while (m.find()) {
                if (m.end() > m.start() || m.start() > start) {
                    end = m.start();
                    if (m.end() == buf.length() && !sourceClosed) {
                        end = -2; // the delimiter may continue
                    }
                    break;
                }
            }
            if (end == -2 || (end == -1 && !sourceClosed)) {
                if (readMore()) {
                    continue;
                }
                end = end == -2 ? m.start() : buf.length();
            }
            if (end == -1) {
                end = buf.length();
            }
            cachedStart = start;
            cachedEnd = end;
            return true;
        }
    }

    private String peekToken() {
        return buf.substring(cachedStart, cachedEnd);
    }

    private String consumeToken() {
        String token = peekToken();
        position = cachedEnd;
        Matcher m = FIND_ANY_PATTERN.matcher(token);
        m.matches();
        lastMatch = m.toMatchResult();
        matchValid = true;
        cachedStart = cachedEnd = -1;
        return token;
    }

    private void clearCache() {
        cachedStart = cachedEnd = -1;
    }

    public boolean hasNext() {
        ensureOpen();
        return findToken();
    }

    public String next() {
        ensureOpen();
        if (!findToken()) {
            matchValid = false;
            throw new NoSuchElementException();
        }
        return consumeToken();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public boolean hasNext(String pattern) {
        return hasNext(Pattern.compile(pattern));
    }

    public String next(String pattern) {
        return next(Pattern.compile(pattern));
    }

    public boolean hasNext(Pattern pattern) {
        ensureOpen();
        Objects.requireNonNull(pattern);
        return findToken() && pattern.matcher(peekToken()).matches();
    }

    public String next(Pattern pattern) {
        ensureOpen();
        Objects.requireNonNull(pattern);
        if (!findToken()) {
            matchValid = false;
            throw new NoSuchElementException();
        }
        Matcher m = pattern.matcher(peekToken());
        if (!m.matches()) {
            matchValid = false;
            throw new InputMismatchException();
        }
        String token = consumeToken();
        lastMatch = m.toMatchResult();
        return token;
    }

    // ---- lines

    /** End of the current line's text (before its terminator) and of its terminator, or null if no input is left. */
    private int[] findLine() {
        for (;;) {
            for (int i = position; i < buf.length(); i++) {
                char c = buf.charAt(i);
                if (c == '\n' || c == '\u2028' || c == '\u2029' || c == '\u0085') {
                    return new int[] {i, i + 1};
                }
                if (c == '\r') {
                    if (i + 1 == buf.length() && readMore()) {
                        i--; // look at this '\r' again with the next character available
                        continue;
                    }
                    int end = (i + 1 < buf.length() && buf.charAt(i + 1) == '\n') ? i + 2 : i + 1;
                    return new int[] {i, end};
                }
            }
            if (!readMore()) {
                return position < buf.length() ? new int[] {buf.length(), buf.length()} : null;
            }
        }
    }

    public boolean hasNextLine() {
        ensureOpen();
        return findLine() != null;
    }

    public String nextLine() {
        ensureOpen();
        clearCache();
        int[] line = findLine();
        if (line == null) {
            matchValid = false;
            throw new NoSuchElementException("No line found");
        }
        String all = buf.substring(position, line[1]);
        String text = buf.substring(position, line[0]);
        Matcher m = FIND_ANY_PATTERN.matcher(all);
        m.matches();
        lastMatch = m.toMatchResult();
        matchValid = true;
        position = line[1];
        return text;
    }

    // ---- finding and skipping

    public String findInLine(String pattern) {
        return findInLine(Pattern.compile(pattern));
    }

    public String findInLine(Pattern pattern) {
        ensureOpen();
        Objects.requireNonNull(pattern);
        clearCache();
        int[] line = findLine();
        if (line == null) {
            return null;
        }
        Matcher m = pattern.matcher(buf);
        m.region(position, line[0]);
        if (m.find()) {
            lastMatch = m.toMatchResult();
            matchValid = true;
            position = m.end();
            return m.group();
        }
        matchValid = false;
        return null;
    }

    public String findWithinHorizon(String pattern, int horizon) {
        return findWithinHorizon(Pattern.compile(pattern), horizon);
    }

    public String findWithinHorizon(Pattern pattern, int horizon) {
        ensureOpen();
        Objects.requireNonNull(pattern);
        if (horizon < 0) {
            throw new IllegalArgumentException("horizon < 0");
        }
        clearCache();
        for (;;) {
            int limit = horizon == 0 ? buf.length() : Math.min(buf.length(), position + horizon);
            Matcher m = pattern.matcher(buf);
            m.region(position, limit);
            boolean found = m.find();
            boolean canGrow = !sourceClosed && (horizon == 0 || buf.length() - position < horizon);
            if (found && (m.end() < limit || !canGrow)) {
                lastMatch = m.toMatchResult();
                matchValid = true;
                position = m.end();
                return m.group();
            }
            if (canGrow && readMore()) {
                continue;
            }
            if (found) {
                lastMatch = m.toMatchResult();
                matchValid = true;
                position = m.end();
                return m.group();
            }
            matchValid = false;
            return null;
        }
    }

    public Scanner skip(Pattern pattern) {
        ensureOpen();
        Objects.requireNonNull(pattern);
        clearCache();
        for (;;) {
            Matcher m = pattern.matcher(buf);
            m.region(position, buf.length());
            boolean ok = m.lookingAt();
            if ((ok && m.end() == buf.length() || !ok && (m.hitEnd() || position == buf.length())) && readMore()) {
                continue;
            }
            if (!ok) {
                matchValid = false;
                throw new NoSuchElementException();
            }
            lastMatch = m.toMatchResult();
            matchValid = true;
            position = m.end();
            return this;
        }
    }

    public Scanner skip(String pattern) {
        return skip(Pattern.compile(pattern));
    }

    // ---- configuration

    public void close() {
        if (closed) {
            return;
        }
        if (source instanceof Closeable) {
            try {
                ((Closeable) source).close();
            } catch (IOException ioe) {
                lastException = ioe;
            }
        }
        sourceClosed = true;
        source = null;
        closed = true;
    }

    public IOException ioException() {
        return lastException;
    }

    public Pattern delimiter() {
        return delimPattern;
    }

    public Scanner useDelimiter(Pattern pattern) {
        delimPattern = Objects.requireNonNull(pattern);
        clearCache();
        return this;
    }

    public Scanner useDelimiter(String pattern) {
        return useDelimiter(Pattern.compile(pattern));
    }

    public Locale locale() {
        return locale;
    }

    public Scanner useLocale(Locale locale) {
        if (locale.equals(this.locale)) {
            return this;
        }
        this.locale = locale;
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(locale);
        groupSeparator = dfs.getGroupingSeparator();
        decimalSeparator = dfs.getDecimalSeparator();
        clearCache();
        return this;
    }

    public int radix() {
        return defaultRadix;
    }

    public Scanner useRadix(int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw new IllegalArgumentException("radix:" + radix);
        }
        defaultRadix = radix;
        return this;
    }

    public MatchResult match() {
        if (!matchValid) {
            throw new IllegalStateException("No match result available");
        }
        return lastMatch;
    }

    public Scanner reset() {
        delimPattern = WHITESPACE_PATTERN;
        useLocale(Locale.getDefault(Locale.Category.FORMAT));
        defaultRadix = 10;
        clearCache();
        return this;
    }

    public String toString() {
        return "java.util.Scanner[delimiters=" + delimPattern + "][position=" + position + "][match valid="
                + matchValid + "][need input=false][source closed=" + sourceClosed + "][skipped=false][group separator="
                + Pattern.quote(String.valueOf(groupSeparator)) + "][decimal separator="
                + Pattern.quote(String.valueOf(decimalSeparator)) + "][positive prefix=][negative prefix=\\Q-\\E]"
                + "[positive suffix=][negative suffix=][NaN string=\\QNaN\\E][infinity string=\\Q\u221e\\E]";
    }

    // ---- numbers

    /** The integer text with grouping removed, or null if the token is not an integer in this radix. */
    private String integerText(String token, int radix) {
        int i = 0, n = token.length();
        StringBuilder sb = new StringBuilder(n);
        if (i < n && (token.charAt(i) == '-' || token.charAt(i) == '+')) {
            if (token.charAt(i) == '-') {
                sb.append('-');
            }
            i++;
        }
        int digitsStart = i;
        int groupLen = -1; // digits since the last separator, -1 before any separator
        boolean grouped = false;
        int firstGroup = 0;
        for (; i < n; i++) {
            char c = token.charAt(i);
            if (c == groupSeparator && radix == 10 && i > digitsStart) {
                if (grouped && groupLen != 3) {
                    return null;
                }
                if (!grouped) {
                    firstGroup = i - digitsStart;
                    if (firstGroup > 3) {
                        return null;
                    }
                }
                grouped = true;
                groupLen = 0;
                continue;
            }
            if (Character.digit(c, radix) < 0) {
                return null;
            }
            sb.append(c);
            if (grouped) {
                groupLen++;
            }
        }
        if (i == digitsStart || (grouped && groupLen != 3)) {
            return null;
        }
        return sb.toString();
    }

    private long parseInteger(int radix, long min, long max, boolean consume) {
        ensureOpen();
        if (!findToken()) {
            throw new NoSuchElementException();
        }
        String token = peekToken();
        String text = integerText(token, radix);
        if (text == null) {
            throw new InputMismatchException("For input string: \"" + token + "\"");
        }
        long v;
        try {
            v = Long.parseLong(text, radix);
        } catch (NumberFormatException nfe) {
            throw new InputMismatchException(nfe.getMessage());
        }
        if (v < min || v > max) {
            throw new InputMismatchException("Value out of range. Value:\"" + token + "\" Radix:" + radix);
        }
        if (consume) {
            consumeToken();
        }
        return v;
    }

    private boolean hasInteger(int radix, long min, long max) {
        try {
            parseInteger(radix, min, max, false);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public boolean hasNextBoolean() {
        return hasNext(BOOLEAN_PATTERN);
    }

    public boolean nextBoolean() {
        return Boolean.parseBoolean(next(BOOLEAN_PATTERN));
    }

    public boolean hasNextByte() {
        return hasNextByte(defaultRadix);
    }

    public boolean hasNextByte(int radix) {
        return hasInteger(radix, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    public byte nextByte() {
        return nextByte(defaultRadix);
    }

    public byte nextByte(int radix) {
        return (byte) parseInteger(radix, Byte.MIN_VALUE, Byte.MAX_VALUE, true);
    }

    public boolean hasNextShort() {
        return hasNextShort(defaultRadix);
    }

    public boolean hasNextShort(int radix) {
        return hasInteger(radix, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    public short nextShort() {
        return nextShort(defaultRadix);
    }

    public short nextShort(int radix) {
        return (short) parseInteger(radix, Short.MIN_VALUE, Short.MAX_VALUE, true);
    }

    public boolean hasNextInt() {
        return hasNextInt(defaultRadix);
    }

    public boolean hasNextInt(int radix) {
        return hasInteger(radix, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public int nextInt() {
        return nextInt(defaultRadix);
    }

    public int nextInt(int radix) {
        return (int) parseInteger(radix, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
    }

    public boolean hasNextLong() {
        return hasNextLong(defaultRadix);
    }

    public boolean hasNextLong(int radix) {
        return hasInteger(radix, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public long nextLong() {
        return nextLong(defaultRadix);
    }

    public long nextLong(int radix) {
        return parseInteger(radix, Long.MIN_VALUE, Long.MAX_VALUE, true);
    }

    /** Decimal text in the locale's notation, rewritten for Double.parseDouble / BigDecimal; null if not one. */
    private String decimalText(String token) {
        String t = token;
        String sign = "";
        if (t.startsWith("-") || t.startsWith("+")) {
            sign = t.startsWith("-") ? "-" : "";
            t = t.substring(1);
        }
        if (t.equals("NaN")) {
            return "NaN";
        }
        if (t.equals("Infinity") || t.equals("\u221e")) {
            return sign + "Infinity";
        }
        int e = t.indexOf('e');
        if (e < 0) {
            e = t.indexOf('E');
        }
        String mantissa = e >= 0 ? t.substring(0, e) : t;
        String exponent = e >= 0 ? t.substring(e + 1) : null;
        int dot = mantissa.indexOf(decimalSeparator);
        String intPart = dot >= 0 ? mantissa.substring(0, dot) : mantissa;
        String fracPart = dot >= 0 ? mantissa.substring(dot + 1) : "";
        String digits = intPart.isEmpty() ? "" : integerText(intPart, 10);
        if (digits == null || (intPart.isEmpty() && fracPart.isEmpty())) {
            return null;
        }
        for (int i = 0; i < fracPart.length(); i++) {
            if (!Character.isDigit(fracPart.charAt(i))) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder(sign).append(digits.isEmpty() ? "0" : digits);
        if (!fracPart.isEmpty()) {
            sb.append('.').append(fracPart);
        }
        if (exponent != null) {
            if (!exponent.matches("[+-]?[0-9]+")) {
                return null;
            }
            sb.append('E').append(exponent);
        }
        return sb.toString();
    }

    private String decimalToken(boolean consume) {
        ensureOpen();
        if (!findToken()) {
            throw new NoSuchElementException();
        }
        String token = peekToken();
        String text = decimalText(token);
        if (text == null) {
            throw new InputMismatchException("For input string: \"" + token + "\"");
        }
        if (consume) {
            consumeToken();
        }
        return text;
    }

    private boolean hasDecimal() {
        try {
            decimalToken(false);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public boolean hasNextFloat() {
        return hasDecimal();
    }

    public float nextFloat() {
        return Float.parseFloat(decimalToken(true));
    }

    public boolean hasNextDouble() {
        return hasDecimal();
    }

    public double nextDouble() {
        return Double.parseDouble(decimalToken(true));
    }

    public boolean hasNextBigInteger() {
        return hasNextBigInteger(defaultRadix);
    }

    public boolean hasNextBigInteger(int radix) {
        ensureOpen();
        return findToken() && integerText(peekToken(), radix) != null;
    }

    public BigInteger nextBigInteger() {
        return nextBigInteger(defaultRadix);
    }

    public BigInteger nextBigInteger(int radix) {
        ensureOpen();
        if (!findToken()) {
            throw new NoSuchElementException();
        }
        String text = integerText(peekToken(), radix);
        if (text == null) {
            throw new InputMismatchException("For input string: \"" + peekToken() + "\"");
        }
        consumeToken();
        return new BigInteger(text, radix);
    }

    public boolean hasNextBigDecimal() {
        ensureOpen();
        if (!findToken()) {
            return false;
        }
        String text = decimalText(peekToken());
        return text != null && !text.equals("NaN") && !text.endsWith("Infinity");
    }

    public BigDecimal nextBigDecimal() {
        if (!hasNextBigDecimal()) {
            if (!findToken()) {
                throw new NoSuchElementException();
            }
            throw new InputMismatchException("For input string: \"" + peekToken() + "\"");
        }
        return new BigDecimal(decimalToken(true));
    }

    // ---- streams

    private Runnable closer() {
        return new Runnable() {
            public void run() {
                close();
            }
        };
    }

    public Stream<String> tokens() {
        ensureOpen();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this,
                Spliterator.ORDERED | Spliterator.NONNULL), false).onClose(closer());
    }

    public Stream<MatchResult> findAll(String patString) {
        return findAll(Pattern.compile(patString));
    }

    public Stream<MatchResult> findAll(final Pattern pattern) {
        Objects.requireNonNull(pattern);
        ensureOpen();
        Iterator<MatchResult> it = new Iterator<MatchResult>() {
            MatchResult nextResult;
            boolean done;

            public boolean hasNext() {
                if (nextResult == null && !done) {
                    if (findWithinHorizon(pattern, 0) != null) {
                        nextResult = match();
                        if (nextResult.end() == nextResult.start()) {
                            // an empty match: step past one character so the stream ends
                            if (position < buf.length() || readMore()) {
                                position++;
                            } else {
                                done = true;
                            }
                        }
                    } else {
                        done = true;
                    }
                }
                return nextResult != null;
            }

            public MatchResult next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                MatchResult r = nextResult;
                nextResult = null;
                return r;
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it,
                Spliterator.ORDERED | Spliterator.NONNULL), false).onClose(closer());
    }
}
