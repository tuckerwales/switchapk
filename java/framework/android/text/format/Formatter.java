package android.text.format;

import android.content.Context;

/**
 * File size and IPv4 formatting (AOSP Formatter). Sizes use SI units of 1000,
 * which is the API 29 default. A null context yields an empty string.
 */
public final class Formatter {
    private static final String[] UNITS = {"B", "kB", "MB", "GB", "TB", "PB"};

    public Formatter() {}

    public static String formatFileSize(Context context, long sizeBytes) {
        if (context == null) return "";
        return formatBytes(sizeBytes, false);
    }

    public static String formatShortFileSize(Context context, long sizeBytes) {
        if (context == null) return "";
        return formatBytes(sizeBytes, true);
    }

    /**
     * Formats the int the way the platform method does: the high byte is the
     * first octet. {@code 0x01020304} is {@code "1.2.3.4"}.
     */
    public static String formatIpAddress(int ipv4Address) {
        return ((ipv4Address >> 24) & 0xFF) + "." + ((ipv4Address >> 16) & 0xFF) + "."
                + ((ipv4Address >> 8) & 0xFF) + "." + (ipv4Address & 0xFF);
    }

    private static String formatBytes(long sizeBytes, boolean shorter) {
        if (sizeBytes < 0) return "-" + formatBytes(-sizeBytes, shorter);
        if (sizeBytes == 0) return "0 B";
        float result = sizeBytes;
        int unit = 0;
        while (result > 900 && unit < UNITS.length - 1) {
            result /= 1000f;
            unit++;
        }
        int decimals;
        if (result < 1) decimals = 2;
        else if (result < 10) decimals = shorter ? 1 : 2;
        else if (result < 100) decimals = shorter ? 0 : 2;
        else decimals = 0;
        return trimFloat(result, decimals) + " " + UNITS[unit];
    }

    private static String trimFloat(double v, int decimals) {
        if (decimals <= 0) return Long.toString(Math.round(v));
        double scale = 1;
        for (int i = 0; i < decimals; i++) scale *= 10;
        long scaled = Math.round(v * scale);
        long ip = scaled / (long) scale;
        long frac = Math.abs(scaled % (long) scale);
        String f = Long.toString(frac);
        while (f.length() < decimals) f = "0" + f;
        return ip + "." + f;
    }
}
