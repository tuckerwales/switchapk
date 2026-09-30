package java.net;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class URLDecoder {
    private URLDecoder() {
    }

    @Deprecated
    public static String decode(String s) {
        return decode(s, Charset.defaultCharset());
    }

    public static String decode(String s, String enc) throws UnsupportedEncodingException {
        try {
            return decode(s, Charset.forName(enc));
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(enc);
        }
    }

    public static String decode(String s, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%') {
                while (i + 2 < s.length() + 0 && s.charAt(i) == '%') {
                    out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 3;
                }
                byte[] b = out.toByteArray();
                sb.append(new String(b, charset));
                out.reset();
            } else {
                sb.append(c == '+' ? ' ' : c);
                i++;
            }
        }
        return sb.toString();
    }
}
