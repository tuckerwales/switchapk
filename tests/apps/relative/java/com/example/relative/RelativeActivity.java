package com.example.relative;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class RelativeActivity extends Activity {
    private int mFailures;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final View root = findViewById(R.id.root);
        root.post(new Runnable() {
            public void run() { check(root); }
        });
    }

    private void expect(String name, int got, int want) {
        if (got == want) {
            Log.i("Relative", "RELCHECK ok " + name);
        } else {
            mFailures++;
            Log.e("Relative", "RELCHECK FAIL " + name + " got " + got + " want " + want);
        }
    }

    private void check(View root) {
        // 1280x720 at density 1.5: padding 10dp = 15px.
        int w = root.getWidth();
        int h = root.getHeight();
        View header = findViewById(R.id.header);
        View left = findViewById(R.id.left);
        View right = findViewById(R.id.right);
        View center = findViewById(R.id.center);
        View footer = findViewById(R.id.footer);
        View above = findViewById(R.id.abovefooter);
        View status = findViewById(R.id.status);
        expect("headerTop", header.getTop(), 15);
        expect("headerWidth", header.getWidth(), w - 30);
        expect("leftBelowHeader", left.getTop(), header.getBottom() + 15);
        expect("rightAlignTop", right.getTop(), left.getTop());
        expect("rightStart", right.getLeft(), left.getRight() + 15);
        expect("rightEnd", right.getRight(), w - 15);
        expect("centerX", center.getLeft(), (w - center.getWidth()) / 2);
        expect("centerY", center.getTop(), (h - center.getHeight()) / 2);
        expect("footerBottom", footer.getBottom(), h - 15);
        expect("aboveFooter", above.getBottom(), footer.getTop());
        expect("aboveRight", above.getRight(), w - 15);
        expect("statusBelow", status.getTop(), left.getBottom());
        expect("statusCentered", status.getLeft(), (w - status.getWidth()) / 2);
        Log.i("Relative", "RELCHECK done failures=" + mFailures);
        status.setBackgroundColor(mFailures == 0 ? 0xFF4CAF50 : 0xFFF44336);
    }
}
