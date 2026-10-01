package org.xml.sax;

/**
 * Minimal SAX reader type so {@code android.text.Html.TagHandler} matches the
 * SDK signature. There is no SAX parser; Html passes null for this argument.
 */
public interface XMLReader {
}
