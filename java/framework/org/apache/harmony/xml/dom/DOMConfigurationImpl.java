package org.apache.harmony.xml.dom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMStringList;

/**
 * Holds the DOM Level 3 configuration parameters. Only the standard boolean parameters and
 * error-handler are recognized; normalizeDocument() merges text nodes regardless of them.
 */
public final class DOMConfigurationImpl implements DOMConfiguration {
    private final Map<String, Object> params = new LinkedHashMap<String, Object>();

    DOMConfigurationImpl() {
        params.put("canonical-form", Boolean.FALSE);
        params.put("cdata-sections", Boolean.TRUE);
        params.put("check-character-normalization", Boolean.FALSE);
        params.put("comments", Boolean.TRUE);
        params.put("datatype-normalization", Boolean.FALSE);
        params.put("element-content-whitespace", Boolean.TRUE);
        params.put("entities", Boolean.TRUE);
        params.put("error-handler", null);
        params.put("infoset", Boolean.TRUE);
        params.put("namespaces", Boolean.TRUE);
        params.put("namespace-declarations", Boolean.TRUE);
        params.put("normalize-characters", Boolean.FALSE);
        params.put("schema-location", null);
        params.put("schema-type", null);
        params.put("split-cdata-sections", Boolean.TRUE);
        params.put("validate", Boolean.FALSE);
        params.put("validate-if-schema", Boolean.FALSE);
        params.put("well-formed", Boolean.TRUE);
    }

    private static String key(String name) {
        return name == null ? null : name.toLowerCase(java.util.Locale.ROOT);
    }

    public boolean canSetParameter(String name, Object value) {
        String k = key(name);
        if (!params.containsKey(k)) {
            return false;
        }
        if ("error-handler".equals(k)) {
            return value == null || value instanceof DOMErrorHandler;
        }
        if (k.startsWith("schema-")) {
            return value == null || value instanceof String;
        }
        return value == null || value instanceof Boolean;
    }

    public void setParameter(String name, Object value) throws DOMException {
        String k = key(name);
        if (!params.containsKey(k)) {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "NOT_FOUND_ERR: parameter " + name);
        }
        if (!canSetParameter(name, value)) {
            throw new DOMException(DOMException.TYPE_MISMATCH_ERR, "TYPE_MISMATCH_ERR: parameter " + name);
        }
        params.put(k, value);
    }

    public Object getParameter(String name) throws DOMException {
        String k = key(name);
        if (!params.containsKey(k)) {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "NOT_FOUND_ERR: parameter " + name);
        }
        return params.get(k);
    }

    public DOMStringList getParameterNames() {
        final List<String> names = new ArrayList<String>(params.keySet());
        return new DOMStringList() {
            public String item(int index) {
                return index < 0 || index >= names.size() ? null : names.get(index);
            }

            public int getLength() {
                return names.size();
            }

            public boolean contains(String str) {
                return names.contains(str);
            }
        };
    }
}
