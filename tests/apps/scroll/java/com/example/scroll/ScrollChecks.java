package com.example.scroll;

import android.app.Activity;
import android.graphics.BlendMode;
import android.util.Log;
import android.view.View;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.ScrollView;
import android.widget.Scroller;

/** Logic checks for scrolling. Each logs "SCROLLCHECK ok name" or a failure. */
final class ScrollChecks {
    static final String TAG = "ScrollTest";
    static int failures;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "SCROLLCHECK ok " + name);
        else {
            failures++;
            Log.e(TAG, "SCROLLCHECK FAIL " + name + " " + detail);
        }
    }

    static void prepare(Activity activity) {
        vertical(activity);
        horizontal(activity);
        animators(activity);
        edge(activity);
    }

    private static void vertical(Activity activity) {
        ScrollView sv = new ScrollView(activity);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(new View(activity), new LinearLayout.LayoutParams(100, 2000));
        sv.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        int w = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
        int h = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
        sv.measure(w, h);
        sv.layout(0, 0, 300, 400);
        check("child tall", column.getMeasuredHeight() == 2000, column.getMeasuredHeight());
        check("viewport", sv.getMeasuredHeight() == 400, sv.getMeasuredHeight());
        sv.scrollTo(0, 500);
        check("scroll mid", sv.getScrollY() == 500, sv.getScrollY());
        sv.scrollTo(0, 10000);
        check("scroll clamp", sv.getScrollY() == 1600, sv.getScrollY());
        sv.scrollTo(0, -20);
        check("scroll top", sv.getScrollY() == 0, sv.getScrollY());
        sv.smoothScrollBy(0, 200);
        check("smooth pending", sv.getScrollY() == 0, sv.getScrollY());
        boolean threw = false;
        try {
            sv.addView(new View(activity));
        } catch (IllegalStateException ex) {
            threw = true;
        }
        check("one child", threw, "second add");
    }

    private static void horizontal(Activity activity) {
        HorizontalScrollView hv = new HorizontalScrollView(activity);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(new View(activity), new LinearLayout.LayoutParams(2000, 100));
        hv.addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        int w = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
        int h = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY);
        hv.measure(w, h);
        hv.layout(0, 0, 300, 400);
        check("row wide", row.getMeasuredWidth() == 2000, row.getMeasuredWidth());
        check("h viewport", hv.getMeasuredWidth() == 300, hv.getMeasuredWidth());
        hv.scrollTo(500, 0);
        check("hscroll", hv.getScrollX() == 500, hv.getScrollX());
        hv.scrollTo(10000, 0);
        check("hclamp", hv.getScrollX() == 1700, hv.getScrollX());
        hv.scrollTo(-10, 0);
        check("h top", hv.getScrollX() == 0, hv.getScrollX());
    }

    private static void animators(Activity activity) {
        Scroller scroller = new Scroller(activity);
        check("scroller idle", scroller.isFinished(), scroller.isFinished());
        scroller.startScroll(10, 20, 30, 40, 200);
        check("scroller final", scroller.getFinalX() == 40 && scroller.getFinalY() == 60 && !scroller.isFinished(),
                scroller.getFinalX() + "," + scroller.getFinalY());
        scroller.abortAnimation();
        check("scroller abort", scroller.isFinished() && scroller.getCurrX() == 40 && scroller.getCurrY() == 60,
                scroller.getCurrX() + "," + scroller.getCurrY());
        scroller.fling(0, 0, 0, 3000, 0, 0, 0, 100000);
        check("scroller fling", scroller.getFinalY() > 100, scroller.getFinalY());

        OverScroller over = new OverScroller(activity);
        over.fling(0, 0, 0, 2000, 0, 0, 0, 100000);
        check("over fling", over.getFinalY() > 100 && !over.isFinished(), over.getFinalY());
        over.abortAnimation();
        check("over abort", over.isFinished() && over.getCurrY() == over.getFinalY(), over.getCurrY());
        boolean sprang = over.springBack(0, -40, 0, 100, 0, 100);
        check("spring", sprang && over.getFinalY() == 0, over.getFinalY());
    }

    private static void edge(Activity activity) {
        EdgeEffect edge = new EdgeEffect(activity);
        check("edge idle", edge.isFinished(), edge.isFinished());
        check("blend", edge.getBlendMode() == BlendMode.SRC_ATOP, edge.getBlendMode());
        float consumed = edge.onPullDistance(0.4f, 0.5f);
        check("edge pull", !edge.isFinished() && edge.getDistance() > 0 && consumed > 0, edge.getDistance());
        edge.finish();
        check("edge done", edge.isFinished() && edge.getDistance() == 0f, edge.getDistance());
        edge.setSize(100, 200);
        check("edge max", edge.getMaxHeight() == 100, edge.getMaxHeight());
        edge.setColor(0xFF1565C0);
        check("edge color", edge.getColor() == 0xFF1565C0, edge.getColor());
    }
}
