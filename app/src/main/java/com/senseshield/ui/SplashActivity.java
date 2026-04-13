package com.senseshield.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.senseshield.SenseShieldApp;
import com.senseshield.ui.caregiver.CaregiverHomeActivity;
import com.senseshield.ui.home.HomeActivity;
import com.senseshield.ui.onboarding.OnboardingActivity;
import com.senseshield.utils.AppConstants;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(SenseShieldApp.getThemeResId(this));
        super.onCreate(savedInstanceState);
        new Handler(Looper.getMainLooper()).postDelayed(this::route, 800);
    }

    private void route() {
        SharedPreferences prefs = getSharedPreferences(
                AppConstants.PREFS_NAME, MODE_PRIVATE);
        boolean onboardingDone = prefs.getBoolean(
                AppConstants.PREF_ONBOARDING_DONE, false);
        Intent intent;
        if (!onboardingDone) {
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            int mode = prefs.getInt(AppConstants.PREF_USER_MODE, AppConstants.MODE_PERSON);
            intent = (mode == AppConstants.MODE_CAREGIVER)
                    ? new Intent(this, CaregiverHomeActivity.class)
                    : new Intent(this, HomeActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
