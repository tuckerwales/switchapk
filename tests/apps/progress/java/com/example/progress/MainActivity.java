package com.example.progress;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        final TextView seekValue = (TextView) findViewById(R.id.seek_value);
        final TextView ratingValue = (TextView) findViewById(R.id.rating_value);
        SeekBar seek = (SeekBar) findViewById(R.id.seek);
        seekValue.setText("seek " + seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                seekValue.setText("seek " + progress);
                Log.i("PROG", "seek " + progress + " user=" + fromUser);
            }

            public void onStartTrackingTouch(SeekBar bar) { Log.i("PROG", "seek start"); }

            public void onStopTrackingTouch(SeekBar bar) { Log.i("PROG", "seek stop"); }
        });
        RatingBar rating = (RatingBar) findViewById(R.id.rating);
        ratingValue.setText("rating " + rating.getRating());
        rating.setOnRatingBarChangeListener((bar, value, fromUser) -> {
            ratingValue.setText("rating " + value);
            Log.i("PROG", "rating " + value + " user=" + fromUser);
        });
        ProgressBar horizontal = (ProgressBar) findViewById(R.id.horizontal);
        Log.i("PROG", "horizontal " + horizontal.getProgress() + "/" + horizontal.getMax() + " secondary "
                + horizontal.getSecondaryProgress() + " indeterminate "
                + ((ProgressBar) findViewById(R.id.indeterminate)).isIndeterminate());
        findViewById(R.id.dialog).setOnClickListener(v -> {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Downloading");
            dialog.setMessage("Please wait");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(100);
            dialog.show();
            dialog.setProgress(60);
            Log.i("PROG", "dialog " + dialog.getProgress() + "/" + dialog.getMax());
        });
    }
}
