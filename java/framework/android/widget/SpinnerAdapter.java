package android.widget;

import android.view.View;
import android.view.ViewGroup;

/** Adapter that can also build the drop-down row (AOSP SpinnerAdapter). */
public interface SpinnerAdapter extends Adapter {
    View getDropDownView(int position, View convertView, ViewGroup parent);
}
