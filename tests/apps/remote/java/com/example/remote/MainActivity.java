package com.example.remote;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.ViewFlipper;
import java.util.Collections;
import java.util.HashMap;

/**
 * RemoteViews stub. The card is built with the public setters, applied into
 * the host, and opened by a click PendingIntent. A second copy is parcelled
 * and a landscape / sized pair is applied off screen.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Remote";
    private static final String ACTION = "com.example.remote.ACTION";
    private static final int TEAL = 0xFF008577;
    private static final int ORANGE = 0xFFE65100;

    private RemoteViews mViews;
    private View mRoot;
    private boolean mLoggedChecked;
    private boolean mLoggedRow;
    private boolean mLoggedOpen;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "RVCHECK ok " + name);
        else Log.e(TAG, "RVCHECK FAIL " + name + " " + detail);
    }

    private Intent broadcast(String what) {
        return new Intent(ACTION).setPackage(getPackageName()).putExtra("what", what);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final TextView status = findViewById(R.id.status);
        final ViewGroup host = findViewById(R.id.host);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String what = intent.getStringExtra("what");
                if ("check".equals(what) && !mLoggedChecked) {
                    mLoggedChecked = true;
                    check("checked extra", intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false),
                            intent.getExtras());
                } else if ("row".equals(what) && !mLoggedRow) {
                    mLoggedRow = true;
                    check("row fill-in", "alpha".equals(intent.getStringExtra("row")), intent.getStringExtra("row"));
                } else if ("open".equals(what) && !mLoggedOpen) {
                    mLoggedOpen = true;
                    mViews.setTextViewText(R.id.title, "Opened");
                    mViews.setInt(R.id.swatch, "setBackgroundColor", ORANGE);
                    mViews.setTextViewText(R.id.open, "Opened");
                    mViews.reapply(MainActivity.this, mRoot);
                    status.setText("opened");
                    check("opened", true, null);
                }
            }
        }, new IntentFilter(ACTION));

        float density = getResources().getDisplayMetrics().density;
        check("density", Math.abs(density - 1.5f) < 0.01f, density);
        check("orientation", getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE,
                getResources().getConfiguration().orientation);

        mViews = buildCard();
        check("package", getPackageName().equals(mViews.getPackage()), mViews.getPackage());
        check("layout", mViews.getLayoutId() == R.layout.card, mViews.getLayoutId());
        check("view id", mViews.getViewId() == 0, mViews.getViewId());
        check("load class", mViews.onLoadClass(TextView.class), null);
        check("reject class", !mViews.onLoadClass(String.class), null);

        RemoteViews cloned = mViews.clone();
        cloned.setTextViewText(R.id.title, "cloned");
        TextView cloneTitle = (TextView) cloned.apply(this, new FrameLayout(this)).findViewById(R.id.title);
        check("clone", cloneTitle != null && "cloned".equals(cloneTitle.getText().toString()),
                cloneTitle == null ? null : cloneTitle.getText());

        Parcel parcel = Parcel.obtain();
        mViews.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        RemoteViews read = RemoteViews.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        mViews.setTextViewText(R.id.hidden, "after");
        TextView parcelHidden = (TextView) read.apply(this, new FrameLayout(this)).findViewById(R.id.hidden);
        check("parcel", parcelHidden != null && "before".equals(parcelHidden.getText().toString()),
                parcelHidden == null ? null : parcelHidden.getText());

        check("draw version", RemoteViews.DrawInstructions.getSupportedVersion() == 1L, null);
        byte[] bytes = new byte[] {1, 2, 3};
        RemoteViews drawn = new RemoteViews(new RemoteViews.DrawInstructions.Builder(
                Collections.singletonList(bytes)).build());
        View drawnView = drawn.apply(this, new FrameLayout(this));
        check("draw view", drawnView != null, drawnView);

        RemoteViews land = new RemoteViews(getPackageName(), R.layout.plain_land);
        land.setTextViewText(R.id.label, "land");
        RemoteViews port = new RemoteViews(getPackageName(), R.layout.plain_port);
        port.setTextViewText(R.id.label, "port");
        RemoteViews both = new RemoteViews(land, port);
        TextView landLabel = (TextView) both.apply(this, new FrameLayout(this)).findViewById(R.id.label);
        check("landscape", landLabel != null && "land".equals(landLabel.getText().toString()),
                landLabel == null ? null : landLabel.getText());
        check("portrait id", both.getLayoutId() == R.layout.plain_port, both.getLayoutId());

        RemoteViews narrow = new RemoteViews(getPackageName(), R.layout.plain_land);
        narrow.setTextViewText(R.id.label, "narrow");
        RemoteViews wide = new RemoteViews(getPackageName(), R.layout.plain_port);
        wide.setTextViewText(R.id.label, "wide");
        HashMap<SizeF, RemoteViews> sizes = new HashMap<SizeF, RemoteViews>();
        sizes.put(new SizeF(100f, 100f), narrow);
        sizes.put(new SizeF(2000f, 100f), wide);
        TextView sizedLabel = (TextView) new RemoteViews(sizes).apply(this, new FrameLayout(this))
                .findViewById(R.id.label);
        check("sized", sizedLabel != null && "wide".equals(sizedLabel.getText().toString()),
                sizedLabel == null ? null : sizedLabel.getText());

        RemoteViews.RemoteCollectionItems items = new RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true).setViewTypeCount(1)
                .addItem(7L, row("Alpha", 0xFF1565C0, "alpha"))
                .addItem(8L, row("Beta", 0xFF2E7D32, "beta"))
                .build();
        check("item id", items.getItemId(0) == 7L && items.hasStableIds() && items.getViewTypeCount() == 1,
                items.getItemId(0));
        boolean threw = false;
        try {
            new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
                    .addItem(1L, new RemoteViews(getPackageName(), R.layout.plain_land))
                    .addItem(2L, new RemoteViews(getPackageName(), R.layout.plain_port))
                    .build();
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("type count", threw, null);

        RemoteViews forced = new RemoteViews(getPackageName(), R.layout.bare, R.id.forced);
        View forcedView = forced.apply(this, new FrameLayout(this));
        check("root id", forced.getViewId() == R.id.forced && forcedView.getId() == R.id.forced, forcedView.getId());

        boolean rejected = false;
        try {
            mViews.reapply(this, new TextView(this));
        } catch (RemoteViews.ActionException e) {
            rejected = true;
        }
        check("bad reapply", rejected, null);

        mRoot = mViews.apply(this, host);
        host.addView(mRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mRoot.getWidth() < 100 || mRoot.getHeight() < 100) return;
                mRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                inspect(status);
            }
        });
    }

    private void inspect(TextView status) {
        TextView title = mRoot.findViewById(R.id.title);
        View swatch = mRoot.findViewById(R.id.swatch);
        TextView body = mRoot.findViewById(R.id.body);
        TextView hidden = mRoot.findViewById(R.id.hidden);
        ProgressBar progress = mRoot.findViewById(R.id.progress);
        Chronometer chrono = mRoot.findViewById(R.id.chrono);
        ImageView icon = mRoot.findViewById(R.id.icon);
        ListView list = mRoot.findViewById(R.id.list);
        RadioGroup radios = mRoot.findViewById(R.id.radios);
        ViewFlipper flipper = mRoot.findViewById(R.id.flipper);
        LinearLayout bucket = mRoot.findViewById(R.id.bucket);
        View empty = mRoot.findViewById(R.id.empty);
        TextView open = mRoot.findViewById(R.id.open);

        check("title", title != null && "Remote views".equals(title.getText().toString()), title.getText());
        check("description", "card title".equals(String.valueOf(title.getContentDescription())),
                title.getContentDescription());
        int swatchColor = swatch.getBackground() instanceof ColorDrawable
                ? ((ColorDrawable) swatch.getBackground()).getColor() : 0;
        check("swatch", swatchColor == TEAL, Integer.toHexString(swatchColor));
        check("body", "From the package".equals(body.getText().toString()), body.getText());
        check("hidden gone", hidden.getVisibility() == View.GONE, hidden.getVisibility());
        check("hidden text", "after".equals(hidden.getText().toString()), hidden.getText());
        check("progress", progress.getMax() == 100 && progress.getProgress() == 40 && !progress.isIndeterminate(),
                progress.getProgress());
        check("chrono", chrono.isCountDown() && "clock".equals(chrono.getText().toString()), chrono.getText());
        check("padding", title.getPaddingLeft() == 18 && title.getPaddingTop() == 10, title.getPaddingLeft());
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) progress.getLayoutParams();
        check("margin", mlp.leftMargin == 12, mlp.leftMargin);
        int wantWidth = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80f,
                getResources().getDisplayMetrics()));
        check("width", swatch.getLayoutParams().width == wantWidth, swatch.getLayoutParams().width);
        check("min width", hidden.getMinimumWidth() == getResources().getDimensionPixelSize(R.dimen.gap),
                hidden.getMinimumWidth());
        float wantSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, getResources().getDisplayMetrics());
        check("text size", Math.abs(title.getTextSize() - wantSize) < 1f, title.getTextSize());
        check("color pair", hidden.getCurrentTextColor() == 0xFF010203,
                Integer.toHexString(hidden.getCurrentTextColor()));
        check("icon", icon.getDrawable() != null, icon.getDrawable());
        TextView row = list.getChildCount() > 0 ? (TextView) list.getChildAt(0) : null;
        check("list text", row != null && "Alpha".equals(row.getText().toString()),
                row == null ? "no child" : row.getText());
        check("radio", radios.getCheckedRadioButtonId() == R.id.radio_b, radios.getCheckedRadioButtonId());
        check("flipper", flipper.getDisplayedChild() == 1, flipper.getDisplayedChild());
        check("chip", bucket.getChildCount() == 1 && bucket.getChildAt(0).getId() == R.id.chip, bucket.getChildCount());
        check("empty", list.getEmptyView() == empty && empty.getVisibility() == View.GONE, empty.getVisibility());
        float wantRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                getResources().getDisplayMetrics());
        boolean outline = swatch.getOutlineProvider() instanceof RemoteViews.RemoteViewOutlineProvider
                && Math.abs(((RemoteViews.RemoteViewOutlineProvider) swatch.getOutlineProvider()).getRadius()
                - wantRadius) < 0.5f;
        check("outline", outline, swatch.getOutlineProvider());

        // Click while the row is still attached. reapply installs the collection
        // adapter again, which detaches these children.
        if (row != null) row.performClick();

        mViews.setTextViewText(R.id.hidden, "reapplied");
        mViews.reapply(this, mRoot);
        check("reapply", "reapplied".equals(hidden.getText().toString()), hidden.getText());
        int[] loc = new int[2];
        open.getLocationOnScreen(loc);
        Log.i(TAG, "RVPOS open " + loc[0] + " " + loc[1] + " " + open.getWidth() + " " + open.getHeight());
        swatch.getLocationOnScreen(loc);
        Log.i(TAG, "RVPOS swatch " + loc[0] + " " + loc[1] + " " + swatch.getWidth() + " " + swatch.getHeight());
        icon.getLocationOnScreen(loc);
        Log.i(TAG, "RVPOS icon " + loc[0] + " " + loc[1] + " " + icon.getWidth() + " " + icon.getHeight());
        list.getLocationOnScreen(loc);
        Log.i(TAG, "RVPOS list " + loc[0] + " " + loc[1] + " " + list.getWidth() + " " + list.getHeight());
        title.getLocationOnScreen(loc);
        Log.i(TAG, "RVPOS title " + loc[0] + " " + loc[1] + " " + title.getWidth() + " " + title.getHeight());
        progress.getLocationOnScreen(loc);
        Log.i(TAG, "RVPOS progress " + loc[0] + " " + loc[1] + " " + progress.getWidth() + " " + progress.getHeight());
        status.setText("remote");
    }

    private RemoteViews buildCard() {
        String pkg = getPackageName();
        RemoteViews views = new RemoteViews(pkg, R.layout.card);
        views.setTextViewText(R.id.title, "Remote views");
        views.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 22f);
        views.setTextColor(R.id.title, 0xFF212121);
        views.setViewPadding(R.id.title, 18, 10, 18, 10);
        views.setContentDescription(R.id.title, "card title");
        views.setInt(R.id.swatch, "setBackgroundColor", TEAL);
        views.setViewLayoutWidth(R.id.swatch, 80f, TypedValue.COMPLEX_UNIT_DIP);
        views.setViewOutlinePreferredRadius(R.id.swatch, 8f, TypedValue.COMPLEX_UNIT_DIP);
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFF673AB7);
        views.setImageViewBitmap(R.id.icon, bitmap);
        views.setCharSequence(R.id.body, "setText", R.string.body);
        views.setViewLayoutMargin(R.id.progress, RemoteViews.MARGIN_LEFT, 12f, TypedValue.COMPLEX_UNIT_PX);
        views.setProgressBar(R.id.progress, 100, 40, false);
        views.setChronometer(R.id.chrono, 0L, "clock", false);
        views.setChronometerCountDown(R.id.chrono, true);
        views.setTextViewText(R.id.hidden, "before");
        views.setViewVisibility(R.id.hidden, View.GONE);
        views.setColorInt(R.id.hidden, "setTextColor", 0xFF010203, 0xFF040506);
        views.setIntDimen(R.id.hidden, "setMinimumWidth", R.dimen.gap);
        views.setOnCheckedChangeResponse(R.id.check, RemoteViews.RemoteResponse.fromPendingIntent(
                PendingIntent.getBroadcast(this, 1, broadcast("check"), PendingIntent.FLAG_MUTABLE)));
        views.setCompoundButtonChecked(R.id.check, true);
        views.setRadioGroupChecked(R.id.radios, R.id.radio_b);
        views.setDisplayedChild(R.id.flipper, 1);
        views.addStableView(R.id.bucket, new RemoteViews(pkg, R.layout.chip), R.id.chip);
        PendingIntent template = PendingIntent.getBroadcast(this, 2, broadcast("row"), PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.list, template);
        RemoteViews.RemoteCollectionItems items = new RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .addItem(7L, row("Alpha", 0xFF1565C0, "alpha"))
                .addItem(8L, row("Beta", 0xFF2E7D32, "beta"))
                .build();
        views.setRemoteAdapter(R.id.list, items);
        views.setEmptyView(R.id.list, R.id.empty);
        views.setOnClickPendingIntent(R.id.open,
                PendingIntent.getBroadcast(this, 3, broadcast("open"), PendingIntent.FLAG_IMMUTABLE));
        views.setLightBackgroundLayoutId(R.layout.card);
        return views;
    }

    private RemoteViews row(String label, int color, String which) {
        RemoteViews row = new RemoteViews(getPackageName(), R.layout.row);
        row.setTextViewText(R.id.label, label);
        row.setInt(R.id.label, "setBackgroundColor", color);
        row.setOnClickFillInIntent(R.id.label, new Intent().putExtra("row", which));
        return row;
    }
}
