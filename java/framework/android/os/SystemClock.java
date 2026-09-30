package android.os;

public final class SystemClock {
    private SystemClock() {}

    public static void sleep(long ms) {
        long start = uptimeMillis();
        long duration = ms;
        boolean interrupted = false;
        do {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            duration = start + ms - uptimeMillis();
        } while (duration > 0);
        if (interrupted) Thread.currentThread().interrupt();
    }

    public static boolean setCurrentTimeMillis(long millis) { return false; }
    public static long uptimeMillis() { return System.nanoTime() / 1000000L; }
    public static long uptimeNanos() { return System.nanoTime(); }
    public static long elapsedRealtime() { return System.nanoTime() / 1000000L; }
    public static long elapsedRealtimeNanos() { return System.nanoTime(); }
    public static long currentThreadTimeMillis() { return System.nanoTime() / 1000000L; }
    public static long currentThreadTimeMicro() { return System.nanoTime() / 1000L; }
    public static long currentTimeMicro() { return System.currentTimeMillis() * 1000L; }
}
