package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import com.android.internal.util.InternalRes;

/**
 * framework-internal. View to show text input based time picker with hour and
 * minute fields and an optional AM/PM spinner (AOSP port).
 */
public class TextInputTimePickerView extends RelativeLayout {
    public static final int HOURS = 0;
    public static final int MINUTES = 1;
    public static final int AMPM = 2;

    private static final int AM = 0;
    private static final int PM = 1;

    private final EditText mHourEditText;
    private final EditText mMinuteEditText;
    private final TextView mInputSeparatorView;
    private final Spinner mAmPmSpinner;
    private final TextView mErrorLabel;
    private final TextView mHourLabel;
    private final TextView mMinuteLabel;

    private boolean mIs24Hour;
    private boolean mHourFormatStartsAtZero;
    private OnValueTypedListener mListener;

    private boolean mErrorShowing;
    private boolean mTimeSet;

    interface OnValueTypedListener {
        void onValueChanged(int inputType, int newValue);
    }

    public TextInputTimePickerView(Context context) {
        this(context, null);
    }

    public TextInputTimePickerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextInputTimePickerView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public TextInputTimePickerView(final Context context, AttributeSet attrs, int defStyle,
            int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);

        inflate(context, InternalRes.layout("time_picker_text_input_material"), this);

        mHourEditText = (EditText) findViewById(InternalRes.viewId("input_hour"));
        mMinuteEditText = (EditText) findViewById(InternalRes.viewId("input_minute"));
        mInputSeparatorView = (TextView) findViewById(InternalRes.viewId("input_separator"));
        mErrorLabel = (TextView) findViewById(InternalRes.viewId("label_error"));
        mHourLabel = (TextView) findViewById(InternalRes.viewId("label_hour"));
        mMinuteLabel = (TextView) findViewById(InternalRes.viewId("label_minute"));

        mHourEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (parseAndSetHourInternal(editable.toString()) && editable.length() > 1) {
                    AccessibilityManager am = AccessibilityManager.getInstance(context);
                    if (!am.isEnabled()) {
                        mMinuteEditText.requestFocus();
                    }
                }
            }
        });

        mMinuteEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                parseAndSetMinuteInternal(editable.toString());
            }
        });

        mAmPmSpinner = (Spinner) findViewById(InternalRes.viewId("am_pm_spinner"));
        final String[] amPmStrings = TimePicker.getAmPmStrings(context);
        ArrayAdapter<CharSequence> adapter =
                new ArrayAdapter<CharSequence>(context, android.R.layout.simple_spinner_dropdown_item);
        adapter.add(TimePickerClockDelegate.obtainVerbatim(amPmStrings[0]));
        adapter.add(TimePickerClockDelegate.obtainVerbatim(amPmStrings[1]));
        mAmPmSpinner.setAdapter(adapter);
        mAmPmSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                    long id) {
                if (position == 0) {
                    mListener.onValueChanged(AMPM, AM);
                } else {
                    mListener.onValueChanged(AMPM, PM);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    void setListener(OnValueTypedListener listener) {
        mListener = listener;
    }

    void setHourFormat(int maxCharLength) {
        mHourEditText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(maxCharLength)});
        mMinuteEditText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(maxCharLength)});
    }

    boolean validateInput() {
        final String hourText = TextUtils.isEmpty(mHourEditText.getText())
                ? mHourEditText.getHint().toString()
                : mHourEditText.getText().toString();
        final String minuteText = TextUtils.isEmpty(mMinuteEditText.getText())
                ? mMinuteEditText.getHint().toString()
                : mMinuteEditText.getText().toString();

        final boolean inputValid = parseAndSetHourInternal(hourText)
                && parseAndSetMinuteInternal(minuteText);
        setError(!inputValid);
        return inputValid;
    }

    void updateSeparator(String separatorText) {
        mInputSeparatorView.setText(separatorText);
    }

    private void setError(boolean enabled) {
        mErrorShowing = enabled;

        mErrorLabel.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        mHourLabel.setVisibility(enabled ? View.INVISIBLE : View.VISIBLE);
        mMinuteLabel.setVisibility(enabled ? View.INVISIBLE : View.VISIBLE);
    }

    private void setTimeSet(boolean timeSet) {
        mTimeSet = mTimeSet || timeSet;
    }

    private boolean isTimeSet() {
        return mTimeSet;
    }

    /**
     * Computes the display value and updates the text of the view.
     * <p>
     * This method should be called whenever the current value or display
     * properties (leading zeroes, max digits) change.
     */
    void updateTextInputValues(int localizedHour, int minute, int amOrPm, boolean is24Hour,
            boolean hourFormatStartsAtZero) {
        final String hourFormat = "%d";
        final String minuteFormat = "%02d";

        mIs24Hour = is24Hour;
        mHourFormatStartsAtZero = hourFormatStartsAtZero;

        mAmPmSpinner.setVisibility(is24Hour ? View.INVISIBLE : View.VISIBLE);

        if (amOrPm == AM) {
            mAmPmSpinner.setSelection(0);
        } else {
            mAmPmSpinner.setSelection(1);
        }

        if (isTimeSet()) {
            mHourEditText.setText(String.format(hourFormat, localizedHour));
            mMinuteEditText.setText(String.format(minuteFormat, minute));
        } else {
            mHourEditText.setHint(String.format(hourFormat, localizedHour));
            mMinuteEditText.setHint(String.format(minuteFormat, minute));
        }

        if (mErrorShowing) {
            validateInput();
        }
    }

    private boolean parseAndSetHourInternal(String input) {
        try {
            final int hour = Integer.parseInt(input);
            if (!isValidLocalizedHour(hour)) {
                return false;
            }
            mListener.onValueChanged(HOURS, getHourOfDayFromLocalizedHour(hour));
            setTimeSet(true);
            return true;
        } catch (NumberFormatException e) {
            // Do nothing since we cannot parse the input.
            return false;
        }
    }

    private boolean parseAndSetMinuteInternal(String input) {
        try {
            final int minutes = Integer.parseInt(input);
            if (minutes < 0 || minutes > 59) {
                return false;
            }
            mListener.onValueChanged(MINUTES, minutes);
            setTimeSet(true);
            return true;
        } catch (NumberFormatException e) {
            // Do nothing since we cannot parse the input.
            return false;
        }
    }

    private boolean isValidLocalizedHour(int localizedHour) {
        final int minHour = mHourFormatStartsAtZero ? 0 : 1;
        final int maxHour = (mIs24Hour ? 23 : 11) + minHour;
        return localizedHour >= minHour && localizedHour <= maxHour;
    }

    private int getHourOfDayFromLocalizedHour(int localizedHour) {
        int hourOfDay = localizedHour;
        if (mIs24Hour) {
            if (!mHourFormatStartsAtZero && localizedHour == 24) {
                hourOfDay = 0;
            }
        } else {
            if (!mHourFormatStartsAtZero && localizedHour == 12) {
                hourOfDay = 0;
            }
            if (mAmPmSpinner.getSelectedItemPosition() == 1) {
                hourOfDay += 12;
            }
        }
        return hourOfDay;
    }
}
