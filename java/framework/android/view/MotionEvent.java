package android.view;

import android.graphics.Matrix;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Motion event with any number of pointers and batched history, following
 * AOSP MotionEvent semantics. Coordinates are stored as delivered (window
 * coordinates); offsetLocation/transform change what getX/getY report while
 * getRawX/getRawY keep returning screen coordinates.
 */
public final class MotionEvent extends InputEvent implements Parcelable {
    public static final int ACTION_BUTTON_PRESS = 11;
    public static final int ACTION_BUTTON_RELEASE = 12;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_HOVER_ENTER = 9;
    public static final int ACTION_HOVER_EXIT = 10;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_MASK = 255;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_1_DOWN = 5;
    public static final int ACTION_POINTER_1_UP = 6;
    public static final int ACTION_POINTER_2_DOWN = 261;
    public static final int ACTION_POINTER_2_UP = 262;
    public static final int ACTION_POINTER_3_DOWN = 517;
    public static final int ACTION_POINTER_3_UP = 518;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_ID_MASK = 65280;
    public static final int ACTION_POINTER_ID_SHIFT = 8;
    public static final int ACTION_POINTER_INDEX_MASK = 65280;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_SCROLL = 8;
    public static final int ACTION_UP = 1;
    public static final int AXIS_BRAKE = 23;
    public static final int AXIS_DISTANCE = 24;
    public static final int AXIS_GAS = 22;
    public static final int AXIS_GENERIC_1 = 32;
    public static final int AXIS_GENERIC_10 = 41;
    public static final int AXIS_GENERIC_11 = 42;
    public static final int AXIS_GENERIC_12 = 43;
    public static final int AXIS_GENERIC_13 = 44;
    public static final int AXIS_GENERIC_14 = 45;
    public static final int AXIS_GENERIC_15 = 46;
    public static final int AXIS_GENERIC_16 = 47;
    public static final int AXIS_GENERIC_2 = 33;
    public static final int AXIS_GENERIC_3 = 34;
    public static final int AXIS_GENERIC_4 = 35;
    public static final int AXIS_GENERIC_5 = 36;
    public static final int AXIS_GENERIC_6 = 37;
    public static final int AXIS_GENERIC_7 = 38;
    public static final int AXIS_GENERIC_8 = 39;
    public static final int AXIS_GENERIC_9 = 40;
    public static final int AXIS_GESTURE_PINCH_SCALE_FACTOR = 52;
    public static final int AXIS_GESTURE_SCROLL_X_DISTANCE = 50;
    public static final int AXIS_GESTURE_SCROLL_Y_DISTANCE = 51;
    public static final int AXIS_GESTURE_X_OFFSET = 48;
    public static final int AXIS_GESTURE_Y_OFFSET = 49;
    public static final int AXIS_HAT_X = 15;
    public static final int AXIS_HAT_Y = 16;
    public static final int AXIS_HSCROLL = 10;
    public static final int AXIS_LTRIGGER = 17;
    public static final int AXIS_ORIENTATION = 8;
    public static final int AXIS_PRESSURE = 2;
    public static final int AXIS_RELATIVE_X = 27;
    public static final int AXIS_RELATIVE_Y = 28;
    public static final int AXIS_RTRIGGER = 18;
    public static final int AXIS_RUDDER = 20;
    public static final int AXIS_RX = 12;
    public static final int AXIS_RY = 13;
    public static final int AXIS_RZ = 14;
    public static final int AXIS_SCROLL = 26;
    public static final int AXIS_SIZE = 3;
    public static final int AXIS_THROTTLE = 19;
    public static final int AXIS_TILT = 25;
    public static final int AXIS_TOOL_MAJOR = 6;
    public static final int AXIS_TOOL_MINOR = 7;
    public static final int AXIS_TOUCH_MAJOR = 4;
    public static final int AXIS_TOUCH_MINOR = 5;
    public static final int AXIS_VSCROLL = 9;
    public static final int AXIS_WHEEL = 21;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 11;
    public static final int BUTTON_BACK = 8;
    public static final int BUTTON_FORWARD = 16;
    public static final int BUTTON_PRIMARY = 1;
    public static final int BUTTON_SECONDARY = 2;
    public static final int BUTTON_STYLUS_PRIMARY = 32;
    public static final int BUTTON_STYLUS_SECONDARY = 64;
    public static final int BUTTON_TERTIARY = 4;
    public static final int CLASSIFICATION_AMBIGUOUS_GESTURE = 1;
    public static final int CLASSIFICATION_DEEP_PRESS = 2;
    public static final int CLASSIFICATION_NONE = 0;
    public static final int CLASSIFICATION_PINCH = 5;
    public static final int CLASSIFICATION_TWO_FINGER_SWIPE = 3;
    public static final int EDGE_BOTTOM = 2;
    public static final int EDGE_LEFT = 4;
    public static final int EDGE_RIGHT = 8;
    public static final int EDGE_TOP = 1;
    public static final int FLAG_CANCELED = 32;
    public static final int FLAG_WINDOW_IS_OBSCURED = 1;
    public static final int FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 2;
    public static final int INVALID_POINTER_ID = -1;
    public static final int TOOL_TYPE_ERASER = 4;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_MOUSE = 3;
    public static final int TOOL_TYPE_STYLUS = 2;
    public static final int TOOL_TYPE_UNKNOWN = 0;

