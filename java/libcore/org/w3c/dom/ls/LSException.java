package org.w3c.dom.ls;

public class LSException extends RuntimeException {
    public short code;
    public static final short PARSE_ERR = 81;
    public static final short SERIALIZE_ERR = 82;

    public LSException(short code, String message) {
        super(message);
        this.code = code;
    }
}
