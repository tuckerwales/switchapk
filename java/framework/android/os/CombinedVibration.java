package android.os;

public abstract class CombinedVibration implements Parcelable {
    final int[] mIds;
    final VibrationEffect[] mEffects;

    CombinedVibration(int[] ids, VibrationEffect[] effects) {
        mIds = ids;
        mEffects = effects;
    }

    public static CombinedVibration createParallel(VibrationEffect effect) {
        if (effect == null) throw new IllegalArgumentException("effect must not be null");
        return new CombinedVibration(new int[] {-1}, new VibrationEffect[] {effect}) {};
    }

    public static ParallelCombination startParallel() {
        return new ParallelCombination();
    }

    /** framework-internal: the effect for the default vibrator. */
    VibrationEffect firstEffect() {
        for (int i = 0; i < mIds.length; i++) {
            if (mIds[i] == -1 || mIds[i] == 0) return mEffects[i];
        }
        return mEffects.length > 0 ? mEffects[0] : null;
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mIds.length);
        for (int i = 0; i < mIds.length; i++) {
            dest.writeInt(mIds[i]);
            mEffects[i].writeToParcel(dest, flags);
        }
    }

    public static final Parcelable.Creator<CombinedVibration> CREATOR = new Parcelable.Creator<CombinedVibration>() {
        public CombinedVibration createFromParcel(Parcel in) {
            int n = in.readInt();
            int[] ids = new int[n];
            VibrationEffect[] effects = new VibrationEffect[n];
            for (int i = 0; i < n; i++) {
                ids[i] = in.readInt();
                effects[i] = VibrationEffect.CREATOR.createFromParcel(in);
            }
            return new CombinedVibration(ids, effects) {};
        }

        public CombinedVibration[] newArray(int size) { return new CombinedVibration[size]; }
    };

    public static final class ParallelCombination {
        private final java.util.ArrayList<Integer> mIds = new java.util.ArrayList<Integer>();
        private final java.util.ArrayList<VibrationEffect> mEffects = new java.util.ArrayList<VibrationEffect>();

        ParallelCombination() {}

        public ParallelCombination addVibrator(int vibratorId, VibrationEffect effect) {
            mIds.add(vibratorId);
            mEffects.add(effect);
            return this;
        }

        public CombinedVibration combine() {
            if (mIds.isEmpty()) throw new IllegalStateException("Combination must have at least one element to combine.");
            int[] ids = new int[mIds.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = mIds.get(i);
            return new CombinedVibration(ids, mEffects.toArray(new VibrationEffect[0])) {};
        }
    }
}
