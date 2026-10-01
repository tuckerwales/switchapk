package com.example.appmodel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final String TAG = "AppModel";
    private TextView mStatus;
    private View mBand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        mBand = findViewById(R.id.band);
        registerForContextMenu(findViewById(R.id.hold));
        mStatus.post(new Runnable() {
            public void run() { showAlert(); }
        });
    }

    private void showAlert() {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage("The file will be removed from this device.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        Log.i(TAG, "APPCHECK alert positive which=" + which);
                        mBand.setBackgroundColor(0xFF4CAF50);
                        mStatus.post(new Runnable() {
                            public void run() { showList(); }
                        });
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        Log.i(TAG, "APPCHECK alert negative");
                        mBand.setBackgroundColor(0xFFF44336);
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface d) { Log.i(TAG, "APPCHECK alert dismissed"); }
                })
                .show();
    }

    private void showList() {
        final CharSequence[] items = {"Red", "Green", "Blue"};
        new AlertDialog.Builder(this)
                .setTitle("Pick a colour")
                .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        Log.i(TAG, "APPCHECK list choice " + which);
                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        int checked = ((AlertDialog) d).getListView().getCheckedItemPosition();
                        Log.i(TAG, "APPCHECK list ok checked=" + checked);
                        mStatus.setText("picked " + items[checked]);
                        mBand.setBackgroundColor(checked == 2 ? 0xFF2196F3 : 0xFFFF9800);
                    }
                })
                .show();
    }

    private static final int ID_RED = 1;
    private static final int ID_PURPLE = 2;
    private static final int ID_ORANGE = 3;
    private static final int ID_GREEN = 4;
    private static final int ID_GOLD = 5;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, ID_RED, 0, "Red band");
        menu.add(0, ID_PURPLE, 0, "Purple band");
        SubMenu more = menu.addSubMenu("More colours");
        more.add(0, ID_ORANGE, 0, "Orange band");
        Log.i(TAG, "APPCHECK options created size=" + menu.size());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "APPCHECK options selected " + item.getItemId());
        return applyColour(item.getItemId());
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) { Log.i(TAG, "APPCHECK options closed"); }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        menu.setHeaderTitle("Band colour");
        menu.add(0, ID_GREEN, 0, "Make green");
        menu.add(0, ID_GOLD, 0, "Make gold");
        Log.i(TAG, "APPCHECK context created for " + v.getId());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Log.i(TAG, "APPCHECK context selected " + item.getItemId());
        return applyColour(item.getItemId());
    }

    @Override
    public void onContextMenuClosed(Menu menu) { Log.i(TAG, "APPCHECK context closed"); }

    private boolean applyColour(int id) {
        switch (id) {
            case ID_RED: mBand.setBackgroundColor(0xFFE53935); return true;
            case ID_PURPLE: mBand.setBackgroundColor(0xFF8E24AA); return true;
            case ID_ORANGE: mBand.setBackgroundColor(0xFFFB8C00); return true;
            case ID_GREEN: mBand.setBackgroundColor(0xFF43A047); return true;
            case ID_GOLD: mBand.setBackgroundColor(0xFFFFB300); return true;
            default: return false;
        }
    }
}
