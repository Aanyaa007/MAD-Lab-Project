package com.senseshield.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.ChipGroup;
import com.senseshield.R;
import com.senseshield.utils.AppConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * OnboardingToolsFragment.java — FIXED
 * Builds tool toggle cards programmatically into gl_tools GridLayout.
 * Sound chip uses existing cg_sounds ChipGroup.
 */
public class OnboardingToolsFragment extends Fragment {

    private static final String[] TOOL_EMOJIS     = { "🌬️", "🎵", "👁️", "🔢" };
    private static final String[] TOOL_LABELS     = { "Breathing", "Sound therapy", "Visual grounding", "Countdown" };
    private static final String[] TOOL_CONSTANTS  = {
            AppConstants.CALM_BREATHING,
            AppConstants.CALM_SOUND,
            AppConstants.CALM_VISUAL,
            AppConstants.CALM_COUNTDOWN
    };

    // Track which tools are selected (all on by default)
    private final boolean[] selected = { true, true, true, true };

    // Sound track selection
    private String selectedSound = AppConstants.SOUND_RAIN;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        buildToolCards(root);
        setupSoundChips(root);
    }

    // ─── Build tool toggle cards into gl_tools ────────────────────────────────

    private static final int COLOR_SELECTED_BG     = 0xFFEDE9FF; // light purple fill
    private static final int COLOR_SELECTED_STROKE = 0xFF534AB7; // purple border
    private static final int COLOR_UNSELECTED_STROKE = 0xFFDDDDDD; // grey border

    private void applyCardStyle(com.google.android.material.card.MaterialCardView card,
                                android.widget.TextView tvLabel,
                                android.widget.TextView tvCheck, boolean isSelected) {
        if (isSelected) {
            card.setCardBackgroundColor(COLOR_SELECTED_BG);
            card.setStrokeColor(COLOR_SELECTED_STROKE);
            card.setStrokeWidth(dpToPx(2));
            card.setCardElevation(dpToPx(4));
            card.setAlpha(1.0f);
            tvLabel.setTextColor(0xFF3C3489);
            tvCheck.setVisibility(View.VISIBLE);
        } else {
            android.util.TypedValue tv = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
            card.setCardBackgroundColor(tv.data);
            card.setStrokeColor(COLOR_UNSELECTED_STROKE);
            card.setStrokeWidth(dpToPx(1));
            card.setCardElevation(dpToPx(1));
            card.setAlpha(0.85f);
            requireContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
            tvLabel.setTextColor(tv.data);
            tvCheck.setVisibility(View.INVISIBLE);
        }
    }

    private void buildToolCards(View root) {
        GridLayout grid = root.findViewById(R.id.gl_tools);
        if (grid == null) return;
        grid.removeAllViews();
        grid.setColumnCount(1);

        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        int textColor = tv.data;

        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        int unselectedBg = tv.data;


        int cardHeightPx = dpToPx(72);
        int marginPx = dpToPx(6);

        for (int i = 0; i < TOOL_LABELS.length; i++) {
            com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(requireContext());

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = cardHeightPx;
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            card.setLayoutParams(lp);
            card.setRadius(dpToPx(12));
            card.setClickable(true);
            card.setFocusable(true);

            // Inner layout
            android.widget.LinearLayout inner = new android.widget.LinearLayout(requireContext());
            inner.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
            inner.setPadding(dpToPx(14), 0, dpToPx(14), 0);
            inner.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT));

            android.widget.TextView tvEmoji = new android.widget.TextView(requireContext());
            tvEmoji.setText(TOOL_EMOJIS[i]);
            tvEmoji.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28);
            tvEmoji.setGravity(android.view.Gravity.CENTER);
            tvEmoji.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            android.widget.TextView tvLabel = new android.widget.TextView(requireContext());
            tvLabel.setText(TOOL_LABELS[i]);
            tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            tvLabel.setGravity(android.view.Gravity.CENTER_VERTICAL);
            android.widget.LinearLayout.LayoutParams labelLp =
                    new android.widget.LinearLayout.LayoutParams(
                            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.weight = 1f;
            labelLp.setMarginStart(dpToPx(12));
            tvLabel.setLayoutParams(labelLp);

            tvLabel.setTextColor(textColor);

            // Checkmark indicator on the right
            android.widget.TextView tvCheck = new android.widget.TextView(requireContext());
            tvCheck.setText("✓");
            tvCheck.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
            tvCheck.setTextColor(COLOR_SELECTED_STROKE);
            tvCheck.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCheck.setGravity(android.view.Gravity.CENTER);
            tvCheck.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            inner.addView(tvEmoji);
            inner.addView(tvLabel);
            inner.addView(tvCheck);
            card.addView(inner);

            // Apply initial style (all selected by default)
            applyCardStyle(card, tvLabel, tvCheck, selected[i]);
            card.setContentDescription(TOOL_LABELS[i] + " calming tool, selected");

            final int index = i;
            card.setOnClickListener(v -> {
                // Must keep at least one selected
                int selectedCount = 0;
                for (boolean b : selected) if (b) selectedCount++;
                if (selected[index] && selectedCount <= 1) return;

                selected[index] = !selected[index];
                applyCardStyle(card, tvLabel, tvCheck, selected[index]);
                card.setContentDescription(TOOL_LABELS[index] +
                        (selected[index] ? " selected" : " not selected"));
            });

            grid.addView(card);
        }
    }

    // ─── Sound chips ─────────────────────────────────────────────────────────

    private void setupSoundChips(View root) {
        ChipGroup cg = root.findViewById(R.id.cg_sounds);
        if (cg == null) return;
        cg.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chip_rain)       selectedSound = AppConstants.SOUND_RAIN;
            else if (id == R.id.chip_ocean)      selectedSound = AppConstants.SOUND_OCEAN;
            else if (id == R.id.chip_forest)     selectedSound = AppConstants.SOUND_FOREST;
            else if (id == R.id.chip_white_noise)selectedSound = AppConstants.SOUND_WHITE_NOISE;
            else if (id == R.id.chip_music)      selectedSound = AppConstants.SOUND_GENTLE_MUSIC;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    // ─── Data accessors ───────────────────────────────────────────────────────

    public String getSelectedTools() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) list.add(TOOL_CONSTANTS[i]);
        }
        if (list.isEmpty()) return AppConstants.CALM_BREATHING;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public String getSelectedSound() {
        return selectedSound;
    }
}
