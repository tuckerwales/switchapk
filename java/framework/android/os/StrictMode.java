package android.os;

public final class StrictMode {
    private StrictMode() {}

    public static void setThreadPolicy(ThreadPolicy policy) {}
    public static ThreadPolicy getThreadPolicy() { return ThreadPolicy.LAX; }
    public static ThreadPolicy allowThreadDiskWrites() { return ThreadPolicy.LAX; }
    public static ThreadPolicy allowThreadDiskReads() { return ThreadPolicy.LAX; }
    public static void setVmPolicy(VmPolicy policy) {}
    public static VmPolicy getVmPolicy() { return VmPolicy.LAX; }
    public static void enableDefaults() {}
    public static void noteSlowCall(String name) {}

    public static final class ThreadPolicy {
        public static final ThreadPolicy LAX = new ThreadPolicy();

        public static final class Builder {
            public Builder() {}
            public Builder(ThreadPolicy policy) {}
            public Builder detectAll() { return this; }
            public Builder permitAll() { return this; }
            public Builder detectNetwork() { return this; }
            public Builder permitNetwork() { return this; }
            public Builder detectCustomSlowCalls() { return this; }
            public Builder permitCustomSlowCalls() { return this; }
            public Builder detectDiskReads() { return this; }
            public Builder permitDiskReads() { return this; }
            public Builder detectDiskWrites() { return this; }
            public Builder permitDiskWrites() { return this; }
            public Builder detectResourceMismatches() { return this; }
            public Builder detectUnbufferedIo() { return this; }
            public Builder permitUnbufferedIo() { return this; }
            public Builder penaltyDialog() { return this; }
            public Builder penaltyDeath() { return this; }
            public Builder penaltyDeathOnNetwork() { return this; }
            public Builder penaltyFlashScreen() { return this; }
            public Builder penaltyLog() { return this; }
            public Builder penaltyDropBox() { return this; }
            public Builder penaltyListener(java.util.concurrent.Executor executor, Object listener) { return this; }
            public ThreadPolicy build() { return LAX; }
        }
    }

    public static final class VmPolicy {
        public static final VmPolicy LAX = new VmPolicy();

        public static final class Builder {
            public Builder() {}
            public Builder(VmPolicy base) {}
            public Builder setClassInstanceLimit(Class klass, int instanceLimit) { return this; }
            public Builder detectActivityLeaks() { return this; }
            public Builder detectAll() { return this; }
            public Builder detectLeakedSqlLiteObjects() { return this; }
            public Builder detectLeakedClosableObjects() { return this; }
            public Builder detectLeakedRegistrationObjects() { return this; }
            public Builder detectFileUriExposure() { return this; }
            public Builder detectCleartextNetwork() { return this; }
            public Builder detectContentUriWithoutPermission() { return this; }
            public Builder detectUntaggedSockets() { return this; }
            public Builder detectNonSdkApiUsage() { return this; }
            public Builder permitNonSdkApiUsage() { return this; }
            public Builder penaltyDeath() { return this; }
            public Builder penaltyDeathOnCleartextNetwork() { return this; }
            public Builder penaltyDeathOnFileUriExposure() { return this; }
            public Builder penaltyLog() { return this; }
            public Builder penaltyDropBox() { return this; }
            public Builder penaltyListener(java.util.concurrent.Executor executor, Object listener) { return this; }
            public VmPolicy build() { return LAX; }
        }
    }
}
