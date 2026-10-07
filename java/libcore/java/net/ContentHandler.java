package java.net;

import java.io.IOException;

public abstract class ContentHandler {
    public ContentHandler() {
    }

    public abstract Object getContent(URLConnection urlc) throws IOException;

    public Object getContent(URLConnection urlc, Class[] classes) throws IOException {
        Object obj = getContent(urlc);
        for (Class c : classes) {
            if (c.isInstance(obj)) {
                return obj;
            }
        }
        return null;
    }
}
