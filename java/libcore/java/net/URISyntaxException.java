package java.net;

public class URISyntaxException extends Exception {
    private final String input;
    private final int index;

    public URISyntaxException(String input, String reason, int index) {
        super(reason);
        this.input = input;
        this.index = index;
    }

    public URISyntaxException(String input, String reason) {
        this(input, reason, -1);
    }

    public String getInput() {
        return input;
    }

    public String getReason() {
        return super.getMessage();
    }

    public int getIndex() {
        return index;
    }

    public String getMessage() {
        return getReason() + (index > -1 ? " at index " + index : "") + ": " + input;
    }
}
