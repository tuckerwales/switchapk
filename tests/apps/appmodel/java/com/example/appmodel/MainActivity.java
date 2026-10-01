package com.example.appmodel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
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
}
