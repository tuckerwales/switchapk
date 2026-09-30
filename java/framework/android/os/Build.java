package android.os;

public class Build {
    public static final String UNKNOWN = "unknown";
    public static final String ID = "SWITCHAPK";
    public static final String DISPLAY = "switchapk";
    public static final String PRODUCT = "switchapk";
    public static final String DEVICE = "switchapk";
    public static final String BOARD = "nx";
    public static final String CPU_ABI;
    public static final String CPU_ABI2 = "";
    public static final String MANUFACTURER = "switchapk";
    public static final String BRAND = "switchapk";
    public static final String MODEL = "Nintendo Switch (switchapk)";
    public static final String SOC_MANUFACTURER = "NVIDIA";
    public static final String SOC_MODEL = "Tegra X1";
    public static final String BOOTLOADER = UNKNOWN;
    @Deprecated public static final String RADIO = UNKNOWN;
    public static final String HARDWARE = "nx";
    public static final String SKU = UNKNOWN;
    public static final String ODM_SKU = UNKNOWN;
    public static final boolean IS_EMULATOR = false;
    @Deprecated public static final String SERIAL = UNKNOWN;
    public static final String[] SUPPORTED_ABIS;
    public static final String[] SUPPORTED_32_BIT_ABIS = new String[0];
    public static final String[] SUPPORTED_64_BIT_ABIS;
    public static final String TYPE = "user";
    public static final String TAGS = "release-keys";
    public static final String FINGERPRINT = "switchapk/switchapk/switchapk:10/SWITCHAPK/1:user/release-keys";
    public static final long TIME = 1700000000000L;
    public static final String USER = "switchapk";
    public static final String HOST = "switchapk";

    static {
        String arch = System.getProperty("os.arch", "aarch64");
        String abi = (arch.contains("64") && (arch.contains("x86") || arch.contains("amd"))) ? "x86_64" : "arm64-v8a";
        CPU_ABI = abi;
        SUPPORTED_ABIS = new String[] {abi};
        SUPPORTED_64_BIT_ABIS = new String[] {abi};
    }

    public static class VERSION {
        public static final String INCREMENTAL = "1";
        public static final String RELEASE = "10";
        public static final String RELEASE_OR_CODENAME = "10";
        public static final String RELEASE_OR_PREVIEW_DISPLAY = "10";
        public static final String BASE_OS = "";
        public static final String SECURITY_PATCH = "2020-01-01";
        public static final int MEDIA_PERFORMANCE_CLASS = 0;
        @Deprecated public static final String SDK = "29";
        public static final int SDK_INT = 29;
        public static final int PREVIEW_SDK_INT = 0;
        public static final String CODENAME = "REL";
        public static final int DEVICE_INITIAL_SDK_INT = 29;
    }

    public static class VERSION_CODES {
        public static final int CUR_DEVELOPMENT = 10000;
        public static final int BASE = 1;
        public static final int BASE_1_1 = 2;
        public static final int CUPCAKE = 3;
        public static final int DONUT = 4;
        public static final int ECLAIR = 5;
        public static final int ECLAIR_0_1 = 6;
        public static final int ECLAIR_MR1 = 7;
        public static final int FROYO = 8;
        public static final int GINGERBREAD = 9;
        public static final int GINGERBREAD_MR1 = 10;
        public static final int HONEYCOMB = 11;
        public static final int HONEYCOMB_MR1 = 12;
        public static final int HONEYCOMB_MR2 = 13;
        public static final int ICE_CREAM_SANDWICH = 14;
        public static final int ICE_CREAM_SANDWICH_MR1 = 15;
        public static final int JELLY_BEAN = 16;
        public static final int JELLY_BEAN_MR1 = 17;
        public static final int JELLY_BEAN_MR2 = 18;
        public static final int KITKAT = 19;
        public static final int KITKAT_WATCH = 20;
        public static final int L = 21;
        public static final int LOLLIPOP = 21;
        public static final int LOLLIPOP_MR1 = 22;
        public static final int M = 23;
        public static final int N = 24;
        public static final int N_MR1 = 25;
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int S_V2 = 32;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANILLA_ICE_CREAM = 35;
    }

    public static class Partition {
        public static final String PARTITION_NAME_SYSTEM = "system";
        public String getName() { return PARTITION_NAME_SYSTEM; }
        public String getFingerprint() { return FINGERPRINT; }
        public long getBuildTimeMillis() { return TIME; }
    }

    public static java.util.List<Partition> getFingerprintedPartitions() { return new java.util.ArrayList<Partition>(); }
    public static String getSerial() { return UNKNOWN; }
    public static String getRadioVersion() { return null; }
}
