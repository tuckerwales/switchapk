package java.util;

import java.lang.reflect.Array;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Arrays {
    private Arrays() {
    }

    static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }
    }

    public static void sort(int[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(int[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(int[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(int[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            int pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (a[j] < pivot) {
                    int t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            int t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            int x = a[i];
            int j = i;
            while (j > lo && x < a[j - 1]) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(int[] a, int key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(int[] a, int fromIndex, int toIndex, int key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Integer.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static void sort(long[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(long[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(long[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(long[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            long pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (a[j] < pivot) {
                    long t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            long t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            long x = a[i];
            int j = i;
            while (j > lo && x < a[j - 1]) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(long[] a, long key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(long[] a, int fromIndex, int toIndex, long key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Long.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static void sort(short[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(short[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(short[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(short[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            short pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (a[j] < pivot) {
                    short t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            short t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            short x = a[i];
            int j = i;
            while (j > lo && x < a[j - 1]) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(short[] a, short key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(short[] a, int fromIndex, int toIndex, short key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Short.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static void sort(char[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(char[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(char[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(char[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            char pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (a[j] < pivot) {
                    char t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            char t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            char x = a[i];
            int j = i;
            while (j > lo && x < a[j - 1]) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(char[] a, char key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(char[] a, int fromIndex, int toIndex, char key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Character.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static void sort(byte[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(byte[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(byte[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(byte[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            byte pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (a[j] < pivot) {
                    byte t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            byte t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            byte x = a[i];
            int j = i;
            while (j > lo && x < a[j - 1]) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(byte[] a, byte key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(byte[] a, int fromIndex, int toIndex, byte key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Byte.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static void sort(float[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(float[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(float[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(float[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            float pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (Float.compare(a[j], pivot) < 0) {
                    float t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            float t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            float x = a[i];
            int j = i;
            while (j > lo && Float.compare(x, a[j - 1]) < 0) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(float[] a, float key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(float[] a, int fromIndex, int toIndex, float key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Float.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static void sort(double[] a) {
        sort(a, 0, a.length);
    }

    public static void parallelSort(double[] a) {
        sort(a, 0, a.length);
    }

    public static void sort(double[] a, int fromIndex, int toIndex) {
        rangeCheck(a.length, fromIndex, toIndex);
        qsort(a, fromIndex, toIndex - 1);
    }

    private static void qsort(double[] a, int lo, int hi) {
        while (hi - lo > 16) {
            int mid = (lo + hi) >>> 1;
            double pivot = a[mid];
            a[mid] = a[hi];
            a[hi] = pivot;
            int store = lo;
            for (int j = lo; j < hi; j++) {
                if (Double.compare(a[j], pivot) < 0) {
                    double t = a[j];
                    a[j] = a[store];
                    a[store] = t;
                    store++;
                }
            }
            double t = a[store];
            a[store] = a[hi];
            a[hi] = t;
            if (store - lo < hi - store) {
                qsort(a, lo, store - 1);
                lo = store + 1;
            } else {
                qsort(a, store + 1, hi);
                hi = store - 1;
            }
        }
        for (int i = lo + 1; i <= hi; i++) {
            double x = a[i];
            int j = i;
            while (j > lo && Double.compare(x, a[j - 1]) < 0) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = x;
        }
    }

    public static int binarySearch(double[] a, double key) {
        return binarySearch(a, 0, a.length, key);
    }

    public static int binarySearch(double[] a, int fromIndex, int toIndex, double key) {
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int c = Double.compare(a[mid], key);
            if (c < 0) {
                low = mid + 1;
            } else if (c > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static boolean equals(int[] a, int[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(int[] a, int val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(int[] a, int fromIndex, int toIndex, int val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static int[] copyOf(int[] original, int newLength) {
        int[] copy = new int[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static int[] copyOfRange(int[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        int[] copy = new int[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(int[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (int e : a) {
            result = 31 * result + e;
        }
        return result;
    }

    public static String toString(int[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(long[] a, long[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(long[] a, long val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(long[] a, int fromIndex, int toIndex, long val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static long[] copyOf(long[] original, int newLength) {
        long[] copy = new long[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static long[] copyOfRange(long[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        long[] copy = new long[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(long[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (long e : a) {
            result = 31 * result + (int) (e ^ (e >>> 32));
        }
        return result;
    }

    public static String toString(long[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(short[] a, short[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(short[] a, short val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(short[] a, int fromIndex, int toIndex, short val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static short[] copyOf(short[] original, int newLength) {
        short[] copy = new short[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static short[] copyOfRange(short[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        short[] copy = new short[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(short[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (short e : a) {
            result = 31 * result + e;
        }
        return result;
    }

    public static String toString(short[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(char[] a, char[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(char[] a, char val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(char[] a, int fromIndex, int toIndex, char val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static char[] copyOf(char[] original, int newLength) {
        char[] copy = new char[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static char[] copyOfRange(char[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        char[] copy = new char[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(char[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (char e : a) {
            result = 31 * result + e;
        }
        return result;
    }

    public static String toString(char[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(byte[] a, byte[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(byte[] a, byte val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(byte[] a, int fromIndex, int toIndex, byte val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static byte[] copyOf(byte[] original, int newLength) {
        byte[] copy = new byte[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static byte[] copyOfRange(byte[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        byte[] copy = new byte[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(byte[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (byte e : a) {
            result = 31 * result + e;
        }
        return result;
    }

    public static String toString(byte[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(float[] a, float[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Float.compare(a[i], a2[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    public static void fill(float[] a, float val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(float[] a, int fromIndex, int toIndex, float val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static float[] copyOf(float[] original, int newLength) {
        float[] copy = new float[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static float[] copyOfRange(float[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        float[] copy = new float[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(float[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (float e : a) {
            result = 31 * result + Float.floatToIntBits(e);
        }
        return result;
    }

    public static String toString(float[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(double[] a, double[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Double.compare(a[i], a2[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    public static void fill(double[] a, double val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(double[] a, int fromIndex, int toIndex, double val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static double[] copyOf(double[] original, int newLength) {
        double[] copy = new double[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static double[] copyOfRange(double[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        double[] copy = new double[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(double[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (double e : a) {
            result = 31 * result + Double.hashCode(e);
        }
        return result;
    }

    public static String toString(double[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static boolean equals(boolean[] a, boolean[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    public static void fill(boolean[] a, boolean val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(boolean[] a, int fromIndex, int toIndex, boolean val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    public static boolean[] copyOf(boolean[] original, int newLength) {
        boolean[] copy = new boolean[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    public static boolean[] copyOfRange(boolean[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        boolean[] copy = new boolean[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    public static int hashCode(boolean[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (boolean e : a) {
            result = 31 * result + (e ? 1231 : 1237);
        }
        return result;
    }

    public static String toString(boolean[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(a[i]);
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static void sort(Object[] a) {
        sort(a, 0, a.length, null);
    }

    public static void sort(Object[] a, int fromIndex, int toIndex) {
        sort(a, fromIndex, toIndex, null);
    }

    public static <T> void sort(T[] a, Comparator<? super T> c) {
        sort(a, 0, a.length, c);
    }

    public static <T> void parallelSort(T[] a, Comparator<? super T> c) {
        sort(a, 0, a.length, c);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void sort(T[] a, int fromIndex, int toIndex, Comparator<? super T> c) {
        rangeCheck(a.length, fromIndex, toIndex);
        if (c == null) {
            c = (Comparator) Collections.NATURAL_ORDER;
        }
        Object[] aux = copyOfRange(a, fromIndex, toIndex);
        mergeSort(aux, a, fromIndex, toIndex, -fromIndex, (Comparator<Object>) c);
    }

    private static void mergeSort(Object[] src, Object[] dest, int low, int high, int off, Comparator<Object> c) {
        int length = high - low;
        if (length < 7) {
            for (int i = low; i < high; i++) {
                for (int j = i; j > low && c.compare(dest[j - 1], dest[j]) > 0; j--) {
                    Object t = dest[j];
                    dest[j] = dest[j - 1];
                    dest[j - 1] = t;
                }
            }
            return;
        }
        int destLow = low;
        int destHigh = high;
        low += off;
        high += off;
        int mid = (low + high) >>> 1;
        mergeSort(dest, src, low, mid, -off, c);
        mergeSort(dest, src, mid, high, -off, c);
        if (c.compare(src[mid - 1], src[mid]) <= 0) {
            System.arraycopy(src, low, dest, destLow, length);
            return;
        }
        for (int i = destLow, p = low, q = mid; i < destHigh; i++) {
            if (q >= high || p < mid && c.compare(src[p], src[q]) <= 0) {
                dest[i] = src[p++];
            } else {
                dest[i] = src[q++];
            }
        }
    }

    public static int binarySearch(Object[] a, Object key) {
        return binarySearch(a, 0, a.length, key, null);
    }

    public static <T> int binarySearch(T[] a, T key, Comparator<? super T> c) {
        return binarySearch(a, 0, a.length, key, c);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> int binarySearch(T[] a, int fromIndex, int toIndex, T key, Comparator<? super T> c) {
        if (c == null) {
            c = (Comparator) Collections.NATURAL_ORDER;
        }
        int low = fromIndex;
        int high = toIndex - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = c.compare(a[mid], key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static boolean equals(Object[] a, Object[] a2) {
        if (a == a2) {
            return true;
        }
        if (a == null || a2 == null || a2.length != a.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!Objects.equals(a[i], a2[i])) {
                return false;
            }
        }
        return true;
    }

    public static void fill(Object[] a, Object val) {
        for (int i = 0, len = a.length; i < len; i++) {
            a[i] = val;
        }
    }

    public static void fill(Object[] a, int fromIndex, int toIndex, Object val) {
        rangeCheck(a.length, fromIndex, toIndex);
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = val;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] copyOf(T[] original, int newLength) {
        return (T[]) copyOf(original, newLength, original.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T, U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
        T[] copy = ((Object) newType == (Object) Object[].class) ? (T[]) new Object[newLength]
                : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] copyOfRange(T[] original, int from, int to) {
        return copyOfRange(original, from, to, (Class<? extends T[]>) original.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T, U> T[] copyOfRange(U[] original, int from, int to, Class<? extends T[]> newType) {
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        T[] copy = ((Object) newType == (Object) Object[].class) ? (T[]) new Object[newLength]
                : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    @SafeVarargs
    public static <T> List<T> asList(T... a) {
        return new ArrayAsList<T>(a);
    }

    private static class ArrayAsList<E> extends AbstractList<E> implements RandomAccess, java.io.Serializable {
        private final E[] a;

        ArrayAsList(E[] array) {
            a = Objects.requireNonNull(array);
        }

        public int size() {
            return a.length;
        }

        public Object[] toArray() {
            return Arrays.copyOf(a, a.length, Object[].class);
        }

        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] b) {
            int size = size();
            if (b.length < size) {
                return Arrays.copyOf(this.a, size, (Class<? extends T[]>) b.getClass());
            }
            System.arraycopy(this.a, 0, b, 0, size);
            if (b.length > size) {
                b[size] = null;
            }
            return b;
        }

        public E get(int index) {
            return a[index];
        }

        public E set(int index, E element) {
            E oldValue = a[index];
            a[index] = element;
            return oldValue;
        }

        public int indexOf(Object o) {
            for (int i = 0; i < a.length; i++) {
                if (Objects.equals(o, a[i])) {
                    return i;
                }
            }
            return -1;
        }

        public boolean contains(Object o) {
            return indexOf(o) >= 0;
        }

        public void sort(Comparator<? super E> c) {
            Arrays.sort(a, c);
        }
    }

    public static int hashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (Object element : a) {
            result = 31 * result + (element == null ? 0 : element.hashCode());
        }
        return result;
    }

    public static int deepHashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;
        for (Object element : a) {
            int elementHash;
            if (element instanceof Object[]) {
                elementHash = deepHashCode((Object[]) element);
            } else if (element instanceof int[]) {
                elementHash = hashCode((int[]) element);
            } else if (element instanceof long[]) {
                elementHash = hashCode((long[]) element);
            } else if (element instanceof byte[]) {
                elementHash = hashCode((byte[]) element);
            } else if (element instanceof char[]) {
                elementHash = hashCode((char[]) element);
            } else if (element instanceof float[]) {
                elementHash = hashCode((float[]) element);
            } else if (element instanceof double[]) {
                elementHash = hashCode((double[]) element);
            } else if (element != null) {
                elementHash = element.hashCode();
            } else {
                elementHash = 0;
            }
            result = 31 * result + elementHash;
        }
        return result;
    }

    public static boolean deepEquals(Object[] a1, Object[] a2) {
        if (a1 == a2) {
            return true;
        }
        if (a1 == null || a2 == null) {
            return false;
        }
        int length = a1.length;
        if (a2.length != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            Object e1 = a1[i];
            Object e2 = a2[i];
            if (e1 == e2) {
                continue;
            }
            if (e1 == null || !deepEquals0(e1, e2)) {
                return false;
            }
        }
        return true;
    }

    static boolean deepEquals0(Object e1, Object e2) {
        if (e1 instanceof Object[] && e2 instanceof Object[]) {
            return deepEquals((Object[]) e1, (Object[]) e2);
        } else if (e1 instanceof int[] && e2 instanceof int[]) {
            return equals((int[]) e1, (int[]) e2);
        } else if (e1 instanceof long[] && e2 instanceof long[]) {
            return equals((long[]) e1, (long[]) e2);
        } else if (e1 instanceof byte[] && e2 instanceof byte[]) {
            return equals((byte[]) e1, (byte[]) e2);
        } else if (e1 instanceof char[] && e2 instanceof char[]) {
            return equals((char[]) e1, (char[]) e2);
        } else if (e1 instanceof float[] && e2 instanceof float[]) {
            return equals((float[]) e1, (float[]) e2);
        } else if (e1 instanceof double[] && e2 instanceof double[]) {
            return equals((double[]) e1, (double[]) e2);
        } else if (e1 instanceof boolean[] && e2 instanceof boolean[]) {
            return equals((boolean[]) e1, (boolean[]) e2);
        }
        return e1.equals(e2);
    }

    public static String toString(Object[] a) {
        if (a == null) {
            return "null";
        }
        int iMax = a.length - 1;
        if (iMax == -1) {
            return "[]";
        }
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0;; i++) {
            b.append(String.valueOf(a[i]));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(", ");
        }
    }

    public static String deepToString(Object[] a) {
        if (a == null) {
            return "null";
        }
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                buf.append(", ");
            }
            Object element = a[i];
            if (element instanceof Object[]) {
                buf.append(deepToString((Object[]) element));
            } else if (element instanceof int[]) {
                buf.append(toString((int[]) element));
            } else if (element instanceof long[]) {
                buf.append(toString((long[]) element));
            } else if (element instanceof float[]) {
                buf.append(toString((float[]) element));
            } else if (element instanceof double[]) {
                buf.append(toString((double[]) element));
            } else if (element instanceof byte[]) {
                buf.append(toString((byte[]) element));
            } else if (element instanceof char[]) {
                buf.append(toString((char[]) element));
            } else if (element instanceof boolean[]) {
                buf.append(toString((boolean[]) element));
            } else {
                buf.append(String.valueOf(element));
            }
        }
        buf.append(']');
        return buf.toString();
    }

    public static <T> void setAll(T[] array, IntFunction<? extends T> generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.apply(i);
        }
    }

    public static void setAll(int[] array, IntUnaryOperator generator) {
        for (int i = 0; i < array.length; i++) {
            array[i] = generator.applyAsInt(i);
        }
    }

    public static <T> Stream<T> stream(T[] array) {
        return Stream.of(array);
    }

    public static <T> Stream<T> stream(T[] array, int startInclusive, int endExclusive) {
        return Stream.of(copyOfRange(array, startInclusive, endExclusive));
    }

    public static IntStream stream(int[] array) {
        return IntStream.of(array);
    }

    public static <T> Spliterator<T> spliterator(T[] array) {
        return Spliterators.spliterator(asList(array), 0);
    }
}
