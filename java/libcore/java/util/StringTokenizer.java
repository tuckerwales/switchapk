package java.util;

public class StringTokenizer implements Enumeration<Object> {
    private int currentPosition;
    private final int maxPosition;
    private final String str;
    private String delimiters;
    private final boolean retDelims;

    public StringTokenizer(String str, String delim, boolean returnDelims) {
        currentPosition = 0;
        this.str = str;
        maxPosition = str.length();
        delimiters = delim;
        retDelims = returnDelims;
    }

    public StringTokenizer(String str, String delim) {
        this(str, delim, false);
    }

    public StringTokenizer(String str) {
        this(str, " \t\n\r\f", false);
    }

    private int skipDelimiters(int startPos) {
        int position = startPos;
        while (!retDelims && position < maxPosition) {
            if (delimiters.indexOf(str.charAt(position)) < 0) {
                break;
            }
            position++;
        }
        return position;
    }

    private int scanToken(int startPos) {
        int position = startPos;
        while (position < maxPosition) {
            if (delimiters.indexOf(str.charAt(position)) >= 0) {
                break;
            }
            position++;
        }
        if (retDelims && startPos == position && position < maxPosition) {
            position++;
        }
        return position;
    }

    public boolean hasMoreTokens() {
        return skipDelimiters(currentPosition) < maxPosition;
    }

    public String nextToken() {
        currentPosition = skipDelimiters(currentPosition);
        if (currentPosition >= maxPosition) {
            throw new NoSuchElementException();
        }
        int start = currentPosition;
        currentPosition = scanToken(currentPosition);
        return str.substring(start, currentPosition);
    }

    public String nextToken(String delim) {
        delimiters = delim;
        return nextToken();
    }

    public boolean hasMoreElements() {
        return hasMoreTokens();
    }

    public Object nextElement() {
        return nextToken();
    }

    public int countTokens() {
        int count = 0;
        int currpos = currentPosition;
        while (currpos < maxPosition) {
            currpos = skipDelimiters(currpos);
            if (currpos >= maxPosition) {
                break;
            }
            currpos = scanToken(currpos);
            count++;
        }
        return count;
    }
}
