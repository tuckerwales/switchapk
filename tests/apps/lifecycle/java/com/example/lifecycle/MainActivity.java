package com.example.lifecycle;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Counter, note and fragment state that must survive recreation (dock switch),
 * plus a result from DetailActivity. Band colour = count, strip colour = last result.
 */
public class MainActivity extends Activity {
    static final int[] COUNT_COLORS = { 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA };
    static final int REQUEST_PICK = 7;

    private int mCount;
    private int mResult;
    private View mBand;
    private View mStrip;
    private TextView mStatus;
    private EditText mNote;

    private static void log(String event) { LifeApp.log("Main." + event); }

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        log("onCreate " + (state != null ? "restored" : "fresh"));
        if (state != null) {
            mCount = state.getInt("count");
            mResult = state.getInt("result");
        }
        Object retained = getLastNonConfigurationInstance();
        if (retained != null) log("retained " + retained);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        mBand = new View(this);
        root.addView(mBand, new LinearLayout.LayoutParams(-1, dp(60)));
        mStrip = new View(this);
        root.addView(mStrip, new LinearLayout.LayoutParams(-1, dp(20)));
        mStatus = new TextView(this);
        mStatus.setTextSize(18);
        root.addView(mStatus);
        LinearLayout row = new LinearLayout(this);
        Button count = new Button(this);
        count.setId(R.id.count);
        count.setText("Count");
        count.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCount++;
                update();
            }
        });
        row.addView(count, new LinearLayout.LayoutParams(dp(160), -2));
        Button open = new Button(this);
        open.setId(R.id.open);
        open.setText("Open");
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra("item", 3);
                startActivityForResult(intent, REQUEST_PICK);
            }
        });
        row.addView(open, new LinearLayout.LayoutParams(dp(160), -2));
        mNote = new EditText(this);
        mNote.setId(R.id.note);
        mNote.setSingleLine(true);
        row.addView(mNote, new LinearLayout.LayoutParams(dp(200), -2));
        root.addView(row);
        FrameLayout frag = new FrameLayout(this);
        frag.setId(R.id.frag);
        root.addView(frag, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        if (state == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.frag, CounterFragment.create(5), "counter")
                    .add(new RetainedFragment(), "retained")
                    .commit();
        }
        update();
    }

    void update() {
        mBand.setBackgroundColor(COUNT_COLORS[mCount % COUNT_COLORS.length]);
        int strip = 0xFF9E9E9E;
        if (mResult == 1) strip = 0xFF1E88E5;
        else if (mResult == 2) strip = 0xFF212121;
        else if (mResult == 3) strip = 0xFF8E24AA;
        mStrip.setBackgroundColor(strip);
        mStatus.setText("count " + mCount + " result " + mResult);
    }

    @Override
    protected void onRestart() { super.onRestart(); log("onRestart"); }

    @Override
    protected void onStart() { super.onStart(); log("onStart"); }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        log("onRestoreInstanceState");
    }

    @Override
    protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        log("onPostCreate note=" + mNote.getText());
    }

    @Override
    protected void onResume() { super.onResume(); log("onResume"); }

    @Override
    protected void onPause() { super.onPause(); log("onPause"); }

    @Override
    protected void onStop() { super.onStop(); log("onStop"); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("count", mCount);
        out.putInt("result", mResult);
        log("onSaveInstanceState");
    }

    @Override
    public Object onRetainNonConfigurationInstance() { return "token" + mCount; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log("onDestroy changing=" + isChangingConfigurations());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int picked = data != null ? data.getIntExtra("picked", 0) : 0;
        log("onActivityResult " + requestCode + " " + resultCode + " " + picked);
        if (requestCode != REQUEST_PICK) return;
        if (resultCode == RESULT_OK) mResult = picked == 6 ? 1 : 3;
        else mResult = 2;
        update();
    }

    /** Child fragment with its own saved state and arguments. */
    public static class CounterFragment extends Fragment {
        private int mClicks;
        private View mSwatch;

        static CounterFragment create(int start) {
            CounterFragment f = new CounterFragment();
            Bundle args = new Bundle();
            args.putInt("start", start);
            f.setArguments(args);
            return f;
        }

        private static void log(String event) { LifeApp.log("Counter." + event); }

        @Override
        public void onCreate(Bundle state) {
            super.onCreate(state);
            mClicks = state != null ? state.getInt("clicks") : 0;
            log("onCreate " + (state != null ? "restored" : "fresh") + " clicks=" + mClicks
                    + " start=" + getArguments().getInt("start"));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
            log("onCreateView");
            final MainActivity host = (MainActivity) getActivity();
            LinearLayout row = new LinearLayout(host);
            mSwatch = new View(host);
            row.addView(mSwatch, new LinearLayout.LayoutParams(host.dp(120), host.dp(60)));
            Button plus = new Button(host);
            plus.setText("Frag+");
            plus.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mClicks++;
                    update();
                }
            });
            row.addView(plus, new LinearLayout.LayoutParams(host.dp(160), -2));
            update();
            return row;
        }

        void update() { mSwatch.setBackgroundColor(COUNT_COLORS[mClicks % COUNT_COLORS.length]); }

        @Override
        public void onActivityCreated(Bundle state) { super.onActivityCreated(state); log("onActivityCreated"); }

        @Override
        public void onStart() { super.onStart(); log("onStart"); }

        @Override
        public void onResume() { super.onResume(); log("onResume"); }

        @Override
        public void onPause() { super.onPause(); log("onPause"); }

        @Override
        public void onStop() { super.onStop(); log("onStop"); }

        @Override
        public void onSaveInstanceState(Bundle out) {
            super.onSaveInstanceState(out);
            out.putInt("clicks", mClicks);
        }

        @Override
        public void onDestroyView() { super.onDestroyView(); log("onDestroyView"); }

        @Override
        public void onDestroy() { super.onDestroy(); log("onDestroy"); }
    }

    /** Headless retained fragment: one instance across recreation. */
    public static class RetainedFragment extends Fragment {
        private static int sInstances;
        private final int mInstance = ++sInstances;

        @Override
        public void onCreate(Bundle state) {
            super.onCreate(state);
            setRetainInstance(true);
            LifeApp.log("Retained.onCreate instance=" + mInstance);
        }

        @Override
        public void onActivityCreated(Bundle state) {
            super.onActivityCreated(state);
            LifeApp.log("Retained.onActivityCreated instance=" + mInstance);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            LifeApp.log("Retained.onDestroy instance=" + mInstance);
        }
    }
}
