package dev.birdy.minimal;

import android.app.Activity;
import android.os.Bundle;

// "Bedienung": the gestures and long presses, read-only.
public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings_help);
    }
}
