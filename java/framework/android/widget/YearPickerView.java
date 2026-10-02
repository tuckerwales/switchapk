package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.internal.util.InternalRes;
import java.util.Calendar;

/**
 * framework-internal. Displays a selectable list of years (AOSP port).
 */
class YearPickerView extends ListView {
    private final YearAdapter mAdapter;
    private final int mViewSize;
    private final int mChildSize;

    private OnYearSelectedListener mOnYearSelectedListener;

    public YearPickerView(Context context) {
        this(context, null);
    }

    public YearPickerView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }

    public YearPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public YearPickerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final LayoutParams frame = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        setLayoutParams(frame);

        final Resources res = context.getResources();
        mViewSize = res.getDimensionPixelOffset(InternalRes.dimen("datepicker_view_animator_height"));
        mChildSize = res.getDimensionPixelOffset(InternalRes.dimen("datepicker_year_label_height"));

        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final int year = mAdapter.getYearForPosition(position);
                mAdapter.setSelection(year);

                if (mOnYearSelectedListener != null) {
                    mOnYearSelectedListener.onYearChanged(YearPickerView.this, year);
                }
            }
        });

        mAdapter = new YearAdapter(getContext());
        setAdapter(mAdapter);
    }

    public void setOnYearSelectedListener(OnYearSelectedListener listener) {
        mOnYearSelectedListener = listener;
    }

    /**
     * Sets the currently selected year. Jumps immediately to the new year.
     *
     * @param year the target year
     */
    public void setYear(final int year) {
        mAdapter.setSelection(year);

        post(new Runnable() {
            @Override
            public void run() {
                final int position = mAdapter.getPositionForYear(year);
                if (position >= 0 && position < getCount()) {
                    setSelectionCentered(position);
                }
            }
        });
    }

    public void setSelectionCentered(int position) {
        final int offset = mViewSize / 2 - mChildSize / 2;
        setSelectionFromTop(position, offset);
    }

    public void setRange(Calendar min, Calendar max) {
        mAdapter.setRange(min, max);
    }

    private static class YearAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final int mItemLayout;
        private final int mItemTextAppearance;
        private final int mItemTextActivatedAppearance;

        private int mActivatedYear;
        private int mMinYear;
        private int mCount;

        public YearAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
            mItemLayout = InternalRes.layout("year_label_text_view");
            mItemTextAppearance = InternalRes.style("TextAppearance.Material.DatePicker.List.YearLabel");
            mItemTextActivatedAppearance =
                    InternalRes.style("TextAppearance.Material.DatePicker.List.YearLabel.Activated");
        }

        public void setRange(Calendar minDate, Calendar maxDate) {
            final int minYear = minDate.get(Calendar.YEAR);
            final int count = maxDate.get(Calendar.YEAR) - minYear + 1;

            if (mMinYear != minYear || mCount != count) {
                mMinYear = minYear;
                mCount = count;
                notifyDataSetInvalidated();
            }
        }

        public boolean setSelection(int year) {
            if (mActivatedYear != year) {
                mActivatedYear = year;
                notifyDataSetChanged();
                return true;
            }
            return false;
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public Integer getItem(int position) {
            return getYearForPosition(position);
        }

        @Override
        public long getItemId(int position) {
            return getYearForPosition(position);
        }

        public int getPositionForYear(int year) {
            return year - mMinYear;
        }

        public int getYearForPosition(int position) {
            return mMinYear + position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final TextView v;
            final boolean hasNewView = convertView == null;
            if (hasNewView) {
                v = (TextView) mInflater.inflate(mItemLayout, parent, false);
            } else {
                v = (TextView) convertView;
            }

            final int year = getYearForPosition(position);
            final boolean activated = mActivatedYear == year;

            if (hasNewView || v.isActivated() != activated) {
                final int textAppearanceResId;
                if (activated && mItemTextActivatedAppearance != 0) {
                    textAppearanceResId = mItemTextActivatedAppearance;
                } else {
                    textAppearanceResId = mItemTextAppearance;
                }
                v.setTextAppearance(textAppearanceResId);
                v.setActivated(activated);
            }

            v.setText(Integer.toString(year));
            return v;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }
    }

    public int getFirstPositionOffset() {
        final View firstChild = getChildAt(0);
        if (firstChild == null) {
            return 0;
        }
        return firstChild.getTop();
    }

    /**
     * The callback used to indicate the user changed the year.
     */
    public interface OnYearSelectedListener {
        /**
         * Called upon a year change.
         *
         * @param view The view associated with this listener.
         * @param year The year that was set.
         */
        void onYearChanged(YearPickerView view, int year);
    }
}
