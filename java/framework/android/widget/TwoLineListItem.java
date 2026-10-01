package android.widget;

import android.content.Context;
import android.util.AttributeSet;

/** A list row with a title (text1) and a second line (text2) (AOSP TwoLineListItem, deprecated). */
@Deprecated
public class TwoLineListItem extends RelativeLayout {
    private TextView mText1;
    private TextView mText2;

    public TwoLineListItem(Context context) { this(context, null, 0); }

    public TwoLineListItem(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public TwoLineListItem(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public TwoLineListItem(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText1 = (TextView) findViewById(android.R.id.text1);
        mText2 = (TextView) findViewById(android.R.id.text2);
    }

    public TextView getText1() { return mText1; }

    public TextView getText2() { return mText2; }

    @Override
    public CharSequence getAccessibilityClassName() { return TwoLineListItem.class.getName(); }
}
