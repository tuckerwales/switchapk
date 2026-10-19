package org.w3c.dom;

public interface DOMError {
    short SEVERITY_WARNING = 1;
    short SEVERITY_ERROR = 2;
    short SEVERITY_FATAL_ERROR = 3;
    short getSeverity();
    java.lang.String getMessage();
    java.lang.String getType();
    java.lang.Object getRelatedException();
    java.lang.Object getRelatedData();
    org.w3c.dom.DOMLocator getLocation();
}
