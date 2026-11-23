package com.mama.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "MamaApp";
    private int taps = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: app started");

        TextView label = new TextView(this);
        label.setText("Hello from Mama App");
        label.setTextSize(28);
        label.setGravity(Gravity.CENTER);

        Button button = new Button(this);
        button.setText("Tap me");
        button.setOnClickListener(v -> {
            taps++;
            label.setText("Taps: " + taps);
            Log.d(TAG, "button tapped, count=" + taps);
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.addView(label);
        root.addView(button);
        setContentView(root);
    }
}
