package java.net;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class URLEncoder {
    private URLEncoder() {
    }

    @Deprecated
    public static String encode(String s) {
        return encode(s, Charset.defaultCharset());
    }

    public static String encode(String s, String enc) throws UnsupportedEncodingException {
        try {
            return encode(s, Charset.forName(enc));
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(enc);
        }
    }

    public static String encode(String s, Charset charset) {
        StringBuilder out = new StringBuilder(s.length());
        byte[] bytes = s.getBytes(charset);
        for (byte b : bytes) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-'
                    || c == '*' || c == '_') {
                out.append((char) c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }
}
