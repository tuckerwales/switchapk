package com.example.popups;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView mStatus;

    private void status(String s) {
        mStatus.setText(s);
        Log.i("POP", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                status((parent.getId() == R.id.dropdown ? "dropdown " : "dialog ") + position + " "
                        + parent.getItemAtPosition(position));
            }

            public void onNothingSelected(AdapterView<?> parent) { status("nothing"); }
        };
        ((Spinner) findViewById(R.id.dropdown)).setOnItemSelectedListener(listener);
        ((Spinner) findViewById(R.id.dialog_spinner)).setOnItemSelectedListener(listener);
        findViewById(R.id.menu).setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v);
            menu.inflate(R.menu.popup);
            menu.setOnMenuItemClickListener(item -> {
                status("menu " + item.getTitle());
                return true;
            });
            menu.setOnDismissListener(m -> Log.i("POP", "menu dismissed"));
            menu.show();
        });
        findViewById(R.id.toast).setOnClickListener(v -> Toast.makeText(this, "Hello toast", Toast.LENGTH_SHORT).show());
        findViewById(R.id.popup).setOnClickListener(v -> {
            TextView content = new TextView(this);
            content.setText("Custom popup");
            content.setTextSize(20);
            content.setPadding(24, 24, 24, 24);
            PopupWindow popup = new PopupWindow(content, 300, 200, true);
            popup.setBackgroundDrawable(new ColorDrawable(0xFF3949AB));
            popup.setOnDismissListener(() -> Log.i("POP", "popup dismissed"));
            popup.showAsDropDown(v);
            Log.i("POP", "popup above " + popup.isAboveAnchor());
        });
    }
}
