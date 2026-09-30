package java.text;

public final class Normalizer {
    private Normalizer() {
    }

    public enum Form {
        NFD, NFC, NFKD, NFKC
    }

    public static String normalize(CharSequence src, Form form) {
        return src.toString();
    }

    public static boolean isNormalized(CharSequence src, Form form) {
        return true;
    }
}