    static final int NUM_AXES = 53;

    private int mDeviceId;
    private int mSource;
    private int mDisplayId;
    private int mAction;
    private int mActionButton;
    private int mFlags;
    private int mEdgeFlags;
    private int mMetaState;
    private int mButtonState;
    private int mClassification;
    private float mXPrecision = 1f;
    private float mYPrecision = 1f;
    private long mDownTime;
    // pointer properties
    private int mPointerCount;
    private int[] mIds;
    private int[] mToolTypes;
    // samples: index (sample * mPointerCount + pointer) * NUM_AXES + axis; last sample is the current one
    private int mSampleCount;
    private long[] mSampleTimes;
    private float[] mSamples;
    // transform applied to x/y relative to the raw (screen) coordinates
    private float mOffsetX;
    private float mOffsetY;
    private float[] mTransform;  // non-null when a non-translation transform was applied (3x3 row major)
    private float mRawOffsetX;
    private float mRawOffsetY;

    private MotionEvent() {}

    public static final class PointerCoords {
        public float orientation;
        public float pressure;
        public float size;
        public float toolMajor;
        public float toolMinor;
        public float touchMajor;
        public float touchMinor;
        public float x;
        public float y;
        private float[] mOther;

        public PointerCoords() {}

        public PointerCoords(PointerCoords other) { copyFrom(other); }

        public boolean isResampled() { return false; }

        public void clear() {
            orientation = pressure = size = toolMajor = toolMinor = touchMajor = touchMinor = x = y = 0;
            mOther = null;
        }

        public void copyFrom(PointerCoords other) {
            x = other.x;
            y = other.y;
            pressure = other.pressure;
            size = other.size;
            touchMajor = other.touchMajor;
            touchMinor = other.touchMinor;
            toolMajor = other.toolMajor;
            toolMinor = other.toolMinor;
            orientation = other.orientation;
            mOther = other.mOther != null ? other.mOther.clone() : null;
        }

        public float getAxisValue(int axis) {
            switch (axis) {
                case AXIS_X: return x;
                case AXIS_Y: return y;
                case AXIS_PRESSURE: return pressure;
                case AXIS_SIZE: return size;
                case AXIS_TOUCH_MAJOR: return touchMajor;
                case AXIS_TOUCH_MINOR: return touchMinor;
                case AXIS_TOOL_MAJOR: return toolMajor;
                case AXIS_TOOL_MINOR: return toolMinor;
                case AXIS_ORIENTATION: return orientation;
                default:
                    if (axis < 0 || axis >= NUM_AXES) throw new IllegalArgumentException("Axis out of range.");
                    return mOther != null ? mOther[axis] : 0;
            }
        }

        public void setAxisValue(int axis, float value) {
            switch (axis) {
                case AXIS_X: x = value; break;
                case AXIS_Y: y = value; break;
                case AXIS_PRESSURE: pressure = value; break;
                case AXIS_SIZE: size = value; break;
                case AXIS_TOUCH_MAJOR: touchMajor = value; break;
                case AXIS_TOUCH_MINOR: touchMinor = value; break;
                case AXIS_TOOL_MAJOR: toolMajor = value; break;
                case AXIS_TOOL_MINOR: toolMinor = value; break;
                case AXIS_ORIENTATION: orientation = value; break;
                default:
                    if (axis < 0 || axis >= NUM_AXES) throw new IllegalArgumentException("Axis out of range.");
                    if (mOther == null) mOther = new float[NUM_AXES];
                    mOther[axis] = value;
            }
        }
    }

    public static final class PointerProperties {
        public int id;
        public int toolType;

        public PointerProperties() { clear(); }

        public PointerProperties(PointerProperties other) { copyFrom(other); }

        public void clear() {
            id = INVALID_POINTER_ID;
            toolType = TOOL_TYPE_UNKNOWN;
        }

