package android.text;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.XMLReader;

/**
 * HTML in and out of spanned text (AOSP Html, without TagSoup). Block tags
 * use the same separator flags: legacy mode inserts a blank line, compact
 * mode inserts one newline.
 */
public class Html {
    /** Separate blocks with a blank line. Pre-N behaviour. */
    public static final int FROM_HTML_MODE_LEGACY = 0;
    /** Separate blocks with a single newline. */
    public static final int FROM_HTML_MODE_COMPACT = 63;
    /** Prefer CSS named colors over {@link Color} for a few names. */
    public static final int FROM_HTML_OPTION_USE_CSS_COLORS = 256;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 1;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_HEADING = 2;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM = 4;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST = 8;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_DIV = 16;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE = 32;
    /** Group consecutive lines into one paragraph element. */
    public static final int TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0;
    /** One paragraph or list element per line. */
    public static final int TO_HTML_PARAGRAPH_LINES_INDIVIDUAL = 1;

    private Html() {}

    /** Supplies a drawable for an {@code img} src. */
    public interface ImageGetter {
        Drawable getDrawable(String source);
    }

    /**
     * Notified for tags this parser does not handle. {@code xmlReader} is
     * null: there is no SAX parser behind fromHtml.
     */
    public interface TagHandler {
        void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader);
    }

    public static Spanned fromHtml(String source) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY, null, null);
    }

    public static Spanned fromHtml(String source, int flags) {
        return fromHtml(source, flags, null, null);
    }

    public static Spanned fromHtml(String source, ImageGetter imageGetter, TagHandler tagHandler) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY, imageGetter, tagHandler);
    }

    public static Spanned fromHtml(String source, int flags, ImageGetter imageGetter, TagHandler tagHandler) {
        return new Converter(source, flags, imageGetter, tagHandler).convert();
    }

    public static String toHtml(Spanned text) {
        return toHtml(text, TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
    }

    public static String toHtml(Spanned text, int option) {
        StringBuilder out = new StringBuilder();
        withinHtml(out, text, option);
        return out.toString();
    }

    /** Escapes HTML metacharacters. Runs of spaces become {@code &nbsp;} plus one space. */
    public static String escapeHtml(CharSequence text) {
        StringBuilder out = new StringBuilder();
        withinStyle(out, text, 0, text.length());
        return out.toString();
    }

    private static void withinHtml(StringBuilder out, Spanned text, int option) {
        if ((option & TO_HTML_PARAGRAPH_LINES_INDIVIDUAL) == 0) {
            encodeTextAlignmentByDiv(out, text, option);
            return;
        }
        withinDiv(out, text, 0, text.length(), option);
    }

    private static void encodeTextAlignmentByDiv(StringBuilder out, Spanned text, int option) {
        int len = text.length();
        int next;
        for (int i = 0; i < len; i = next) {
            next = text.nextSpanTransition(i, len, ParagraphStyle.class);
            ParagraphStyle[] style = text.getSpans(i, next, ParagraphStyle.class);
            String align = null;
            for (int j = 0; j < style.length; j++) {
                if (style[j] instanceof AlignmentSpan) {
                    Layout.Alignment a = ((AlignmentSpan) style[j]).getAlignment();
                    if (a == Layout.Alignment.ALIGN_CENTER) align = "center";
                    else if (a == Layout.Alignment.ALIGN_OPPOSITE) align = "right";
                    else align = "left";
                }
            }
            if (align != null) out.append("<div align=\"").append(align).append("\">");
            withinDiv(out, text, i, next, option);
            if (align != null) out.append("</div>");
        }
    }

    private static void withinDiv(StringBuilder out, Spanned text, int start, int end, int option) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, QuoteSpan.class);
            QuoteSpan[] quotes = text.getSpans(i, next, QuoteSpan.class);
            for (int q = 0; q < quotes.length; q++) out.append("<blockquote>");
            withinBlockquote(out, text, i, next, option);
            for (int q = 0; q < quotes.length; q++) out.append("</blockquote>\n");
        }
    }

    private static void withinBlockquote(StringBuilder out, Spanned text, int start, int end, int option) {
        if ((option & TO_HTML_PARAGRAPH_LINES_INDIVIDUAL) == 0) withinBlockquoteConsecutive(out, text, start, end);
        else withinBlockquoteIndividual(out, text, start, end);
    }

    private static void withinBlockquoteConsecutive(StringBuilder out, Spanned text, int start, int end) {
        out.append("<p>");
        int next;
        for (int i = start; i < end; i = next) {
            next = TextUtils.indexOf(text, '\n', i, end);
            if (next < 0) next = end;
            int nl = 0;
            while (next < end && text.charAt(next) == '\n') {
                nl++;
                next++;
            }
            withinParagraph(out, text, i, next - nl);
            if (nl == 1) out.append("<br>\n");
            else {
                for (int j = 2; j < nl; j++) out.append("<br>");
                if (next != end) out.append("</p>\n<p>");
            }
        }
        out.append("</p>\n");
    }

    private static void withinBlockquoteIndividual(StringBuilder out, Spanned text, int start, int end) {
        boolean inList = false;
        int next;
        for (int i = start; i <= end; i = next + 1) {
            next = TextUtils.indexOf(text, '\n', i, end);
            if (next < 0) next = end;
            if (next == i) {
                if (inList) {
                    inList = false;
                    out.append("</ul>\n");
                }
                out.append("<br>\n");
                continue;
            }
            boolean item = false;
            ParagraphStyle[] ps = text.getSpans(i, next, ParagraphStyle.class);
            for (int j = 0; j < ps.length; j++) {
                int flags = text.getSpanFlags(ps[j]);
                if (ps[j] instanceof BulletSpan && (flags & Spanned.SPAN_PARAGRAPH) == Spanned.SPAN_PARAGRAPH) {
                    item = true;
                    break;
                }
            }
            if (item && !inList) {
                inList = true;
                out.append("<ul>\n");
            }
            if (inList && !item) {
                inList = false;
                out.append("</ul>\n");
            }
            String tag = item ? "li" : "p";
            out.append('<').append(tag).append('>');
            withinParagraph(out, text, i, next);
            out.append("</").append(tag).append(">\n");
            if (next == end && inList) {
                inList = false;
                out.append("</ul>\n");
            }
        }
    }

    private static void withinParagraph(StringBuilder out, Spanned text, int start, int end) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, CharacterStyle.class);
            CharacterStyle[] style = text.getSpans(i, next, CharacterStyle.class);
            boolean image = false;
            for (int j = 0; j < style.length; j++) {
                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();
                    if ((s & Typeface.BOLD) != 0) out.append("<b>");
                    if ((s & Typeface.ITALIC) != 0) out.append("<i>");
                }
                if (style[j] instanceof TypefaceSpan && "monospace".equals(((TypefaceSpan) style[j]).getFamily())) {
                    out.append("<tt>");
                }
                if (style[j] instanceof SuperscriptSpan) out.append("<sup>");
                if (style[j] instanceof SubscriptSpan) out.append("<sub>");
                if (style[j] instanceof UnderlineSpan) out.append("<u>");
                if (style[j] instanceof StrikethroughSpan) out.append("<span style=\"text-decoration:line-through;\">");
                if (style[j] instanceof URLSpan) {
                    out.append("<a href=\"").append(((URLSpan) style[j]).getURL()).append("\">");
                }
                if (style[j] instanceof ImageSpan) {
                    out.append("<img src=\"").append(((ImageSpan) style[j]).getSource()).append("\">");
                    image = true;
                }
                if (style[j] instanceof RelativeSizeSpan) {
                    float em = ((RelativeSizeSpan) style[j]).getSizeChange();
                    out.append("<span style=\"font-size:").append(twoDecimals(em)).append("em;\">");
                }
                if (style[j] instanceof ForegroundColorSpan) {
                    out.append("<span style=\"color:#")
                            .append(cssColor(((ForegroundColorSpan) style[j]).getForegroundColor())).append(";\">");
                }
                if (style[j] instanceof BackgroundColorSpan) {
                    out.append("<span style=\"background-color:#")
                            .append(cssColor(((BackgroundColorSpan) style[j]).getBackgroundColor())).append(";\">");
                }
            }
            if (!image) withinStyle(out, text, i, next);
            for (int j = style.length - 1; j >= 0; j--) {
                if (style[j] instanceof BackgroundColorSpan) out.append("</span>");
                if (style[j] instanceof ForegroundColorSpan) out.append("</span>");
                if (style[j] instanceof RelativeSizeSpan) out.append("</span>");
                if (style[j] instanceof URLSpan) out.append("</a>");
                if (style[j] instanceof StrikethroughSpan) out.append("</span>");
                if (style[j] instanceof UnderlineSpan) out.append("</u>");
                if (style[j] instanceof SubscriptSpan) out.append("</sub>");
                if (style[j] instanceof SuperscriptSpan) out.append("</sup>");
                if (style[j] instanceof TypefaceSpan && "monospace".equals(((TypefaceSpan) style[j]).getFamily())) {
                    out.append("</tt>");
                }
                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();
                    if ((s & Typeface.ITALIC) != 0) out.append("</i>");
                    if ((s & Typeface.BOLD) != 0) out.append("</b>");
                }
            }
        }
    }

    private static void withinStyle(StringBuilder out, CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '<') out.append("&lt;");
            else if (c == '>') out.append("&gt;");
            else if (c == '&') out.append("&amp;");
            else if (c >= 0xD800 && c <= 0xDFFF) {
                if (c < 0xDC00 && i + 1 < end) {
                    char d = text.charAt(i + 1);
                    if (d >= 0xDC00 && d <= 0xDFFF) {
                        i++;
                        int code = 0x10000 + ((c - 0xD800) << 10) + (d - 0xDC00);
                        out.append("&#").append(code).append(';');
                    }
                }
            } else if (c > 0x7E || c < ' ') {
                out.append("&#").append((int) c).append(';');
            } else if (c == ' ') {
                while (i + 1 < end && text.charAt(i + 1) == ' ') {
                    out.append("&nbsp;");
                    i++;
                }
                out.append(' ');
            } else out.append(c);
        }
    }

    private static String cssColor(int color) {
        String s = Integer.toHexString(color & 0xFFFFFF);
        StringBuilder b = new StringBuilder(6);
        for (int i = s.length(); i < 6; i++) b.append('0');
        b.append(s);
        return b.toString().toUpperCase(Locale.US);
    }

    private static String twoDecimals(float v) {
        int scaled = Math.round(v * 100f);
        int ip = scaled / 100;
        int frac = Math.abs(scaled % 100);
        return ip + "." + (frac < 10 ? "0" : "") + frac;
    }

    private static final class Converter {
        private static final float[] HEADING_SIZES = {1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f};
        private static final HashMap<String, Integer> CSS_COLORS = new HashMap<String, Integer>();
        private static Pattern sTextAlign;
        private static Pattern sForeground;
        private static Pattern sBackground;
        private static Pattern sDecoration;

        static {
            CSS_COLORS.put("darkgray", 0xFFA9A9A9);
            CSS_COLORS.put("gray", 0xFF808080);
            CSS_COLORS.put("lightgray", 0xFFD3D3D3);
            CSS_COLORS.put("darkgrey", 0xFFA9A9A9);
            CSS_COLORS.put("grey", 0xFF808080);
            CSS_COLORS.put("lightgrey", 0xFFD3D3D3);
            CSS_COLORS.put("green", 0xFF008000);
        }

        private final String mSource;
        private final int mFlags;
        private final ImageGetter mImageGetter;
        private final TagHandler mTagHandler;
        private final SpannableStringBuilder mOut = new SpannableStringBuilder();

        Converter(String source, int flags, ImageGetter imageGetter, TagHandler tagHandler) {
            mSource = source;
            mFlags = flags;
            mImageGetter = imageGetter;
            mTagHandler = tagHandler;
        }

        Spanned convert() {
            if (mSource == null) throw new NullPointerException();
            int i = 0;
            int n = mSource.length();
            while (i < n) {
                if (mSource.charAt(i) == '<') {
                    if (i + 3 < n && mSource.startsWith("<!--", i)) {
                        int end = mSource.indexOf("-->", i + 4);
                        i = end < 0 ? n : end + 3;
                        continue;
                    }
                    if (i + 1 < n && (mSource.charAt(i + 1) == '!' || mSource.charAt(i + 1) == '?')) {
                        int end = mSource.indexOf('>', i + 2);
                        i = end < 0 ? n : end + 1;
                        continue;
                    }
                    int end = mSource.indexOf('>', i + 1);
                    if (end < 0) {
                        appendText(mSource.substring(i));
                        break;
                    }
                    handleRaw(mSource.substring(i + 1, end));
                    i = end + 1;
                } else {
                    int end = mSource.indexOf('<', i);
                    if (end < 0) end = n;
                    appendText(mSource.substring(i, end));
                    i = end;
                }
            }
            fixParagraphs();
            return mOut;
        }

        private void appendText(String raw) {
            if (raw.length() == 0) return;
            mOut.append(decode(raw));
        }

        private void handleRaw(String raw) {
            String body = raw.trim();
            if (body.length() == 0) return;
            boolean closing = false;
            if (body.charAt(0) == '/') {
                closing = true;
                body = body.substring(1).trim();
            }
            boolean self = false;
            if (body.endsWith("/")) {
                self = true;
                body = body.substring(0, body.length() - 1).trim();
            }
            int sp = 0;
            while (sp < body.length() && !isSpace(body.charAt(sp))) sp++;
            String name = body.substring(0, sp);
            if (name.length() == 0) return;
            if (closing) {
                if (!name.equalsIgnoreCase("br") && !name.equalsIgnoreCase("img")) handleEndTag(name);
                return;
            }
            if (name.equalsIgnoreCase("br")) {
                handleEndTag(name);
                return;
            }
            handleStartTag(name, parseAttrs(body.substring(sp)));
            if (self && !name.equalsIgnoreCase("img")) handleEndTag(name);
        }

        private void handleStartTag(String tag, HashMap<String, String> attr) {
            if (tag.equalsIgnoreCase("p")) {
                startBlock(attr, margin(FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH));
                startCss(attr);
            } else if (tag.equalsIgnoreCase("ul")) {
                startBlock(attr, margin(FROM_HTML_SEPARATOR_LINE_BREAK_LIST));
            } else if (tag.equalsIgnoreCase("li")) {
                startBlock(attr, margin(FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM));
                start(new Bullet());
                startCss(attr);
            } else if (tag.equalsIgnoreCase("div")) {
                startBlock(attr, margin(FROM_HTML_SEPARATOR_LINE_BREAK_DIV));
            } else if (tag.equalsIgnoreCase("span")) {
                startCss(attr);
            } else if (tag.equalsIgnoreCase("strong") || tag.equalsIgnoreCase("b")) {
                start(new Bold());
            } else if (tag.equalsIgnoreCase("em") || tag.equalsIgnoreCase("cite") || tag.equalsIgnoreCase("dfn")
                    || tag.equalsIgnoreCase("i")) {
                start(new Italic());
            } else if (tag.equalsIgnoreCase("big")) start(new Big());
            else if (tag.equalsIgnoreCase("small")) start(new Small());
            else if (tag.equalsIgnoreCase("font")) startFont(attr);
            else if (tag.equalsIgnoreCase("blockquote")) {
                startBlock(attr, margin(FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE));
                start(new Blockquote());
            } else if (tag.equalsIgnoreCase("tt")) start(new Monospace());
            else if (tag.equalsIgnoreCase("a")) start(new Href(attr.get("href")));
            else if (tag.equalsIgnoreCase("u")) start(new Under());
            else if (tag.equalsIgnoreCase("del") || tag.equalsIgnoreCase("s") || tag.equalsIgnoreCase("strike")) {
                start(new Strike());
            } else if (tag.equalsIgnoreCase("sup")) start(new Super());
            else if (tag.equalsIgnoreCase("sub")) start(new Sub());
            else if (tag.length() == 2 && Character.toLowerCase(tag.charAt(0)) == 'h' && tag.charAt(1) >= '1'
                    && tag.charAt(1) <= '6') {
                startBlock(attr, margin(FROM_HTML_SEPARATOR_LINE_BREAK_HEADING));
                start(new Heading(tag.charAt(1) - '1'));
            } else if (tag.equalsIgnoreCase("img")) startImg(attr);
            else if (mTagHandler != null) mTagHandler.handleTag(true, tag, mOut, null);
        }

        private void handleEndTag(String tag) {
            if (tag.equalsIgnoreCase("br")) mOut.append('\n');
            else if (tag.equalsIgnoreCase("p")) {
                endCss();
                endBlock();
            } else if (tag.equalsIgnoreCase("ul") || tag.equalsIgnoreCase("div")) endBlock();
            else if (tag.equalsIgnoreCase("li")) {
                endCss();
                endBlock();
                end(Bullet.class, new BulletSpan());
            } else if (tag.equalsIgnoreCase("span")) endCss();
            else if (tag.equalsIgnoreCase("strong") || tag.equalsIgnoreCase("b")) {
                end(Bold.class, new StyleSpan(Typeface.BOLD));
            } else if (tag.equalsIgnoreCase("em") || tag.equalsIgnoreCase("cite") || tag.equalsIgnoreCase("dfn")
                    || tag.equalsIgnoreCase("i")) {
                end(Italic.class, new StyleSpan(Typeface.ITALIC));
            } else if (tag.equalsIgnoreCase("big")) end(Big.class, new RelativeSizeSpan(1.25f));
            else if (tag.equalsIgnoreCase("small")) end(Small.class, new RelativeSizeSpan(0.8f));
            else if (tag.equalsIgnoreCase("font")) endFont();
            else if (tag.equalsIgnoreCase("blockquote")) {
                endBlock();
                end(Blockquote.class, new QuoteSpan());
            } else if (tag.equalsIgnoreCase("tt")) end(Monospace.class, new TypefaceSpan("monospace"));
            else if (tag.equalsIgnoreCase("a")) endA();
            else if (tag.equalsIgnoreCase("u")) end(Under.class, new UnderlineSpan());
            else if (tag.equalsIgnoreCase("del") || tag.equalsIgnoreCase("s") || tag.equalsIgnoreCase("strike")) {
                end(Strike.class, new StrikethroughSpan());
            } else if (tag.equalsIgnoreCase("sup")) end(Super.class, new SuperscriptSpan());
            else if (tag.equalsIgnoreCase("sub")) end(Sub.class, new SubscriptSpan());
            else if (tag.length() == 2 && Character.toLowerCase(tag.charAt(0)) == 'h' && tag.charAt(1) >= '1'
                    && tag.charAt(1) <= '6') {
                endHeading();
            } else if (mTagHandler != null) mTagHandler.handleTag(false, tag, mOut, null);
        }

        private int margin(int flag) {
            return (mFlags & flag) != 0 ? 1 : 2;
        }

        private void startBlock(HashMap<String, String> attr, int margin) {
            appendNewlines(margin);
            start(new Newline(margin));
            String style = attr.get("style");
            if (style == null) return;
            Matcher m = textAlign().matcher(style);
            if (!m.find()) return;
            String alignment = trimSemi(m.group(1));
            Layout.Alignment value = null;
            if (alignment.equalsIgnoreCase("start")) value = Layout.Alignment.ALIGN_NORMAL;
            else if (alignment.equalsIgnoreCase("center")) value = Layout.Alignment.ALIGN_CENTER;
            else if (alignment.equalsIgnoreCase("end")) value = Layout.Alignment.ALIGN_OPPOSITE;
            if (value != null) start(new Align(value));
        }

        private void endBlock() {
            Newline n = getLast(Newline.class);
            if (n != null) {
                appendNewlines(n.mNum);
                mOut.removeSpan(n);
            }
            Align a = getLast(Align.class);
            if (a != null) setSpanFromMark(a, new AlignmentSpan.Standard(a.mAlignment), null);
        }

        private void endHeading() {
            Heading h = getLast(Heading.class);
            if (h != null) {
                setSpanFromMark(h, new RelativeSizeSpan(HEADING_SIZES[h.mLevel]), new StyleSpan(Typeface.BOLD));
            }
            endBlock();
        }

        private void startCss(HashMap<String, String> attr) {
            String style = attr.get("style");
            if (style == null) return;
            Matcher m = foreground().matcher(style);
            if (m.find()) {
                int c = htmlColor(trimSemi(m.group(1)));
                if (c != -1) start(new Fore(c | 0xFF000000));
            }
            m = background().matcher(style);
            if (m.find()) {
                int c = htmlColor(trimSemi(m.group(1)));
                if (c != -1) start(new Back(c | 0xFF000000));
            }
            m = decoration().matcher(style);
            if (m.find() && trimSemi(m.group(1)).equalsIgnoreCase("line-through")) start(new Strike());
        }

        private void endCss() {
            Strike s = getLast(Strike.class);
            if (s != null) setSpanFromMark(s, new StrikethroughSpan(), null);
            Back b = getLast(Back.class);
            if (b != null) setSpanFromMark(b, new BackgroundColorSpan(b.mColor), null);
            Fore f = getLast(Fore.class);
            if (f != null) setSpanFromMark(f, new ForegroundColorSpan(f.mColor), null);
        }

        private void startFont(HashMap<String, String> attr) {
            String color = attr.get("color");
            String face = attr.get("face");
            if (!TextUtils.isEmpty(color)) {
                int c = htmlColor(color);
                if (c != -1) start(new Fore(c | 0xFF000000));
            }
            if (!TextUtils.isEmpty(face)) start(new Font(face));
        }

        private void endFont() {
            Font font = getLast(Font.class);
            if (font != null) setSpanFromMark(font, new TypefaceSpan(font.mFace), null);
            Fore f = getLast(Fore.class);
            if (f != null) setSpanFromMark(f, new ForegroundColorSpan(f.mColor), null);
        }

        private void endA() {
            Href h = getLast(Href.class);
            if (h != null && h.mHref != null) setSpanFromMark(h, new URLSpan(h.mHref), null);
            else if (h != null) mOut.removeSpan(h);
        }

        private void startImg(HashMap<String, String> attr) {
            String src = attr.get("src");
            Drawable d = mImageGetter != null ? mImageGetter.getDrawable(src) : null;
            int len = mOut.length();
            mOut.append('\uFFFC');
            if (d != null) mOut.setSpan(new ImageSpan(d, src), len, mOut.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        private int htmlColor(String color) {
            if ((mFlags & FROM_HTML_OPTION_USE_CSS_COLORS) != 0) {
                Integer css = CSS_COLORS.get(color.toLowerCase(Locale.US));
                if (css != null) return css;
            }
            try {
                return Color.parseColor(color);
            } catch (IllegalArgumentException e) {
                return -1;
            }
        }

        private void appendNewlines(int min) {
            int len = mOut.length();
            if (len == 0) return;
            int existing = 0;
            for (int i = len - 1; i >= 0 && mOut.charAt(i) == '\n'; i--) existing++;
            for (int j = existing; j < min; j++) mOut.append('\n');
        }

        private void start(Object mark) {
            int len = mOut.length();
            mOut.setSpan(mark, len, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }

        private void end(Class kind, Object repl) {
            Object obj = getLast(kind);
            if (obj != null) setSpanFromMark(obj, repl, null);
        }

        private void setSpanFromMark(Object mark, Object span, Object span2) {
            int where = mOut.getSpanStart(mark);
            mOut.removeSpan(mark);
            int len = mOut.length();
            if (where >= 0 && where != len) {
                mOut.setSpan(span, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (span2 != null) mOut.setSpan(span2, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        private <T> T getLast(Class<T> kind) {
            T[] objs = mOut.getSpans(0, mOut.length(), kind);
            if (objs.length == 0) return null;
            return objs[objs.length - 1];
        }

        private void fixParagraphs() {
            Object[] obj = mOut.getSpans(0, mOut.length(), ParagraphStyle.class);
            for (int i = 0; i < obj.length; i++) {
                int start = mOut.getSpanStart(obj[i]);
                int end = mOut.getSpanEnd(obj[i]);
                if (end - 2 >= 0 && mOut.charAt(end - 1) == '\n' && mOut.charAt(end - 2) == '\n') end--;
                if (end == start) mOut.removeSpan(obj[i]);
                else if (boundary(start) && boundary(end)) mOut.setSpan(obj[i], start, end, Spanned.SPAN_PARAGRAPH);
            }
        }

        private boolean boundary(int index) {
            return index == 0 || index == mOut.length() || mOut.charAt(index - 1) == '\n';
        }

        private static Pattern textAlign() {
            if (sTextAlign == null) sTextAlign = Pattern.compile("(?:\\s+|\\A)text-align\\s*:\\s*(\\S*)\\b");
            return sTextAlign;
        }

        private static Pattern foreground() {
            if (sForeground == null) sForeground = Pattern.compile("(?:\\s+|\\A)color\\s*:\\s*(\\S*)\\b");
            return sForeground;
        }

        private static Pattern background() {
            if (sBackground == null) {
                sBackground = Pattern.compile("(?:\\s+|\\A)background(?:-color)?\\s*:\\s*(\\S*)\\b");
            }
            return sBackground;
        }

        private static Pattern decoration() {
            if (sDecoration == null) sDecoration = Pattern.compile("(?:\\s+|\\A)text-decoration\\s*:\\s*(\\S*)\\b");
            return sDecoration;
        }

        private static String trimSemi(String s) {
            if (s != null && s.endsWith(";")) return s.substring(0, s.length() - 1);
            return s == null ? "" : s;
        }

        private static boolean isSpace(char c) {
            return c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f';
        }

        private static HashMap<String, String> parseAttrs(String body) {
            HashMap<String, String> map = new HashMap<String, String>();
            int i = 0;
            int n = body.length();
            while (i < n) {
                while (i < n && isSpace(body.charAt(i))) i++;
                if (i >= n) break;
                int start = i;
                while (i < n && !isSpace(body.charAt(i)) && body.charAt(i) != '=') i++;
                String key = body.substring(start, i).toLowerCase(Locale.US);
                while (i < n && isSpace(body.charAt(i))) i++;
                if (i >= n || body.charAt(i) != '=') {
                    if (key.length() > 0) map.put(key, "");
                    continue;
                }
                i++;
                while (i < n && isSpace(body.charAt(i))) i++;
                if (i >= n) {
                    map.put(key, "");
                    break;
                }
                char q = body.charAt(i);
                String value;
                if (q == '"' || q == '\'') {
                    i++;
                    int v = i;
                    while (i < n && body.charAt(i) != q) i++;
                    value = body.substring(v, i);
                    if (i < n) i++;
                } else {
                    int v = i;
                    while (i < n && !isSpace(body.charAt(i))) i++;
                    value = body.substring(v, i);
                }
                if (key.length() > 0) map.put(key, decode(value));
            }
            return map;
        }

        private static String decode(String raw) {
            if (raw.indexOf('&') < 0) return raw;
            StringBuilder out = new StringBuilder(raw.length());
            int i = 0;
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c != '&') {
                    out.append(c);
                    i++;
                    continue;
                }
                int semi = raw.indexOf(';', i + 1);
                if (semi < 0 || semi - i > 12) {
                    out.append('&');
                    i++;
                    continue;
                }
                String ent = raw.substring(i + 1, semi);
                if (ent.equals("lt")) out.append('<');
                else if (ent.equals("gt")) out.append('>');
                else if (ent.equals("amp")) out.append('&');
                else if (ent.equals("quot")) out.append('"');
                else if (ent.equals("apos")) out.append('\'');
                else if (ent.equals("nbsp")) out.append('\u00A0');
                else if (ent.length() > 1 && ent.charAt(0) == '#') {
                    try {
                        int cp = ent.charAt(1) == 'x' || ent.charAt(1) == 'X'
                                ? Integer.parseInt(ent.substring(2), 16) : Integer.parseInt(ent.substring(1));
                        out.append(Character.toChars(cp));
                    } catch (RuntimeException e) {
                        out.append('&').append(ent).append(';');
                    }
                } else {
                    out.append('&').append(ent).append(';');
                    i = semi + 1;
                    continue;
                }
                i = semi + 1;
            }
            return out.toString();
        }

        private static final class Bold {}
        private static final class Italic {}
        private static final class Under {}
        private static final class Strike {}
        private static final class Big {}
        private static final class Small {}
        private static final class Monospace {}
        private static final class Super {}
        private static final class Sub {}
        private static final class Bullet {}
        private static final class Blockquote {}

        private static final class Newline {
            final int mNum;
            Newline(int n) { mNum = n; }
        }

        private static final class Heading {
            final int mLevel;
            Heading(int level) { mLevel = level; }
        }

        private static final class Href {
            final String mHref;
            Href(String href) { mHref = href; }
        }

        private static final class Font {
            final String mFace;
            Font(String face) { mFace = face; }
        }

        private static final class Fore {
            final int mColor;
            Fore(int color) { mColor = color; }
        }

        private static final class Back {
            final int mColor;
            Back(int color) { mColor = color; }
        }

        private static final class Align {
            final Layout.Alignment mAlignment;
            Align(Layout.Alignment alignment) { mAlignment = alignment; }
        }
    }
}
