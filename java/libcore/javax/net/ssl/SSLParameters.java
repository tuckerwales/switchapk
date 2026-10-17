package javax.net.ssl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SSLParameters {
    private String[] cipherSuites;
    private String[] protocols;
    private boolean wantClientAuth;
    private boolean needClientAuth;
    private String identificationAlgorithm;
    private List<SNIServerName> sniNames;
    private Collection<SNIMatcher> sniMatchers;
    private boolean preferLocalCipherSuites;
    private boolean enableRetransmissions = true;
    private int maximumPacketSize;
    private String[] applicationProtocols = new String[0];
    private String[] signatureSchemes;
    private String[] namedGroups;

    public SSLParameters() {
    }

    public SSLParameters(String[] cipherSuites) {
        setCipherSuites(cipherSuites);
    }

    public SSLParameters(String[] cipherSuites, String[] protocols) {
        setCipherSuites(cipherSuites);
        setProtocols(protocols);
    }

    private static String[] clone(String[] s) {
        return s == null ? null : s.clone();
    }

    public String[] getCipherSuites() {
        return clone(cipherSuites);
    }

    public void setCipherSuites(String[] cipherSuites) {
        this.cipherSuites = clone(cipherSuites);
    }

    public String[] getProtocols() {
        return clone(protocols);
    }

    public void setProtocols(String[] protocols) {
        this.protocols = clone(protocols);
    }

    public boolean getWantClientAuth() {
        return wantClientAuth;
    }

    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
        this.needClientAuth = false;
    }

    public boolean getNeedClientAuth() {
        return needClientAuth;
    }

    public void setNeedClientAuth(boolean needClientAuth) {
        this.wantClientAuth = false;
        this.needClientAuth = needClientAuth;
    }

    public String getEndpointIdentificationAlgorithm() {
        return identificationAlgorithm;
    }

    public void setEndpointIdentificationAlgorithm(String algorithm) {
        this.identificationAlgorithm = algorithm;
    }

    public final void setServerNames(List<SNIServerName> serverNames) {
        if (serverNames == null) {
            sniNames = null;
            return;
        }
        Map<Integer, SNIServerName> seen = new HashMap<Integer, SNIServerName>();
        for (SNIServerName n : serverNames) {
            if (seen.put(n.getType(), n) != null) {
                throw new IllegalArgumentException("Duplicated server name of type " + n.getType());
            }
        }
        sniNames = Collections.unmodifiableList(new ArrayList<SNIServerName>(serverNames));
    }

    public final List<SNIServerName> getServerNames() {
        return sniNames;
    }

    public final void setSNIMatchers(Collection<SNIMatcher> matchers) {
        if (matchers == null) {
            sniMatchers = null;
            return;
        }
        Map<Integer, SNIMatcher> seen = new HashMap<Integer, SNIMatcher>();
        for (SNIMatcher m : matchers) {
            if (seen.put(m.getType(), m) != null) {
                throw new IllegalArgumentException("Duplicated server name of type " + m.getType());
            }
        }
        sniMatchers = Collections.unmodifiableCollection(new ArrayList<SNIMatcher>(matchers));
    }

    public final Collection<SNIMatcher> getSNIMatchers() {
        return sniMatchers;
    }

    public final void setUseCipherSuitesOrder(boolean honorOrder) {
        this.preferLocalCipherSuites = honorOrder;
    }

    public final boolean getUseCipherSuitesOrder() {
        return preferLocalCipherSuites;
    }

    public void setEnableRetransmissions(boolean enableRetransmissions) {
        this.enableRetransmissions = enableRetransmissions;
    }

    public boolean getEnableRetransmissions() {
        return enableRetransmissions;
    }

    public void setMaximumPacketSize(int maximumPacketSize) {
        if (maximumPacketSize < 0) throw new IllegalArgumentException("The maximum packet size cannot be negative");
        this.maximumPacketSize = maximumPacketSize;
    }

    public int getMaximumPacketSize() {
        return maximumPacketSize;
    }

    public String[] getApplicationProtocols() {
        return applicationProtocols.clone();
    }

    public void setApplicationProtocols(String[] protocols) {
        if (protocols == null) throw new IllegalArgumentException("protocols was null");
        for (String p : protocols) {
            if (p == null || p.isEmpty()) throw new IllegalArgumentException("An element of protocols was null/empty");
        }
        applicationProtocols = protocols.clone();
    }

    public String[] getSignatureSchemes() {
        return clone(signatureSchemes);
    }

    public void setSignatureSchemes(String[] signatureSchemes) {
        this.signatureSchemes = clone(signatureSchemes);
    }

    public String[] getNamedGroups() {
        return clone(namedGroups);
    }

    public void setNamedGroups(String[] namedGroups) {
        this.namedGroups = clone(namedGroups);
    }
}
