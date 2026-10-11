package java.security.cert;

import java.io.ByteArrayInputStream;
import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.util.Iterator;
import java.util.List;

public abstract class CertPath implements java.io.Serializable {
    private static final long serialVersionUID = 6068470306649138683L;

    private final transient String type;

    protected CertPath(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public abstract Iterator<String> getEncodings();

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CertPath)) return false;
        CertPath o = (CertPath) other;
        return o.getType().equals(type) && getCertificates().equals(o.getCertificates());
    }

    public int hashCode() {
        return 31 * type.hashCode() + getCertificates().hashCode();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        List<? extends Certificate> certs = getCertificates();
        sb.append("\n").append(type).append(" Cert Path: length = ").append(certs.size()).append(".\n");
        sb.append("[\n");
        int i = 1;
        for (Certificate c : certs) {
            sb.append("=========================================================Certificate ").append(i).append(" start.\n");
            sb.append(c.toString());
            sb.append("\n=========================================================Certificate ").append(i).append(" end.\n\n\n");
            i++;
        }
        sb.append("\n]");
        return sb.toString();
    }

    public abstract byte[] getEncoded() throws CertificateEncodingException;

    public abstract byte[] getEncoded(String encoding) throws CertificateEncodingException;

    public abstract List<? extends Certificate> getCertificates();

    protected Object writeReplace() throws ObjectStreamException {
        try {
            return new CertPathRep(type, getEncoded());
        } catch (CertificateException ce) {
            NotSerializableException nse = new NotSerializableException("java.security.cert.CertPath: " + type);
            nse.initCause(ce);
            throw nse;
        }
    }

    protected static class CertPathRep implements java.io.Serializable {
        private static final long serialVersionUID = 3015633072427920915L;

        private final String type;
        private final byte[] data;

        protected CertPathRep(String type, byte[] data) {
            this.type = type;
            this.data = data;
        }

        protected Object readResolve() throws ObjectStreamException {
            try {
                return CertificateFactory.getInstance(type).generateCertPath(new ByteArrayInputStream(data));
            } catch (CertificateException ce) {
                NotSerializableException nse = new NotSerializableException("java.security.cert.CertPath: " + type);
                nse.initCause(ce);
                throw nse;
            }
        }
    }
}
