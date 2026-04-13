package com.senseshield.ui.onboarding;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.senseshield.R;

public class OnboardingNameFragment extends Fragment {

    private static final String[] AVATAR_COLORS = {
            "#7F77DD", "#1D9E75", "#D85A30", "#378ADD", "#BA7517", "#D4537E"
    };
    private static final String[] COLOR_NAMES = {
            "Purple","Teal","Coral","Blue","Amber","Pink"
    };

    private TextInputEditText etName;
    private TextView          tvAvatarPreview;
    private View              avatarBackground; // the soft circle View behind the initials
    private LinearLayout      llRow1, llRow2;
    private View[]            colorCircles = new View[AVATAR_COLORS.length];
    private String            selectedColor = AVATAR_COLORS[0];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_name, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        etName          = root.findViewById(R.id.et_name);
        tvAvatarPreview = root.findViewById(R.id.tv_avatar_preview);
        llRow1          = root.findViewById(R.id.ll_avatar_colors);
        llRow2          = root.findViewById(R.id.ll_avatar_colors_row2);

        // The background circle is the View sibling inside the FrameLayout
        // We tint it directly by finding it via parent
        if (tvAvatarPreview != null && tvAvatarPreview.getParent() instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) tvAvatarPreview.getParent();
            // First child is the background View, second is the TextView
            if (frame.getChildCount() >= 1) {
                avatarBackground = frame.getChildAt(0);
            }
        }

        setupNameField();
        buildColorCircles();
        selectColor(0); // default purple
    }

    private void setupNameField() {
        if (etName == null) return;
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                updateAvatarInitials(s.toString());
            }
        });
    }

    private void updateAvatarInitials(String text) {
        if (tvAvatarPreview == null) return;
        String trimmed = text.trim();
        String initials;
        if (trimmed.isEmpty()) {
            initials = "?";
        } else {
            String[] parts = trimmed.split("\\s+");
            if (parts.length == 1) {
                initials = parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
            } else {
                initials = (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
            }
        }
        tvAvatarPreview.setText(initials);
    }

    private void buildColorCircles() {
        if (llRow1 == null || llRow2 == null) return;
        int sizePx   = dpToPx(56);
        int marginPx = dpToPx(8);

        for (int i = 0; i < AVATAR_COLORS.length; i++) {
            View circle = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            circle.setLayoutParams(lp);
            circle.setBackgroundResource(R.drawable.bg_avatar_circle);
            circle.getBackground().setTint(Color.parseColor(AVATAR_COLORS[i]));
            circle.setContentDescription(COLOR_NAMES[i] + " color");
            circle.setClickable(true);
            circle.setFocusable(true);

            final int index = i;
            circle.setOnClickListener(v -> selectColor(index));

            colorCircles[i] = circle;
            if (i < 3) llRow1.addView(circle);
            else        llRow2.addView(circle);
        }
    }

    private void selectColor(int index) {
        // Scale selected circle up, others normal
        for (int i = 0; i < colorCircles.length; i++) {
            if (colorCircles[i] == null) continue;
            float scale = (i == index) ? 1.25f : 1.0f;
            colorCircles[i].animate().scaleX(scale).scaleY(scale).setDuration(150).start();
        }

        selectedColor = AVATAR_COLORS[index];

        // Tint the background circle View (not the TextView's background)
        if (avatarBackground != null && avatarBackground.getBackground() != null) {
            avatarBackground.getBackground().setTint(Color.parseColor(selectedColor));
        }

        if (colorCircles[index] != null) {
            colorCircles[index].announceForAccessibility(COLOR_NAMES[index] + " selected");
        }
    }

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    public String getEnteredName() {
        if (etName == null || etName.getText() == null) return "Friend";
        String name = etName.getText().toString().trim();
        return name.isEmpty() ? "Friend" : name;
    }

    public String getSelectedAvatarColor() {
        return selectedColor;
    }
}
