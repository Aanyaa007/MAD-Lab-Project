package com.senseshield.ui.help;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.utils.UiClickUtils;

/**
 * HelpActivity — Help & About screen.
 * Explains what SenseShield+ does, how to use it,
 * how the AI works, and shows privacy info and credits.
 */


public class HelpActivity extends AppCompatActivity {
    private int appliedThemeResId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = SenseShieldApp.getThemeResId(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView btnBack = findViewById(R.id.btn_help_back);
        UiClickUtils.setSafeClickListener(btnBack, v -> finish());
    }
}
