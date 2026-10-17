package javax.net.ssl;

import java.util.EventObject;

public class SSLSessionBindingEvent extends EventObject {
    private final String name;

    public SSLSessionBindingEvent(SSLSession session, String name) {
        super(session);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public SSLSession getSession() {
        return (SSLSession) getSource();
    }
}