        public void copyFrom(PointerProperties other) {
            id = other.id;
            toolType = other.toolType;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PointerProperties)) return false;
            PointerProperties o = (PointerProperties) other;
            return id == o.id && toolType == o.toolType;
        }

        @Override
        public int hashCode() { return id | (toolType << 8); }
    }

    // ---------------------------------------------------------------- obtain

    private void initialize(int deviceId, int source, int displayId, int action, int actionButton, int flags,
            int edgeFlags, int metaState, int buttonState, float xPrecision, float yPrecision, long downTime,
            long eventTime, int pointerCount, int[] ids, int[] toolTypes, PointerCoords[] coords) {
        if (pointerCount < 1) throw new IllegalArgumentException("pointerCount must be at least 1");
        mDeviceId = deviceId;
        mSource = source;
        mDisplayId = displayId;
        mAction = action;
        mActionButton = actionButton;
        mFlags = flags;
        mEdgeFlags = edgeFlags;
        mMetaState = metaState;
        mButtonState = buttonState;
        mXPrecision = xPrecision;
        mYPrecision = yPrecision;
        mDownTime = downTime;
        mPointerCount = pointerCount;
        mIds = new int[pointerCount];
        mToolTypes = new int[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            mIds[i] = ids[i];
            mToolTypes[i] = toolTypes[i];
        }
        mSampleCount = 0;
        mSampleTimes = new long[2];
        mSamples = new float[2 * pointerCount * NUM_AXES];
        mOffsetX = mOffsetY = 0;
        mRawOffsetX = mRawOffsetY = 0;
        mTransform = null;
        appendSample(eventTime, coords);
    }

    private void appendSample(long eventTime, PointerCoords[] coords) {
        int stride = mPointerCount * NUM_AXES;
        if (mSampleCount == mSampleTimes.length) {
            long[] t = new long[mSampleCount * 2];
            System.arraycopy(mSampleTimes, 0, t, 0, mSampleCount);
            mSampleTimes = t;
            float[] s = new float[mSampleCount * 2 * stride];
            System.arraycopy(mSamples, 0, s, 0, mSampleCount * stride);
            mSamples = s;
        }
        mSampleTimes[mSampleCount] = eventTime;
        int base = mSampleCount * stride;
        for (int p = 0; p < mPointerCount; p++) {
            PointerCoords c = coords[p];
            int o = base + p * NUM_AXES;
            for (int a = 0; a < NUM_AXES; a++) mSamples[o + a] = c.getAxisValue(a);
            // stored coordinates are raw; undo the current offset so getX stays consistent
            mSamples[o + AXIS_X] = unmapX(c.x, c.y);
            mSamples[o + AXIS_Y] = unmapY(c.x, c.y);
        }
        mSampleCount++;
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount,
            PointerProperties[] pointerProperties, PointerCoords[] pointerCoords, int metaState, int buttonState,
            float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int displayId, int flags,
            int classification) {
        MotionEvent ev = obtain(downTime, eventTime, action, pointerCount, pointerProperties, pointerCoords,
                metaState, buttonState, xPrecision, yPrecision, deviceId, edgeFlags, source, flags);
        ev.mDisplayId = displayId;
        ev.mClassification = classification;
        return ev;
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount,
            PointerProperties[] pointerProperties, PointerCoords[] pointerCoords, int metaState, int buttonState,
            float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int flags) {
        int[] ids = new int[pointerCount];
        int[] tools = new int[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            ids[i] = pointerProperties[i].id;
            tools[i] = pointerProperties[i].toolType;
        }
        MotionEvent ev = new MotionEvent();
        ev.initialize(deviceId, source, 0, action, 0, flags, edgeFlags, metaState, buttonState, xPrecision,
                yPrecision, downTime, eventTime, pointerCount, ids, tools, pointerCoords);
        return ev;
    }

    @Deprecated
    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount,
            int[] pointerIds, PointerCoords[] pointerCoords, int metaState, float xPrecision, float yPrecision,
            int deviceId, int edgeFlags, int source, int flags) {
        int[] tools = new int[pointerCount];
        for (int i = 0; i < pointerCount; i++) tools[i] = TOOL_TYPE_UNKNOWN;
        MotionEvent ev = new MotionEvent();
        ev.initialize(deviceId, source, 0, action, 0, flags, edgeFlags, metaState, 0, xPrecision, yPrecision,
                downTime, eventTime, pointerCount, pointerIds, tools, pointerCoords);
        return ev;
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, float pressure,
            float size, int metaState, float xPrecision, float yPrecision, int deviceId, int edgeFlags) {
        return obtain(downTime, eventTime, action, x, y, pressure, size, metaState, xPrecision, yPrecision,
                deviceId, edgeFlags, InputDevice.SOURCE_TOUCHSCREEN, 0, 0);
    }

    /** framework-internal (hidden in AOSP). */
    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, float pressure,
            float size, int metaState, float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source,
            int displayId, int pointerId) {
        PointerCoords c = new PointerCoords();
        c.x = x;
        c.y = y;
        c.pressure = pressure;
        c.size = size;
        MotionEvent ev = new MotionEvent();
        ev.initialize(deviceId, source, displayId, action, 0, 0, edgeFlags, metaState, 0, xPrecision, yPrecision,
                downTime, eventTime, 1, new int[] {pointerId}, new int[] {TOOL_TYPE_FINGER},
                new PointerCoords[] {c});
        return ev;
    }

    @Deprecated
    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount, float x, float y,
            float pressure, float size, int metaState, float xPrecision, float yPrecision, int deviceId,
            int edgeFlags) {
        return obtain(downTime, eventTime, action, x, y, pressure, size, metaState, xPrecision, yPrecision, deviceId,
                edgeFlags);
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        return obtain(downTime, eventTime, action, x, y, 1.0f, 1.0f, metaState, 1.0f, 1.0f, 0, 0);
    }

    public static MotionEvent obtain(MotionEvent other) {
        if (other == null) throw new IllegalArgumentException("other motion event must not be null");
        MotionEvent ev = other.copyHeader();
        ev.mSampleCount = other.mSampleCount;
        ev.mSampleTimes = other.mSampleTimes.clone();
        ev.mSamples = other.mSamples.clone();
        return ev;
    }

    public static MotionEvent obtainNoHistory(MotionEvent other) {
        if (other == null) throw new IllegalArgumentException("other motion event must not be null");
        MotionEvent ev = other.copyHeader();
        int stride = other.mPointerCount * NUM_AXES;
        ev.mSampleCount = 1;
        ev.mSampleTimes = new long[] {other.mSampleTimes[other.mSampleCount - 1]};
        ev.mSamples = new float[stride];
        System.arraycopy(other.mSamples, (other.mSampleCount - 1) * stride, ev.mSamples, 0, stride);
        return ev;
    }

    private MotionEvent copyHeader() {
        MotionEvent ev = new MotionEvent();
        ev.mDeviceId = mDeviceId;
        ev.mSource = mSource;
        ev.mDisplayId = mDisplayId;
        ev.mAction = mAction;
        ev.mActionButton = mActionButton;
        ev.mFlags = mFlags;
        ev.mEdgeFlags = mEdgeFlags;
        ev.mMetaState = mMetaState;
        ev.mButtonState = mButtonState;
        ev.mClassification = mClassification;
        ev.mXPrecision = mXPrecision;
        ev.mYPrecision = mYPrecision;
        ev.mDownTime = mDownTime;
        ev.mPointerCount = mPointerCount;
        ev.mIds = mIds.clone();
        ev.mToolTypes = mToolTypes.clone();
        ev.mOffsetX = mOffsetX;
        ev.mOffsetY = mOffsetY;
        ev.mRawOffsetX = mRawOffsetX;
        ev.mRawOffsetY = mRawOffsetY;
        ev.mTransform = mTransform != null ? mTransform.clone() : null;
        return ev;
    }

    /** framework-internal (hidden in AOSP). */
    public MotionEvent copy() { return obtain(this); }

    @Override
    public void recycle() {}

    // ---------------------------------------------------------------- transforms

    private float mapX(float x, float y) {
        if (mTransform == null) return x + mOffsetX;
        float[] m = mTransform;
        return m[0] * x + m[1] * y + m[2];
    }

    private float mapY(float x, float y) {
        if (mTransform == null) return y + mOffsetY;
        float[] m = mTransform;
        return m[3] * x + m[4] * y + m[5];
    }

    private float unmapX(float x, float y) {
        if (mTransform == null) return x - mOffsetX;
        float[] inv = invert(mTransform);
        return inv[0] * x + inv[1] * y + inv[2];
    }

    private float unmapY(float x, float y) {
        if (mTransform == null) return y - mOffsetY;
        float[] inv = invert(mTransform);
        return inv[3] * x + inv[4] * y + inv[5];
    }

    private static float[] invert(float[] m) {
        float det = m[0] * m[4] - m[1] * m[3];
        if (det == 0) return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
        float id = 1f / det;
        float a = m[4] * id, b = -m[1] * id, d = -m[3] * id, e = m[0] * id;
        return new float[] {a, b, -(a * m[2] + b * m[5]), d, e, -(d * m[2] + e * m[5]), 0, 0, 1};
    }

    public void offsetLocation(float deltaX, float deltaY) {
        if (deltaX == 0 && deltaY == 0) return;
        if (mTransform == null) {
            mOffsetX += deltaX;
            mOffsetY += deltaY;
        } else {
            mTransform[2] += deltaX;
            mTransform[5] += deltaY;
        }
    }

    public void setLocation(float x, float y) {
        float oldX = getX();
        float oldY = getY();
        offsetLocation(x - oldX, y - oldY);
    }

    public void transform(Matrix matrix) {
        if (matrix == null) throw new IllegalArgumentException("matrix must not be null");
        float[] v = new float[9];
        matrix.getValues(v);
        float[] cur = mTransform != null ? mTransform : new float[] {1, 0, mOffsetX, 0, 1, mOffsetY, 0, 0, 1};
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = v[i * 3] * cur[j] + v[i * 3 + 1] * cur[3 + j] + v[i * 3 + 2] * cur[6 + j];
            }
        }
        if (r[0] == 1 && r[1] == 0 && r[3] == 0 && r[4] == 1 && r[6] == 0 && r[7] == 0) {
            mTransform = null;
            mOffsetX = r[2];
            mOffsetY = r[5];
        } else {
            mTransform = r;
        }
    }

    /** framework-internal (hidden in AOSP). Moves the raw coordinates too (window to screen offset). */
    public void setRawOffset(float dx, float dy) {
        mRawOffsetX = dx;
        mRawOffsetY = dy;
    }

    // ---------------------------------------------------------------- accessors

    @Override
    public int getDeviceId() { return mDeviceId; }

    @Override
    public int getSource() { return mSource; }

    @Override
    public void setSource(int source) { mSource = source; }

    /** framework-internal (hidden in AOSP). */
    public int getDisplayId() { return mDisplayId; }

    /** framework-internal (hidden in AOSP). */
    public void setDisplayId(int displayId) { mDisplayId = displayId; }

    public int getAction() { return mAction; }
    public int getActionMasked() { return mAction & ACTION_MASK; }
    public int getActionIndex() { return (mAction & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT; }

    /** framework-internal (hidden in AOSP). */
    public final boolean isTouchEvent() {
        if ((mSource & InputDevice.SOURCE_CLASS_POINTER) == 0) return false;
        switch (getActionMasked()) {
            case ACTION_DOWN: case ACTION_MOVE: case ACTION_UP: case ACTION_CANCEL:
            case ACTION_POINTER_DOWN: case ACTION_POINTER_UP: case ACTION_OUTSIDE:
                return true;
            default:
                return false;
        }
    }

    public int getFlags() { return mFlags; }

    /** framework-internal (hidden in AOSP). */
    public void setFlags(int flags) { mFlags = flags; }

    /** framework-internal (hidden in AOSP). */
    public final boolean isTargetAccessibilityFocus() { return (mFlags & FLAG_TARGET_ACCESSIBILITY_FOCUS) != 0; }

    /** framework-internal (hidden in AOSP). */
    public final void setTargetAccessibilityFocus(boolean targetsFocus) {
        if (targetsFocus) mFlags |= FLAG_TARGET_ACCESSIBILITY_FOCUS;
        else mFlags &= ~FLAG_TARGET_ACCESSIBILITY_FOCUS;
    }

    public long getDownTime() { return mDownTime; }

    /** framework-internal (hidden in AOSP). */
    public void setDownTime(long downTime) { mDownTime = downTime; }

    @Override
    public long getEventTime() { return mSampleTimes[mSampleCount - 1]; }

    @Override
    public long getEventTimeNanos() { return getEventTime() * 1000000L; }

    private float raw(int sample, int pointer, int axis) {
        if (pointer < 0 || pointer >= mPointerCount) {
            throw new IllegalArgumentException("pointerIndex out of range");
        }
        return mSamples[(sample * mPointerCount + pointer) * NUM_AXES + axis];
    }

    private float axis(int sample, int pointer, int axis) {
        if (axis == AXIS_X) return mapX(raw(sample, pointer, AXIS_X), raw(sample, pointer, AXIS_Y));
        if (axis == AXIS_Y) return mapY(raw(sample, pointer, AXIS_X), raw(sample, pointer, AXIS_Y));
        if (axis < 0 || axis >= NUM_AXES) throw new IllegalArgumentException("Axis out of range.");
        return raw(sample, pointer, axis);
    }

    private int cur() { return mSampleCount - 1; }

    public float getX() { return axis(cur(), 0, AXIS_X); }
    public float getY() { return axis(cur(), 0, AXIS_Y); }
    public float getPressure() { return axis(cur(), 0, AXIS_PRESSURE); }
    public float getSize() { return axis(cur(), 0, AXIS_SIZE); }
    public float getTouchMajor() { return axis(cur(), 0, AXIS_TOUCH_MAJOR); }
    public float getTouchMinor() { return axis(cur(), 0, AXIS_TOUCH_MINOR); }
    public float getToolMajor() { return axis(cur(), 0, AXIS_TOOL_MAJOR); }
    public float getToolMinor() { return axis(cur(), 0, AXIS_TOOL_MINOR); }
    public float getOrientation() { return axis(cur(), 0, AXIS_ORIENTATION); }
    public float getAxisValue(int axis) { return axis(cur(), 0, axis); }
    public int getPointerCount() { return mPointerCount; }

    public int getPointerId(int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= mPointerCount) {
            throw new IllegalArgumentException("pointerIndex out of range");
        }
        return mIds[pointerIndex];
    }

    public int getToolType(int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= mPointerCount) {
            throw new IllegalArgumentException("pointerIndex out of range");
        }
        return mToolTypes[pointerIndex];
    }

    public int findPointerIndex(int pointerId) {
        for (int i = 0; i < mPointerCount; i++) if (mIds[i] == pointerId) return i;
        return -1;
    }

    public float getX(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_X); }
    public float getY(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_Y); }
    public float getPressure(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_PRESSURE); }
    public float getSize(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_SIZE); }
    public float getTouchMajor(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_TOUCH_MAJOR); }
    public float getTouchMinor(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_TOUCH_MINOR); }
    public float getToolMajor(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_TOOL_MAJOR); }
    public float getToolMinor(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_TOOL_MINOR); }
    public float getOrientation(int pointerIndex) { return axis(cur(), pointerIndex, AXIS_ORIENTATION); }
    public float getAxisValue(int axis, int pointerIndex) { return axis(cur(), pointerIndex, axis); }

    public void getPointerCoords(int pointerIndex, PointerCoords outPointerCoords) {
        getHistoricalPointerCoords(pointerIndex, HISTORY_CURRENT, outPointerCoords);
    }

    public void getPointerProperties(int pointerIndex, PointerProperties outPointerProperties) {
        outPointerProperties.id = getPointerId(pointerIndex);
        outPointerProperties.toolType = getToolType(pointerIndex);
    }

    public int getMetaState() { return mMetaState; }
    public int getButtonState() { return mButtonState; }

    /** framework-internal (hidden in AOSP). */
    public void setButtonState(int buttonState) { mButtonState = buttonState; }

    public int getClassification() { return mClassification; }
    public int getActionButton() { return mActionButton; }

    /** framework-internal (hidden in AOSP). */
    public void setActionButton(int button) { mActionButton = button; }

    public float getRawX() { return getRawX(0); }
    public float getRawY() { return getRawY(0); }
    public float getRawX(int pointerIndex) { return raw(cur(), pointerIndex, AXIS_X) + mRawOffsetX; }
    public float getRawY(int pointerIndex) { return raw(cur(), pointerIndex, AXIS_Y) + mRawOffsetY; }
    public float getXPrecision() { return mXPrecision; }
    public float getYPrecision() { return mYPrecision; }
    public int getHistorySize() { return mSampleCount - 1; }

    private int hist(int pos) {
        if (pos == HISTORY_CURRENT) return cur();
        if (pos < 0 || pos >= mSampleCount - 1) throw new IllegalArgumentException("historyPos out of range");
        return pos;
    }

    public long getHistoricalEventTime(int pos) { return mSampleTimes[hist(pos)]; }
    public long getHistoricalEventTimeNanos(int pos) { return getHistoricalEventTime(pos) * 1000000L; }
    public float getHistoricalX(int pos) { return axis(hist(pos), 0, AXIS_X); }
    public float getHistoricalY(int pos) { return axis(hist(pos), 0, AXIS_Y); }
    public float getHistoricalPressure(int pos) { return axis(hist(pos), 0, AXIS_PRESSURE); }
    public float getHistoricalSize(int pos) { return axis(hist(pos), 0, AXIS_SIZE); }
    public float getHistoricalTouchMajor(int pos) { return axis(hist(pos), 0, AXIS_TOUCH_MAJOR); }
    public float getHistoricalTouchMinor(int pos) { return axis(hist(pos), 0, AXIS_TOUCH_MINOR); }
    public float getHistoricalToolMajor(int pos) { return axis(hist(pos), 0, AXIS_TOOL_MAJOR); }
    public float getHistoricalToolMinor(int pos) { return axis(hist(pos), 0, AXIS_TOOL_MINOR); }
    public float getHistoricalOrientation(int pos) { return axis(hist(pos), 0, AXIS_ORIENTATION); }
    public float getHistoricalAxisValue(int axis, int pos) { return axis(hist(pos), 0, axis); }
    public float getHistoricalX(int pointerIndex, int pos) { return axis(hist(pos), pointerIndex, AXIS_X); }
    public float getHistoricalY(int pointerIndex, int pos) { return axis(hist(pos), pointerIndex, AXIS_Y); }
    public float getHistoricalPressure(int pointerIndex, int pos) { return axis(hist(pos), pointerIndex, AXIS_PRESSURE); }
    public float getHistoricalSize(int pointerIndex, int pos) { return axis(hist(pos), pointerIndex, AXIS_SIZE); }
    public float getHistoricalTouchMajor(int pointerIndex, int pos) {
        return axis(hist(pos), pointerIndex, AXIS_TOUCH_MAJOR);
    }
    public float getHistoricalTouchMinor(int pointerIndex, int pos) {
        return axis(hist(pos), pointerIndex, AXIS_TOUCH_MINOR);
    }
    public float getHistoricalToolMajor(int pointerIndex, int pos) {
        return axis(hist(pos), pointerIndex, AXIS_TOOL_MAJOR);
    }
    public float getHistoricalToolMinor(int pointerIndex, int pos) {
        return axis(hist(pos), pointerIndex, AXIS_TOOL_MINOR);
    }
    public float getHistoricalOrientation(int pointerIndex, int pos) {
        return axis(hist(pos), pointerIndex, AXIS_ORIENTATION);
    }
    public float getHistoricalAxisValue(int axis, int pointerIndex, int pos) {
        return axis(hist(pos), pointerIndex, axis);
    }

    public void getHistoricalPointerCoords(int pointerIndex, int pos, PointerCoords out) {
        if (out == null) throw new IllegalArgumentException("outPointerCoords must not be null");
        int s = hist(pos);
        out.clear();
        for (int a = 0; a < NUM_AXES; a++) {
            float v = axis(s, pointerIndex, a);
            if (v != 0 || a <= AXIS_ORIENTATION) out.setAxisValue(a, v);
        }
    }

    public int getEdgeFlags() { return mEdgeFlags; }
    public void setEdgeFlags(int flags) { mEdgeFlags = flags; }
    public void setAction(int action) { mAction = action; }

    public void addBatch(long eventTime, float x, float y, float pressure, float size, int metaState) {
        PointerCoords c = new PointerCoords();
        c.x = x;
        c.y = y;
        c.pressure = pressure;
        c.size = size;
        PointerCoords[] coords = new PointerCoords[mPointerCount];
        coords[0] = c;
        for (int i = 1; i < mPointerCount; i++) {
            coords[i] = new PointerCoords();
            getPointerCoords(i, coords[i]);
        }
        appendSample(eventTime, coords);
        mMetaState |= metaState;
    }

    public void addBatch(long eventTime, PointerCoords[] pointerCoords, int metaState) {
        appendSample(eventTime, pointerCoords);
        mMetaState |= metaState;
    }

    /** framework-internal (hidden in AOSP). Appends the samples of a move event with the same pointers. */
    public boolean addBatch(MotionEvent event) {
        if (getActionMasked() != ACTION_MOVE || event.getActionMasked() != ACTION_MOVE) return false;
        if (event.mPointerCount != mPointerCount) return false;
        for (int h = 0; h < event.mSampleCount; h++) {
            PointerCoords[] coords = new PointerCoords[mPointerCount];
            for (int p = 0; p < mPointerCount; p++) {
                coords[p] = new PointerCoords();
                event.getHistoricalPointerCoords(p, h == event.mSampleCount - 1 ? HISTORY_CURRENT : h, coords[p]);
            }
            appendSample(event.mSampleTimes[h], coords);
        }
        return true;
    }

    /**
     * framework-internal (hidden in AOSP). Returns a copy containing only the pointers in idBits,
     * with the action adjusted (used by ViewGroup for split touch dispatch).
     */
    public MotionEvent split(int idBits) {
        int[] map = new int[mPointerCount];
        int newCount = 0;
        int oldAction = mAction;
        int oldActionMasked = oldAction & ACTION_MASK;
        int oldActionPointerIndex = (oldAction & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
        int newActionPointerIndex = -1;
        for (int i = 0; i < mPointerCount; i++) {
            int id = mIds[i];
            if (id >= 0 && id < 32 && (idBits & (1 << id)) != 0) {
                if (i == oldActionPointerIndex) newActionPointerIndex = newCount;
                map[newCount++] = i;
            }
        }
        if (newCount == 0) throw new IllegalArgumentException("idBits did not match any ids in the event");
        int newAction;
        if (oldActionMasked == ACTION_POINTER_DOWN || oldActionMasked == ACTION_POINTER_UP) {
            if (newActionPointerIndex < 0) {
                newAction = ACTION_MOVE;
            } else if (newCount == 1) {
                newAction = oldActionMasked == ACTION_POINTER_DOWN ? ACTION_DOWN
                        : ((mFlags & FLAG_CANCELED) != 0 ? ACTION_CANCEL : ACTION_UP);
            } else {
                newAction = oldActionMasked | (newActionPointerIndex << ACTION_POINTER_INDEX_SHIFT);
            }
        } else {
            newAction = oldAction;
        }
        MotionEvent ev = copyHeader();
        ev.mAction = newAction;
        ev.mPointerCount = newCount;
        ev.mIds = new int[newCount];
        ev.mToolTypes = new int[newCount];
        for (int i = 0; i < newCount; i++) {
            ev.mIds[i] = mIds[map[i]];
            ev.mToolTypes[i] = mToolTypes[map[i]];
        }
        ev.mSampleCount = mSampleCount;
        ev.mSampleTimes = mSampleTimes.clone();
        ev.mSamples = new float[mSampleCount * newCount * NUM_AXES];
        for (int s = 0; s < mSampleCount; s++) {
            for (int i = 0; i < newCount; i++) {
                System.arraycopy(mSamples, (s * mPointerCount + map[i]) * NUM_AXES, ev.mSamples,
                        (s * newCount + i) * NUM_AXES, NUM_AXES);
            }
        }
        return ev;
    }

    /** framework-internal (hidden in AOSP). Bit set of the pointer ids in this event. */
    public int getPointerIdBits() {
        int bits = 0;
        for (int i = 0; i < mPointerCount; i++) if (mIds[i] >= 0 && mIds[i] < 32) bits |= 1 << mIds[i];
        return bits;
    }

    public boolean isButtonPressed(int button) {
        if (button == 0) return false;
        return (getButtonState() & button) == button;
    }

    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder();
        msg.append("MotionEvent { action=").append(actionToString(getAction()));
        for (int i = 0; i < mPointerCount; i++) {
            msg.append(", id[").append(i).append("]=").append(getPointerId(i));
            msg.append(", x[").append(i).append("]=").append(getX(i));
            msg.append(", y[").append(i).append("]=").append(getY(i));
        }
        msg.append(", metaState=").append(mMetaState);
        msg.append(", flags=0x").append(Integer.toHexString(mFlags));
        msg.append(", pointerCount=").append(mPointerCount);
        msg.append(", historySize=").append(getHistorySize());
        msg.append(", eventTime=").append(getEventTime());
        msg.append(", downTime=").append(mDownTime);
        msg.append(", deviceId=").append(mDeviceId);
        msg.append(", source=0x").append(Integer.toHexString(mSource));
        msg.append(" }");
        return msg.toString();
    }

    public static String actionToString(int action) {
        switch (action) {
            case ACTION_DOWN: return "ACTION_DOWN";
            case ACTION_UP: return "ACTION_UP";
            case ACTION_CANCEL: return "ACTION_CANCEL";
            case ACTION_OUTSIDE: return "ACTION_OUTSIDE";
            case ACTION_MOVE: return "ACTION_MOVE";
            case ACTION_HOVER_MOVE: return "ACTION_HOVER_MOVE";
            case ACTION_SCROLL: return "ACTION_SCROLL";
            case ACTION_HOVER_ENTER: return "ACTION_HOVER_ENTER";
            case ACTION_HOVER_EXIT: return "ACTION_HOVER_EXIT";
            case ACTION_BUTTON_PRESS: return "ACTION_BUTTON_PRESS";
            case ACTION_BUTTON_RELEASE: return "ACTION_BUTTON_RELEASE";
        }
        int index = (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
        switch (action & ACTION_MASK) {
            case ACTION_POINTER_DOWN: return "ACTION_POINTER_DOWN(" + index + ")";
            case ACTION_POINTER_UP: return "ACTION_POINTER_UP(" + index + ")";
            default: return Integer.toString(action);
        }
    }

    public static String axisToString(int axis) {
        return axis >= 0 && axis < AXIS_NAMES.length ? AXIS_NAMES[axis] : Integer.toString(axis);
    }

    public static int axisFromString(String symbolicName) {
        if (symbolicName.startsWith("AXIS_")) {
            for (int i = 0; i < AXIS_NAMES.length; i++) if (AXIS_NAMES[i].equals(symbolicName)) return i;
            symbolicName = symbolicName.substring(5);
        }
        try {
            return Integer.parseInt(symbolicName, 10);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static final int FLAG_TARGET_ACCESSIBILITY_FOCUS = 0x40000000;
    private static final int HISTORY_CURRENT = -0x80000000;

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(1);
        out.writeInt(mDeviceId);
        out.writeInt(mSource);
        out.writeInt(mAction);
        out.writeInt(mFlags);
        out.writeInt(mEdgeFlags);
        out.writeInt(mMetaState);
        out.writeInt(mButtonState);
        out.writeFloat(mXPrecision);
        out.writeFloat(mYPrecision);
        out.writeLong(mDownTime);
        out.writeInt(mPointerCount);
        for (int i = 0; i < mPointerCount; i++) {
            out.writeInt(mIds[i]);
            out.writeInt(mToolTypes[i]);
        }
        out.writeInt(mSampleCount);
        for (int s = 0; s < mSampleCount; s++) {
            out.writeLong(mSampleTimes[s]);
            for (int p = 0; p < mPointerCount; p++) {
                for (int a = 0; a < NUM_AXES; a++) out.writeFloat(axis(s, p, a));
            }
        }
    }

    /** framework-internal (hidden in AOSP). */
    public static MotionEvent createFromParcelBody(Parcel in) {
        MotionEvent ev = new MotionEvent();
        int deviceId = in.readInt();
        int source = in.readInt();
        int action = in.readInt();
        int flags = in.readInt();
        int edgeFlags = in.readInt();
        int metaState = in.readInt();
        int buttonState = in.readInt();
        float xPrecision = in.readFloat();
        float yPrecision = in.readFloat();
        long downTime = in.readLong();
        int n = in.readInt();
        int[] ids = new int[n];
        int[] tools = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = in.readInt();
            tools[i] = in.readInt();
        }
        int samples = in.readInt();
        for (int s = 0; s < samples; s++) {
            long t = in.readLong();
            PointerCoords[] coords = new PointerCoords[n];
            for (int p = 0; p < n; p++) {
                coords[p] = new PointerCoords();
                for (int a = 0; a < NUM_AXES; a++) coords[p].setAxisValue(a, in.readFloat());
            }
            if (s == 0) {
                ev.initialize(deviceId, source, 0, action, 0, flags, edgeFlags, metaState, buttonState, xPrecision,
                        yPrecision, downTime, t, n, ids, tools, coords);
            } else {
                ev.appendSample(t, coords);
            }
        }
        return ev;
    }

    public static final Parcelable.Creator<MotionEvent> CREATOR = new Parcelable.Creator<MotionEvent>() {
        public MotionEvent createFromParcel(Parcel in) {
            in.readInt();
            return MotionEvent.createFromParcelBody(in);
        }

        public MotionEvent[] newArray(int size) { return new MotionEvent[size]; }
    };

    private static final String[] AXIS_NAMES = {
            "AXIS_X",
            "AXIS_Y",
            "AXIS_PRESSURE",
            "AXIS_SIZE",
            "AXIS_TOUCH_MAJOR",
            "AXIS_TOUCH_MINOR",
            "AXIS_TOOL_MAJOR",
            "AXIS_TOOL_MINOR",
            "AXIS_ORIENTATION",
            "AXIS_VSCROLL",
            "AXIS_HSCROLL",
            "AXIS_Z",
            "AXIS_RX",
            "AXIS_RY",
            "AXIS_RZ",
            "AXIS_HAT_X",
            "AXIS_HAT_Y",
            "AXIS_LTRIGGER",
            "AXIS_RTRIGGER",
            "AXIS_THROTTLE",
            "AXIS_RUDDER",
            "AXIS_WHEEL",
            "AXIS_GAS",
            "AXIS_BRAKE",
            "AXIS_DISTANCE",
            "AXIS_TILT",
            "AXIS_SCROLL",
            "AXIS_RELATIVE_X",
            "AXIS_RELATIVE_Y",
            "29",
            "30",
            "31",
            "AXIS_GENERIC_1",
            "AXIS_GENERIC_2",
            "AXIS_GENERIC_3",
            "AXIS_GENERIC_4",
            "AXIS_GENERIC_5",
            "AXIS_GENERIC_6",
            "AXIS_GENERIC_7",
            "AXIS_GENERIC_8",
            "AXIS_GENERIC_9",
            "AXIS_GENERIC_10",
            "AXIS_GENERIC_11",
            "AXIS_GENERIC_12",
            "AXIS_GENERIC_13",
            "AXIS_GENERIC_14",
            "AXIS_GENERIC_15",
            "AXIS_GENERIC_16",
            "AXIS_GESTURE_X_OFFSET",
            "AXIS_GESTURE_Y_OFFSET",
            "AXIS_GESTURE_SCROLL_X_DISTANCE",
            "AXIS_GESTURE_SCROLL_Y_DISTANCE",
            "AXIS_GESTURE_PINCH_SCALE_FACTOR"
    };
}
