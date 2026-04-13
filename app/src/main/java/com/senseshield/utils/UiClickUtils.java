package com.senseshield.utils;

import android.os.SystemClock;
import android.view.View;

/**
 * Guards against rapid double taps that can trigger duplicate navigations/actions.
 */
public final class UiClickUtils {

    private static final long DEFAULT_DEBOUNCE_MS = 500L;

    private UiClickUtils() {
    }

    public interface SafeClickListener {
        void onSafeClick(View view);
    }

    public static void setSafeClickListener(View view, SafeClickListener listener) {
        if (view == null || listener == null) return;
        view.setOnClickListener(new View.OnClickListener() {
            private long lastClickTs = 0L;

            @Override
            public void onClick(View v) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastClickTs < DEFAULT_DEBOUNCE_MS) return;
                lastClickTs = now;
                listener.onSafeClick(v);
            }
        });
    }
}
