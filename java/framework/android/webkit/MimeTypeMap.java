package android.webkit;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Maps MIME types to file extensions and back. The table follows Android 10's MimeMap (mime.types plus
 * Android's overrides) for the common web, image, audio, video, document and archive types.
 */
public class MimeTypeMap {
    private static final MimeTypeMap sMimeTypeMap = new MimeTypeMap();
    private static final Pattern FILENAME_PATTERN = Pattern.compile("[a-zA-Z_0-9\\.\\-\\(\\)\\%]+");

    private final HashMap<String, String> mExtToMime = new HashMap<String, String>();
    private final HashMap<String, String> mMimeToExt = new HashMap<String, String>();

    private MimeTypeMap() {
        // The first extension listed for a type is the one getExtensionFromMimeType returns.
        // Text and web
        add("text/plain", "txt", "text", "asc", "diff", "po", "pot", "brf", "srt", "conf", "log", "ini");
        add("text/html", "html", "htm", "shtml");
        add("text/css", "css");
        add("text/xml", "xml", "xsd", "xsl");
        add("application/xml", "xml");
        add("application/xhtml+xml", "xhtml", "xht");
        add("application/javascript", "js");
        add("text/javascript", "js", "mjs");
        add("application/json", "json", "map");
        add("application/ld+json", "jsonld");
        add("application/wasm", "wasm");
        add("text/comma-separated-values", "csv");
        add("text/csv", "csv");
        add("text/tab-separated-values", "tsv");
        add("text/markdown", "md", "markdown");
        add("text/calendar", "ics", "icz");
        add("text/x-vcard", "vcf");
        add("text/vcard", "vcf");
        add("text/x-vcalendar", "vcs");
        add("text/richtext", "rtx");
        add("text/rtf", "rtf");
        add("text/x-java", "java");
        add("text/x-csrc", "c");
        add("text/x-chdr", "h");
        add("text/x-c++src", "cpp", "c++", "cc", "cxx");
        add("text/x-c++hdr", "hpp", "h++", "hh", "hxx");
        add("text/x-python", "py");
        add("application/x-sh", "sh");
        add("application/x-latex", "latex");
        add("application/x-tex", "tex");
        add("application/rss+xml", "rss");
        add("application/atom+xml", "atom");
        // Images
        add("image/jpeg", "jpg", "jpeg", "jpe");
        add("image/png", "png");
        add("image/gif", "gif");
        add("image/webp", "webp");
        add("image/bmp", "bmp");
        add("image/x-ms-bmp", "bmp");
        add("image/svg+xml", "svg", "svgz");
        add("image/x-icon", "ico");
        add("image/vnd.microsoft.icon", "ico");
        add("image/tiff", "tiff", "tif");
        add("image/heic", "heic");
        add("image/heif", "heif");
        add("image/avif", "avif");
        add("image/vnd.wap.wbmp", "wbmp");
        add("image/x-adobe-dng", "dng");
        add("image/x-canon-cr2", "cr2");
        add("image/x-nikon-nef", "nef");
        add("image/x-photoshop", "psd");
        add("image/x-portable-anymap", "pnm");
        add("image/x-portable-bitmap", "pbm");
        add("image/x-portable-graymap", "pgm");
        add("image/x-portable-pixmap", "ppm");
        add("image/x-xbitmap", "xbm");
        add("image/x-xpixmap", "xpm");
        // Audio
        add("audio/mpeg", "mp3", "mpga", "mpega", "mp2");
        add("audio/mp4", "m4a");
        add("audio/aac", "aac");
        add("audio/aac-adts", "aac");
        add("audio/flac", "flac");
        add("audio/ogg", "ogg", "oga", "opus", "spx");
        add("audio/x-wav", "wav");
        add("audio/wav", "wav");
        add("audio/amr", "amr");
        add("audio/amr-wb", "awb");
        add("audio/3gpp", "3ga");
        add("audio/midi", "mid", "midi", "kar", "xmf", "rtttl", "smf", "imy", "rtx", "ota", "mxmf");
        add("audio/sp-midi", "mid");
        add("audio/x-matroska", "mka");
        add("audio/x-mpegurl", "m3u");
        add("audio/x-ms-wma", "wma");
        add("audio/x-ms-wax", "wax");
        add("audio/x-scpls", "pls");
        add("audio/x-aiff", "aif", "aiff", "aifc");
        add("audio/webm", "weba");
        add("application/ogg", "ogx");
        // Video
        add("video/mp4", "mp4", "m4v");
        add("video/x-m4v", "m4v");
        add("video/webm", "webm");
        add("video/3gpp", "3gp", "3gpp");
        add("video/3gpp2", "3g2", "3gpp2");
        add("video/x-matroska", "mkv");
        add("video/quicktime", "mov", "qt");
        add("video/avi", "avi");
        add("video/x-msvideo", "avi");
        add("video/mpeg", "mpeg", "mpg", "mpe", "vob");
        add("video/mp2ts", "ts");
        add("video/x-flv", "flv");
        add("video/x-ms-wmv", "wmv");
        add("video/x-ms-asf", "asf", "asx");
        add("video/ogg", "ogv");
        add("application/vnd.apple.mpegurl", "m3u8");
        add("application/x-mpegurl", "m3u8");
        add("application/dash+xml", "mpd");
        // Documents
        add("application/pdf", "pdf");
        add("application/msword", "doc", "dot");
        add("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        add("application/vnd.openxmlformats-officedocument.wordprocessingml.template", "dotx");
        add("application/vnd.ms-excel", "xls", "xlb", "xlt");
        add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        add("application/vnd.openxmlformats-officedocument.spreadsheetml.template", "xltx");
        add("application/vnd.ms-powerpoint", "ppt", "pps", "pot");
        add("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        add("application/vnd.openxmlformats-officedocument.presentationml.slideshow", "ppsx");
        add("application/vnd.oasis.opendocument.text", "odt");
        add("application/vnd.oasis.opendocument.spreadsheet", "ods");
        add("application/vnd.oasis.opendocument.presentation", "odp");
        add("application/vnd.oasis.opendocument.graphics", "odg");
        add("application/rtf", "rtf");
        add("application/epub+zip", "epub");
        add("application/x-mobipocket-ebook", "mobi", "prc");
        add("application/postscript", "ps", "ai", "eps", "epsi", "epsf", "eps2", "eps3");
        add("application/vnd.ms-xpsdocument", "xps");
        // Archives and binaries
        add("application/zip", "zip");
        add("application/gzip", "gz", "tgz");
        add("application/x-gzip", "gz", "tgz");
        add("application/x-tar", "tar");
        add("application/x-bzip2", "bz2");
        add("application/x-xz", "xz");
        add("application/x-7z-compressed", "7z");
        add("application/rar", "rar");
        add("application/vnd.rar", "rar");
        add("application/x-rar-compressed", "rar");
        add("application/x-iso9660-image", "iso");
        add("application/vnd.android.package-archive", "apk");
        add("application/java-archive", "jar");
        add("application/x-java-archive", "jar");
        add("application/x-debian-package", "deb", "udeb");
        add("application/x-msdos-program", "exe", "com", "bat", "dll");
        add("application/x-shockwave-flash", "swf");
        add("application/x-x509-ca-cert", "crt", "der");
        add("application/x-pkcs12", "p12", "pfx");
        add("application/x-pem-file", "pem");
        add("application/x-bittorrent", "torrent");
        add("application/vnd.google-earth.kml+xml", "kml");
        add("application/vnd.google-earth.kmz", "kmz");
        add("application/octet-stream", "bin");
        // Fonts
        add("font/ttf", "ttf");
        add("application/x-font-ttf", "ttf", "ttc");
        add("font/otf", "otf");
        add("font/woff", "woff");
        add("font/woff2", "woff2");
    }

    private void add(String mimeType, String... extensions) {
        for (String ext : extensions) {
            if (!mExtToMime.containsKey(ext)) mExtToMime.put(ext, mimeType);
        }
        if (!mMimeToExt.containsKey(mimeType)) mMimeToExt.put(mimeType, extensions[0]);
    }

    public static MimeTypeMap getSingleton() {
        return sMimeTypeMap;
    }

    /** As AOSP: the text after the last '.' of the URL's last path segment, or "" if it has odd characters. */
    public static String getFileExtensionFromUrl(String url) {
        if (!TextUtils.isEmpty(url)) {
            int fragment = url.lastIndexOf('#');
            if (fragment > 0) url = url.substring(0, fragment);
            int query = url.lastIndexOf('?');
            if (query > 0) url = url.substring(0, query);
            int filenamePos = url.lastIndexOf('/');
            String filename = 0 <= filenamePos ? url.substring(filenamePos + 1) : url;
            // A filename with special characters is not considered valid for matching.
            if (!filename.isEmpty() && FILENAME_PATTERN.matcher(filename).matches()) {
                int dotPos = filename.lastIndexOf('.');
                if (0 <= dotPos) return filename.substring(dotPos + 1);
            }
        }
        return "";
    }

    public boolean hasMimeType(String mimeType) {
        return getExtensionFromMimeType(mimeType) != null;
    }

    public String getMimeTypeFromExtension(String extension) {
        if (extension == null || extension.isEmpty()) return null;
        return mExtToMime.get(extension.toLowerCase(Locale.ROOT));
    }

    public boolean hasExtension(String extension) {
        return getMimeTypeFromExtension(extension) != null;
    }

    public String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return null;
        return mMimeToExt.get(mimeType.toLowerCase(Locale.ROOT));
    }
}
