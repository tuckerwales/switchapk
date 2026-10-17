package javax.net.ssl;

import java.util.EventListener;

public interface SSLSessionBindingListener extends EventListener {
    void valueBound(SSLSessionBindingEvent event);

    void valueUnbound(SSLSessionBindingEvent event);
}
