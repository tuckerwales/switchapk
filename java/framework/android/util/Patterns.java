package android.util;

import java.util.regex.Pattern;

public class Patterns {
    public static final Pattern IP_ADDRESS = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[0-9]))");
    public static final Pattern DOMAIN_NAME = Pattern.compile("(([a-zA-Z0-9][a-zA-Z0-9\\-]*\\.)+[a-zA-Z]{2,63})");
    public static final Pattern WEB_URL = Pattern.compile(
            "((?:(http|https|Http|Https|rtsp|Rtsp):\\/\\/)?(?:[a-zA-Z0-9\\-\\._~%!$&'()*+,;=:]+@)?)?(([a-zA-Z0-9][a-zA-Z0-9\\-]*\\.)+[a-zA-Z]{2,63})(?::\\d{1,5})?(?:\\/[a-zA-Z0-9\\-\\._~%!$&'()*+,;=:@/]*)?(?:\\?[a-zA-Z0-9\\-\\._~%!$&'()*+,;=:@/?]*)?(?:#[a-zA-Z0-9\\-\\._~%!$&'()*+,;=:@/?]*)?");
    public static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}\\@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+");
    public static final Pattern PHONE = Pattern.compile("(\\+[0-9]+[\\- \\.]*)?(\\([0-9]+\\)[\\- \\.]*)?([0-9][0-9\\- \\.]+[0-9])");

    private Patterns() {}
}
