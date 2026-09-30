package java.math;

public enum RoundingMode {
    UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY;

    public static RoundingMode valueOf(int rm) {
        switch (rm) {
            case 0: return UP;
            case 1: return DOWN;
            case 2: return CEILING;
            case 3: return FLOOR;
            case 4: return HALF_UP;
            case 5: return HALF_DOWN;
            case 6: return HALF_EVEN;
            case 7: return UNNECESSARY;
            default: throw new IllegalArgumentException("argument out of range");
        }
    }
}
