package android.webkit;

public class WebMessage {
    private final String mData;
    private final WebMessagePort[] mPorts;

    public WebMessage(String data) {
        mData = data;
        mPorts = null;
    }

    public WebMessage(String data, WebMessagePort[] ports) {
        mData = data;
        mPorts = ports;
    }

    public String getData() {
        return mData;
    }

    public WebMessagePort[] getPorts() {
        return mPorts;
    }
}
