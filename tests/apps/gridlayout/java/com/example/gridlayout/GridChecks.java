package com.example.gridlayout;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

/** Logic checks for GridLayout. Each logs "GLCHECK ok name" or a failure. */
final class GridChecks {
    static final String TAG = "GridTest";
    private static final int UNSPECIFIED = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "GLCHECK ok " + name);
        else Log.e(TAG, "GLCHECK FAIL " + name + " " + detail);
    }

    private static View box(Activity a, GridLayout g, int w, int h) {
        View v = new View(a);
        g.addView(v, new GridLayout.LayoutParams(new ViewGroup.LayoutParams(w, h)));
        return v;
    }

    private static View box(Activity a, GridLayout g, int w, int h, GridLayout.Spec row, GridLayout.Spec col) {
        View v = new View(a);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(row, col);
        lp.width = w;
        lp.height = h;
        g.addView(v, lp);
        return v;
    }

    private static void layout(GridLayout g) {
        g.measure(UNSPECIFIED, UNSPECIFIED);
        g.layout(0, 0, g.getMeasuredWidth(), g.getMeasuredHeight());
    }

    private static String pos(View v) {
        return v.getLeft() + "," + v.getTop() + " " + v.getWidth() + "x" + v.getHeight();
    }

    static void run(Activity a) {
        GridLayout g = new GridLayout(a);
        check("defaults", g.getOrientation() == GridLayout.HORIZONTAL && g.getRowCount() == 0
                && g.getColumnCount() == 0 && !g.getUseDefaultMargins()
                && g.getAlignmentMode() == GridLayout.ALIGN_MARGINS && g.isRowOrderPreserved()
                && g.isColumnOrderPreserved(), g.getRowCount() + " " + g.getColumnCount());
        check("class name", "android.widget.GridLayout".equals(g.getAccessibilityClassName().toString()),
                g.getAccessibilityClassName());

        // Auto flow: five 10x10 boxes in three columns.
        g.setColumnCount(3);
        View[] b = new View[5];
        for (int i = 0; i < 5; i++) b[i] = box(a, g, 10, 10);
        layout(g);
        check("flow size", g.getMeasuredWidth() == 30 && g.getMeasuredHeight() == 20 && g.getRowCount() == 2,
                g.getMeasuredWidth() + "x" + g.getMeasuredHeight() + " rows " + g.getRowCount());
        check("flow wraps", b[3].getLeft() == 0 && b[3].getTop() == 10 && b[4].getLeft() == 10, pos(b[3]) + " / " + pos(b[4]));

        // A two-column span that does not fit at the end of row 1 moves to row 2.
        View wide = box(a, g, 20, 10, GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 2));
        layout(g);
        check("span wraps", wide.getLeft() == 0 && wide.getTop() == 20 && g.getRowCount() == 3, pos(wide));

        // Column count below a child's index is rejected.
        boolean threw = false;
        try {
            g.setColumnCount(1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("count too small throws", threw && g.getColumnCount() == 3, g.getColumnCount());

        // Wrong LayoutParams type on an existing child is rejected.
        threw = false;
        ViewGroup.LayoutParams saved = b[0].getLayoutParams();
        try {
            b[0].setLayoutParams(new LinearLayout.LayoutParams(10, 10));
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("wrong params throw", threw, null);
        b[0].setLayoutParams(saved); // AOSP keeps the rejected params on the child

        // GONE children take no space.
        b[2].setVisibility(View.GONE);
        b[4].setVisibility(View.GONE);
        layout(g);
        check("gone column collapses", g.getMeasuredWidth() == 20, g.getMeasuredWidth());
        wide.setVisibility(View.GONE);
        layout(g);
        check("gone row collapses", g.getMeasuredHeight() == 20, g.getMeasuredHeight());

        explicit(a);
        vertical(a);
        margins(a);
        alignment(a);
        weights(a);
        baseline(a);
    }

    private static void explicit(Activity a) {
        GridLayout g = new GridLayout(a);
        View v = box(a, g, 10, 10, GridLayout.spec(2), GridLayout.spec(1));
        View w = box(a, g, 10, 10, GridLayout.spec(0), GridLayout.spec(0));
        layout(g);
        check("explicit cell", g.getRowCount() == 3 && g.getColumnCount() == 2 && v.getLeft() == 10
                && v.getTop() == 10 && w.getLeft() == 0 && w.getTop() == 0,
                g.getRowCount() + "x" + g.getColumnCount() + " " + pos(v));
        // Empty rows and columns have zero size.
        check("empty row is zero", g.getMeasuredHeight() == 20, g.getMeasuredHeight());
        GridLayout.LayoutParams lp = (GridLayout.LayoutParams) v.getLayoutParams();
        check("spec equality", lp.rowSpec.equals(GridLayout.spec(2)) && !lp.rowSpec.equals(GridLayout.spec(1))
                && lp.columnSpec.equals(GridLayout.spec(1, GridLayout.BASELINE)) == false, lp.rowSpec);
    }

    private static void vertical(Activity a) {
        GridLayout g = new GridLayout(a);
        g.setOrientation(GridLayout.VERTICAL);
        g.setRowCount(2);
        View[] b = new View[4];
        for (int i = 0; i < 4; i++) b[i] = box(a, g, 10, 10);
        layout(g);
        check("vertical flow", b[1].getTop() == 10 && b[1].getLeft() == 0 && b[2].getLeft() == 10
                && b[2].getTop() == 0 && g.getColumnCount() == 2, pos(b[1]) + " / " + pos(b[2]));
    }

    private static void margins(Activity a) {
        GridLayout g = new GridLayout(a);
        g.setUseDefaultMargins(true);
        int gap = Math.round(8 * a.getResources().getDisplayMetrics().density) / 2;
        // Copied params carry zero margins; only the Spec constructors leave them undefined.
        View zero = box(a, new GridLayout(a), 10, 10);
        check("copied params have zero margins",
                ((GridLayout.LayoutParams) zero.getLayoutParams()).leftMargin == 0, null);
        View v = box(a, g, 10, 10, GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED));
        View s = new Space(a);
        GridLayout.LayoutParams slp = new GridLayout.LayoutParams();
        slp.width = 10;
        slp.height = 10;
        g.addView(s, slp);
        layout(g);
        check("default margins", v.getLeft() == gap && v.getTop() == gap
                && g.getMeasuredWidth() == 2 * gap + 20 && g.getMeasuredHeight() == 2 * gap + 10,
                pos(v) + " grid " + g.getMeasuredWidth() + "x" + g.getMeasuredHeight() + " gap " + gap);
        check("space has no margins", s.getLeft() == 2 * gap + 10 && s.getTop() == 0, pos(s));
        // An explicit margin wins over the default.
        GridLayout.LayoutParams lp = (GridLayout.LayoutParams) v.getLayoutParams();
        lp.leftMargin = 0;
        v.setLayoutParams(lp);
        layout(g);
        check("explicit margin", v.getLeft() == 0, pos(v));
    }

    private static void alignment(Activity a) {
        GridLayout g = new GridLayout(a);
        g.setColumnCount(1);
        box(a, g, 40, 10);
        View right = box(a, g, 10, 10, GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, GridLayout.RIGHT));
        View center = box(a, g, 10, 10, GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, GridLayout.CENTER));
        View fill = box(a, g, 10, 10, GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, GridLayout.FILL));
        View end = box(a, g, 10, 10, GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, GridLayout.END));
        layout(g);
        check("align right", right.getLeft() == 30 && right.getTop() == 10, pos(right));
        check("align center", center.getLeft() == 15, pos(center));
        check("align fill", fill.getLeft() == 0 && fill.getWidth() == 40, pos(fill));
        check("align end", end.getLeft() == 30, pos(end));
        GridLayout.LayoutParams lp = (GridLayout.LayoutParams) right.getLayoutParams();
        lp.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        right.setLayoutParams(lp);
        layout(g);
        check("setGravity", right.getLeft() == 15, pos(right));
    }

    private static void weights(Activity a) {
        GridLayout g = new GridLayout(a);
        View[] b = new View[3];
        for (int i = 0; i < 3; i++) {
            b[i] = box(a, g, 0, 10, GridLayout.spec(0), GridLayout.spec(i, i == 2 ? 2f : 1f));
        }
        g.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), UNSPECIFIED);
        g.layout(0, 0, 400, g.getMeasuredHeight());
        check("weights share", b[0].getWidth() == 100 && b[1].getWidth() == 100 && b[2].getWidth() == 200
                && b[2].getLeft() == 200, pos(b[0]) + " / " + pos(b[1]) + " / " + pos(b[2]));
        // Without spare space (wrap content) weighted cells keep their measured size.
        layout(g);
        check("weights wrap", g.getMeasuredWidth() == 0, g.getMeasuredWidth());
    }

    private static void baseline(Activity a) {
        GridLayout g = new GridLayout(a);
        TextView small = new TextView(a);
        small.setText("small");
        small.setTextSize(12);
        TextView big = new TextView(a);
        big.setText("Big");
        big.setTextSize(30);
        g.addView(small);
        g.addView(big);
        layout(g);
        check("baseline aligned", small.getTop() + small.getBaseline() == big.getTop() + big.getBaseline()
                && small.getTop() > 0 && big.getTop() == 0,
                small.getTop() + "+" + small.getBaseline() + " vs " + big.getTop() + "+" + big.getBaseline());
    }

    static void afterLayout(Activity a) {
        GridLayout calc = (GridLayout) a.findViewById(R.id.calc);
        check("xml counts", calc.getColumnCount() == 4 && calc.getRowCount() == 5 && calc.getUseDefaultMargins(),
                calc.getColumnCount() + "x" + calc.getRowCount());
        View display = calc.getChildAt(0);
        GridLayout.LayoutParams lp = (GridLayout.LayoutParams) display.getLayoutParams();
        check("xml span", lp.columnSpec.equals(GridLayout.spec(0, 4, GridLayout.FILL))
                && lp.rowSpec.equals(GridLayout.spec(0)), lp.columnSpec);
        View zero = calc.getChildAt(12);
        View one = calc.getChildAt(8);
        check("xml flow around row spans", zero.getTop() > one.getTop() && zero.getLeft() == one.getLeft()
                && zero.getWidth() > 2 * one.getWidth(), pos(zero) + " / " + pos(one));
        GridLayout align = (GridLayout) a.findViewById(R.id.align);
        View yellow = align.getChildAt(4);
        check("row weight fills column", yellow.getHeight() == align.getHeight() && yellow.getTop() == 0,
                pos(yellow) + " in " + align.getHeight());
    }
}
