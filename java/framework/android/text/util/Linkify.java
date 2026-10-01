package android.text.util;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.util.Patterns;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

/**
 * Turns addresses, phones, emails and web URLs in text into {@link URLSpan}s
 * (AOSP Linkify). Map addresses match a street-number pattern; there is no
 * geocoder.
 */
public class Linkify {
    public static final int WEB_URLS = 1;
    public static final int EMAIL_ADDRESSES = 2;
    public static final int PHONE_NUMBERS = 4;
    public static final int MAP_ADDRESSES = 8;
    public static final int ALL = 15;

    /** Rejects a web match that is the domain half of an email address. */
    public static final MatchFilter sUrlMatchFilter = new MatchFilter() {
        public boolean acceptMatch(CharSequence s, int start, int end) {
            return start == 0 || s.charAt(start - 1) != '@';
        }
    };

    /** Accepts a phone match with at least five digits. */
    public static final MatchFilter sPhoneNumberMatchFilter = new MatchFilter() {
        public boolean acceptMatch(CharSequence s, int start, int end) {
            int digits = 0;
            for (int i = start; i < end; i++) {
                if (Character.isDigit(s.charAt(i))) digits++;
            }
            return digits >= 5;
        }
    };

    /** Keeps digits and a leading plus, which {@code tel:} is then added to. */
    public static final TransformFilter sPhoneNumberTransformFilter = new TransformFilter() {
        public String transformUrl(Matcher match, String url) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < url.length(); i++) {
                char c = url.charAt(i);
                if (c == '+' || (c >= '0' && c <= '9')) out.append(c);
            }
            return out.toString();
        }
    };

    private static final Pattern ADDRESS = Pattern.compile(
            "\\d{1,5}\\s+[A-Za-z0-9.'\\-]+(?:\\s+[A-Za-z0-9.'\\-]+){0,4}\\s+"
                    + "(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Way|Place|Pl)\\b");

    public Linkify() {}

    /** A match is kept when this returns true. */
    public interface MatchFilter {
        boolean acceptMatch(CharSequence s, int start, int end);
    }

    /** Rewrites a matched URL before the scheme is applied. */
    public interface TransformFilter {
        String transformUrl(Matcher match, String url);
    }

    public static final boolean addLinks(Spannable text, int mask) {
        return addLinks(text, mask, null);
    }

    public static final boolean addLinks(Spannable text, int mask, Function<String, URLSpan> urlSpanFactory) {
        if (mask == 0) return false;
        URLSpan[] old = text.getSpans(0, text.length(), URLSpan.class);
        for (int i = old.length - 1; i >= 0; i--) text.removeSpan(old[i]);
        ArrayList<LinkSpec> links = new ArrayList<LinkSpec>();
        if ((mask & WEB_URLS) != 0) {
            gather(links, text, Patterns.WEB_URL, new String[] {"http://", "https://", "rtsp://"},
                    sUrlMatchFilter, null);
        }
        if ((mask & EMAIL_ADDRESSES) != 0) {
            gather(links, text, Patterns.EMAIL_ADDRESS, new String[] {"mailto:"}, null, null);
        }
        if ((mask & PHONE_NUMBERS) != 0) {
            gather(links, text, Patterns.PHONE, new String[] {"tel:"}, sPhoneNumberMatchFilter,
                    sPhoneNumberTransformFilter);
        }
        if ((mask & MAP_ADDRESSES) != 0) gatherMap(links, text);
        prune(links);
        if (links.size() == 0) return false;
        for (int i = 0; i < links.size(); i++) {
            LinkSpec link = links.get(i);
            URLSpan span = urlSpanFactory != null ? urlSpanFactory.apply(link.url) : new URLSpan(link.url);
            text.setSpan(span, link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return true;
    }

    public static final boolean addLinks(TextView text, int mask) {
        if (mask == 0) return false;
        CharSequence t = text.getText();
        if (t instanceof Spannable) {
            if (addLinks((Spannable) t, mask)) {
                addLinkMovementMethod(text);
                return true;
            }
            return false;
        }
        SpannableString s = SpannableString.valueOf(t);
        if (addLinks(s, mask)) {
            addLinkMovementMethod(text);
            text.setText(s);
            return true;
        }
        return false;
    }

    public static final void addLinks(TextView text, Pattern pattern, String scheme) {
        addLinks(text, pattern, scheme, null, null, null);
    }

    public static final void addLinks(TextView text, Pattern pattern, String scheme, MatchFilter matchFilter,
            TransformFilter transformFilter) {
        addLinks(text, pattern, scheme, null, matchFilter, transformFilter);
    }

    public static final void addLinks(TextView text, Pattern pattern, String scheme, String[] schemes,
            MatchFilter matchFilter, TransformFilter transformFilter) {
        SpannableString s = SpannableString.valueOf(text.getText());
        if (addLinks(s, pattern, scheme, schemes, matchFilter, transformFilter)) {
            text.setText(s);
            addLinkMovementMethod(text);
        }
    }

    public static final boolean addLinks(Spannable text, Pattern pattern, String scheme) {
        return addLinks(text, pattern, scheme, null, null, null);
    }

    public static final boolean addLinks(Spannable text, Pattern pattern, String scheme, MatchFilter matchFilter,
            TransformFilter transformFilter) {
        return addLinks(text, pattern, scheme, null, matchFilter, transformFilter);
    }

    public static final boolean addLinks(Spannable text, Pattern pattern, String scheme, String[] schemes,
            MatchFilter matchFilter, TransformFilter transformFilter) {
        return addLinks(text, pattern, scheme, schemes, matchFilter, transformFilter, null);
    }

    public static final boolean addLinks(Spannable text, Pattern pattern, String scheme, String[] schemes,
            MatchFilter matchFilter, TransformFilter transformFilter, Function<String, URLSpan> urlSpanFactory) {
        if (scheme == null) scheme = "";
        if (schemes == null || schemes.length == 0) schemes = new String[] {scheme};
        URLSpan[] old = text.getSpans(0, text.length(), URLSpan.class);
        for (int i = old.length - 1; i >= 0; i--) text.removeSpan(old[i]);
        ArrayList<LinkSpec> links = new ArrayList<LinkSpec>();
        gather(links, text, pattern, schemes, matchFilter, transformFilter);
        if (links.size() == 0) return false;
        for (int i = 0; i < links.size(); i++) {
            LinkSpec link = links.get(i);
            URLSpan span = urlSpanFactory != null ? urlSpanFactory.apply(link.url) : new URLSpan(link.url);
            text.setSpan(span, link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return true;
    }

    private static void addLinkMovementMethod(TextView t) {
        MovementMethod m = t.getMovementMethod();
        if ((m == null || !(m instanceof LinkMovementMethod)) && t.getLinksClickable()) {
            t.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private static void gather(ArrayList<LinkSpec> links, Spannable text, Pattern pattern, String[] schemes,
            MatchFilter matchFilter, TransformFilter transformFilter) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (matchFilter != null && !matchFilter.acceptMatch(text, start, end)) continue;
            LinkSpec spec = new LinkSpec();
            spec.url = makeUrl(m.group(0), schemes, m, transformFilter);
            spec.start = start;
            spec.end = end;
            links.add(spec);
        }
    }

    private static void gatherMap(ArrayList<LinkSpec> links, Spannable text) {
        Matcher m = ADDRESS.matcher(text);
        while (m.find()) {
            LinkSpec spec = new LinkSpec();
            spec.url = "geo:0,0?q=" + android.net.Uri.encode(m.group(0));
            spec.start = m.start();
            spec.end = m.end();
            links.add(spec);
        }
    }

    private static String makeUrl(String url, String[] prefixes, Matcher matcher, TransformFilter filter) {
        if (filter != null) url = filter.transformUrl(matcher, url);
        boolean has = false;
        for (int i = 0; i < prefixes.length; i++) {
            String prefix = prefixes[i];
            if (prefix.length() == 0) continue;
            if (url.regionMatches(true, 0, prefix, 0, prefix.length())) {
                has = true;
                if (!url.regionMatches(false, 0, prefix, 0, prefix.length())) {
                    url = prefix + url.substring(prefix.length());
                }
                break;
            }
        }
        if (!has && prefixes.length > 0) url = prefixes[0] + url;
        return url;
    }

    private static void prune(ArrayList<LinkSpec> links) {
        for (int i = 1; i < links.size(); i++) {
            LinkSpec v = links.get(i);
            int j = i - 1;
            while (j >= 0) {
                LinkSpec u = links.get(j);
                if (u.start < v.start || (u.start == v.start && u.end <= v.end)) break;
                links.set(j + 1, u);
                j--;
            }
            links.set(j + 1, v);
        }
        int i = 0;
        while (i < links.size() - 1) {
            LinkSpec a = links.get(i);
            LinkSpec b = links.get(i + 1);
            int remove = -1;
            if (a.start <= b.start && a.end > b.start) {
                if (b.end <= a.end) remove = i + 1;
                else if (a.end - a.start > b.end - b.start) remove = i + 1;
                else if (a.end - a.start < b.end - b.start) remove = i;
            }
            if (remove != -1) {
                links.remove(remove);
                continue;
            }
            i++;
        }
    }

    private static final class LinkSpec {
        String url;
        int start;
        int end;
    }
}
