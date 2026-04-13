package com.senseshield.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.senseshield.R;

/**
 * OnboardingWelcomeFragment.java
 * Step 1 — Full-screen welcome with app name, tagline, and a calm illustration.
 * No interaction required — user just taps Next to proceed.
 */
public class OnboardingWelcomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_welcome, container, false);
    }
}
