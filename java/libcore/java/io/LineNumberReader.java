package java.io;

public class LineNumberReader extends BufferedReader {
    private int lineNumber = 0;

    public LineNumberReader(Reader in) {
        super(in);
    }

    public LineNumberReader(Reader in, int sz) {
        super(in, sz);
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String readLine() throws IOException {
        String l = super.readLine();
        if (l != null) {
            lineNumber++;
        }
        return l;
    }
}
