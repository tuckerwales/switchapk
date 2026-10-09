import java.util.regex.*;

/**
 * java.util.regex property classes: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 * Characters are chosen so that our approximate Character tables classify them like the JDK does.
 */
public class RegexTest {
    static final String SAMPLE = "aZ5_ \téÉΔδЖжあカ中가、Ａ☃€+(-) ";

    static void p(Object o) {
        System.out.println(o);
    }

    static void check(String regex) {
        StringBuilder sb = new StringBuilder();
        try {
            Pattern pt = Pattern.compile(regex);
            for (int i = 0; i < SAMPLE.length(); i++) {
                sb.append(pt.matcher(SAMPLE.substring(i, i + 1)).matches() ? '1' : '0');
            }
        } catch (PatternSyntaxException e) {
            sb.append("PatternSyntaxException");
        }
        p(regex + " " + sb);
    }

    public static void main(String[] args) {
        String[] blocks = {
            "\\p{InHiragana}", "\\p{InKatakana}", "\\p{InCJK_Unified_Ideographs}", "\\p{InCJK_Symbols_and_Punctuation}",
            "\\p{InHalfwidth_and_Fullwidth_Forms}", "\\p{InHangul_Syllables}", "\\p{InBasicLatin}",
            "\\p{InLatin-1 Supplement}", "\\p{InGreek}", "\\p{InCyrillic}", "\\p{InMiscellaneous_Symbols}",
            "\\p{blk=Hiragana}", "\\p{block=CJKUnifiedIdeographs}", "\\P{InBasicLatin}", "[\\p{InHiragana}\\p{InKatakana}]+",
            "\\p{InNoSuchBlock}",
        };
        for (String b : blocks) {
            check(b);
        }
        String[] cats = {
            "\\p{L}", "\\p{Lu}", "\\p{Ll}", "\\p{Lo}", "\\p{IsL}", "\\p{IsLu}", "\\p{gc=Ll}", "\\p{general_category=Lu}",
            "\\p{N}", "\\p{Nd}", "\\p{Zs}", "\\p{Z}", "\\p{Sc}", "\\p{Pd}", "\\p{Ps}", "\\p{Pe}", "\\p{Pc}", "\\pL", "\\PL",
            "\\p{LC}", "\\p{L&}", "\\p{Cc}", "\\p{So}", "\\p{Sm}",
        };
        for (String c : cats) {
            check(c);
        }
        String[] posix = {
            "\\p{Lower}", "\\p{Upper}", "\\p{Alpha}", "\\p{Digit}", "\\p{Alnum}", "\\p{Punct}", "\\p{Graph}",
            "\\p{Print}", "\\p{Blank}", "\\p{Cntrl}", "\\p{XDigit}", "\\p{Space}", "\\p{ASCII}",
        };
        for (String c : posix) {
            check(c);
        }
        String[] java = {
            "\\p{javaLowerCase}", "\\p{javaUpperCase}", "\\p{javaWhitespace}", "\\p{javaLetter}", "\\p{javaDigit}",
            "\\p{javaLetterOrDigit}", "\\p{javaSpaceChar}", "\\p{javaJavaIdentifierStart}", "\\p{IsAlphabetic}",
            "\\p{IsLetter}", "\\p{IsLowercase}", "\\p{IsUppercase}", "\\p{IsWhite_Space}", "\\p{IsDigit}",
            "\\p{IsPunctuation}", "\\p{IsIdeographic}",
        };
        for (String c : java) {
            check(c);
        }
        String[] scripts = {
            "\\p{IsLatin}", "\\p{IsGreek}", "\\p{IsCyrillic}", "\\p{IsHiragana}", "\\p{IsKatakana}", "\\p{IsHan}",
            "\\p{IsHangul}", "\\p{sc=Latin}", "\\p{script=Han}", "\\p{IsNoSuchScript}",
        };
        for (String c : scripts) {
            check(c);
        }
        p("日本語 text".replaceAll("\\p{InCJK_Unified_Ideographs}", "#"));
        p(Pattern.compile("\\p{InHiragana}|\\p{InKatakana}|\\p{InCJK_Unified_Ideographs}").matcher("abcあ").find());
    }
}
