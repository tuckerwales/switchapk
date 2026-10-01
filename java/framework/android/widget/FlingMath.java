package android.widget;

/**
 * Spline fling tables shared by Scroller and OverScroller (AOSP).
 * Not an Android API type.
 */
final class FlingMath {
    static final int NB_SAMPLES = 100;
    static final float[] SPLINE_POSITION = new float[NB_SAMPLES + 1];
    static final float[] SPLINE_TIME = new float[NB_SAMPLES + 1];

    static final float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
    static final float INFLEXION = 0.35f;
    static final float START_TENSION = 0.5f;
    static final float END_TENSION = 1.0f;
    static final float P1 = START_TENSION * INFLEXION;
    static final float P2 = 1.0f - END_TENSION * (1.0f - INFLEXION);

    /** g (m/s^2), SensorManager.GRAVITY_EARTH. */
    static final float GRAVITY = 9.80665f;
    static final float INCH_PER_METER = 39.37f;
    /** Look-and-feel scale on the physical coefficient. */
    static final float LOOK = 0.84f;

    private static final float VISCOUS_SCALE = 8.0f;
    private static final float VISCOUS_NORMALIZE;
    private static final float VISCOUS_OFFSET;

    static {
        float xMin = 0.0f;
        float yMin = 0.0f;
        for (int i = 0; i < NB_SAMPLES; i++) {
            float alpha = (float) i / NB_SAMPLES;
            float xMax = 1.0f;
            float x = 0f;
            float coef = 0f;
            for (int n = 0; n < 40; n++) {
                x = xMin + (xMax - xMin) / 2.0f;
                coef = 3.0f * x * (1.0f - x);
                float tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x;
                if (Math.abs(tx - alpha) < 1E-5f) break;
                if (tx > alpha) xMax = x;
                else xMin = x;
            }
            SPLINE_POSITION[i] = coef * ((1.0f - x) * START_TENSION + x) + x * x * x;

            float yMax = 1.0f;
            float y = 0f;
            for (int n = 0; n < 40; n++) {
                y = yMin + (yMax - yMin) / 2.0f;
                coef = 3.0f * y * (1.0f - y);
                float dy = coef * ((1.0f - y) * START_TENSION + y) + y * y * y;
                if (Math.abs(dy - alpha) < 1E-5f) break;
                if (dy > alpha) yMax = y;
                else yMin = y;
            }
            SPLINE_TIME[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y;
        }
        SPLINE_POSITION[NB_SAMPLES] = SPLINE_TIME[NB_SAMPLES] = 1.0f;

        VISCOUS_NORMALIZE = 1.0f / viscousFluid(1.0f);
        VISCOUS_OFFSET = 1.0f - VISCOUS_NORMALIZE * viscousFluid(1.0f);
    }

    private FlingMath() {}

    static float pixelsPerInch(float density) { return density * 160.0f; }

    /** Deceleration in pixels/s^2 for a friction coefficient. */
    static float deceleration(float ppi, float friction) {
        return GRAVITY * INCH_PER_METER * ppi * friction;
    }

    static double splineDeceleration(float velocity, float friction, float physicalCoeff) {
        return Math.log(INFLEXION * Math.abs(velocity) / (friction * physicalCoeff));
    }

    static double flingDistance(float velocity, float friction, float physicalCoeff) {
        double l = splineDeceleration(velocity, friction, physicalCoeff);
        double decelMinusOne = DECELERATION_RATE - 1.0;
        return friction * physicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }

    static int flingDuration(float velocity, float friction, float physicalCoeff) {
        double l = splineDeceleration(velocity, friction, physicalCoeff);
        double decelMinusOne = DECELERATION_RATE - 1.0;
        return (int) (1000.0 * Math.exp(l / decelMinusOne));
    }

    /** Distance coefficient and, via out[1], velocity coefficient for time t in 0..1. */
    static float distanceCoef(float t, float[] outVelocityCoef) {
        float distanceCoef = 1f;
        float velocityCoef = 0f;
        int index = (int) (NB_SAMPLES * t);
        if (index < NB_SAMPLES) {
            float tInf = (float) index / NB_SAMPLES;
            float tSup = (float) (index + 1) / NB_SAMPLES;
            float dInf = SPLINE_POSITION[index];
            float dSup = SPLINE_POSITION[index + 1];
            velocityCoef = (dSup - dInf) / (tSup - tInf);
            distanceCoef = dInf + (t - tInf) * velocityCoef;
        }
        if (outVelocityCoef != null) outVelocityCoef[0] = velocityCoef;
        return distanceCoef;
    }

    /** Shortens a spline duration when the end position is clamped closer than the natural stop. */
    static int adjustDuration(int start, int oldFinal, int newFinal, int duration) {
        int oldDistance = oldFinal - start;
        if (oldDistance == 0) return duration;
        float x = Math.abs((float) (newFinal - start) / oldDistance);
        int index = (int) (NB_SAMPLES * x);
        if (index < NB_SAMPLES) {
            float xInf = (float) index / NB_SAMPLES;
            float xSup = (float) (index + 1) / NB_SAMPLES;
            float tInf = SPLINE_TIME[index];
            float tSup = SPLINE_TIME[index + 1];
            float timeCoef = tInf + (x - xInf) / (xSup - xInf) * (tSup - tInf);
            duration = (int) (duration * timeCoef);
        }
        return duration;
    }

    /** Default scroll interpolator (AOSP ViscousFluidInterpolator). */
    static float viscous(float input) {
        float interpolated = VISCOUS_NORMALIZE * viscousFluid(input);
        if (interpolated > 0) return interpolated + VISCOUS_OFFSET;
        return interpolated;
    }

    private static float viscousFluid(float x) {
        x *= VISCOUS_SCALE;
        if (x < 1.0f) {
            x -= (1.0f - (float) Math.exp(-x));
        } else {
            float start = 0.36787944117f;
            x = 1.0f - (float) Math.exp(1.0f - x);
            x = start + x * (1.0f - start);
        }
        return x;
    }
}
