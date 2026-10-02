package com.example.select;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/**
 * A long press on "beta" should select that word and open the floating
 * toolbar. Copy puts the word on the clipboard and closes the toolbar.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Select";
    private boolean mCopied;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "SEL ok " + name);
        else Log.e(TAG, "SEL FAIL " + name + " " + detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final TextView body = findViewById(R.id.body);
        final TextView status = findViewById(R.id.status);
        body.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                CharSequence text = body.getText();
                int start = body.getSelectionStart();
                int end = body.getSelectionEnd();
                int a = Math.min(start, end);
                int b = Math.max(start, end);
                String word = a >= 0 && b <= text.length() ? text.subSequence(a, b).toString() : "";
                check("word", "beta".equals(word), word);
                check("type", mode.getType() == ActionMode.TYPE_FLOATING, mode.getType());
                MenuItem copy = menu.findItem(android.R.id.copy);
                MenuItem cut = menu.findItem(android.R.id.cut);
                MenuItem paste = menu.findItem(android.R.id.paste);
                MenuItem all = menu.findItem(android.R.id.selectAll);
                check("copy item", copy != null && copy.isVisible(), copy == null ? null : copy.isVisible());
                check("cut hidden", cut != null && !cut.isVisible(), cut == null ? null : cut.isVisible());
                check("paste hidden", paste != null && !paste.isVisible(), paste == null ? null : paste.isVisible());
                check("select all", all != null && all.isVisible(), all == null ? null : all.isVisible());
                return true;
            }

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == android.R.id.copy) mCopied = true;
                return false;
            }

            public void onDestroyActionMode(ActionMode mode) {
                ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                CharSequence copied = clip != null ? clip.getText() : null;
                check("clipboard", mCopied && copied != null && "beta".equals(copied.toString()), copied);
                if (mCopied) status.setText("copied");
            }
        });
        body.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (body.getWidth() < 100 || body.getLayout() == null) return;
                body.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                String text = body.getText().toString();
                int beta = text.indexOf("beta");
                Layout layout = body.getLayout();
                float mid = (layout.getPrimaryHorizontal(beta) + layout.getPrimaryHorizontal(beta + 4)) / 2f;
                int line = layout.getLineForOffset(beta);
                int yIn = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
                int[] loc = new int[2];
                body.getLocationOnScreen(loc);
                int x = loc[0] + body.getTotalPaddingLeft() + Math.round(mid);
                int y = loc[1] + body.getTotalPaddingTop() + yIn;
                Log.i(TAG, "SELPOS " + x + " " + y);
            }
        });
    }
}
