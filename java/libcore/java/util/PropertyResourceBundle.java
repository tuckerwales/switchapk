package java.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Resources from a properties file. A stream is read as UTF-8, or as ISO-8859-1 if it is not valid UTF-8. */
public class PropertyResourceBundle extends ResourceBundle {
    private final Map<String, Object> lookup;

    public PropertyResourceBundle(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = stream.read(buf)) > 0;) {
            bytes.write(buf, 0, n);
        }
        byte[] data = bytes.toByteArray();
        Reader reader;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data));
            reader = new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            reader = new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.ISO_8859_1);
        }
        lookup = load(reader);
    }

    public PropertyResourceBundle(Reader reader) throws IOException {
        lookup = load(reader);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> load(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        return new HashMap(properties);
    }

    public Object handleGetObject(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return lookup.get(key);
    }

    public Enumeration<String> getKeys() {
        return ResourceBundle.keys(lookup.keySet(), parent);
    }

    protected Set<String> handleKeySet() {
        return lookup.keySet();
    }
}
