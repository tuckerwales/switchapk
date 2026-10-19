package javax.xml.transform.stream;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import javax.xml.transform.Result;

public class StreamResult implements Result {
    public static final String FEATURE = "http://javax.xml.transform.stream.StreamResult/feature";

    private String systemId;
    private OutputStream outputStream;
    private Writer writer;

    public StreamResult() {
    }

    public StreamResult(OutputStream outputStream) {
        setOutputStream(outputStream);
    }

    public StreamResult(Writer writer) {
        setWriter(writer);
    }

    public StreamResult(String systemId) {
        this.systemId = systemId;
    }

    public StreamResult(File f) {
        setSystemId(f);
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setWriter(Writer writer) {
        this.writer = writer;
    }

    public Writer getWriter() {
        return writer;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public void setSystemId(File f) {
        this.systemId = f.toURI().toASCIIString();
    }

    public String getSystemId() {
        return systemId;
    }
}
