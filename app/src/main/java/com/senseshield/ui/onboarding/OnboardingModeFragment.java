package com.senseshield.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.senseshield.R;
import com.senseshield.utils.AppConstants;

/**
 * OnboardingModeFragment.java
 * Step 1 of onboarding — choose user mode.
 * Two large illustrated cards: "This is for me" vs "I'm a caregiver/teacher"
 */
public class OnboardingModeFragment extends Fragment {

    private MaterialCardView cardPerson;
    private MaterialCardView cardCaregiver;
    private TextView         checkPerson;
    private TextView         checkCaregiver;

    private int selectedMode = AppConstants.MODE_PERSON; // default

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_mode, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        cardPerson     = root.findViewById(R.id.card_mode_person);
        cardCaregiver  = root.findViewById(R.id.card_mode_caregiver);
        checkPerson    = root.findViewById(R.id.check_mode_person);
        checkCaregiver = root.findViewById(R.id.check_mode_caregiver);

        cardPerson.setOnClickListener(v -> selectMode(AppConstants.MODE_PERSON));
        cardCaregiver.setOnClickListener(v -> selectMode(AppConstants.MODE_CAREGIVER));

        // Restore previously selected mode (survives theme recreate)
        if (getActivity() != null) {
            int savedMode = getActivity()
                .getSharedPreferences(AppConstants.PREFS_NAME, 0)
                .getInt("onboarding_mode", AppConstants.MODE_PERSON);
            selectMode(savedMode);
        } else {
            selectMode(AppConstants.MODE_PERSON);
        }
    }

    private void selectMode(int mode) {
        selectedMode = mode;

        boolean isPerson = (mode == AppConstants.MODE_PERSON);

        // Person card
        cardPerson.setCardElevation(isPerson ? 8f : 0f);
        cardPerson.setStrokeWidth(isPerson ? 3 : 2);
        checkPerson.setVisibility(isPerson ? View.VISIBLE : View.INVISIBLE);
        cardPerson.animate().scaleX(isPerson ? 1.02f : 1.0f)
            .scaleY(isPerson ? 1.02f : 1.0f).setDuration(150).start();

        // Caregiver card
        cardCaregiver.setCardElevation(!isPerson ? 8f : 0f);
        cardCaregiver.setStrokeWidth(!isPerson ? 3 : 2);
        checkCaregiver.setVisibility(!isPerson ? View.VISIBLE : View.INVISIBLE);
        cardCaregiver.animate().scaleX(!isPerson ? 1.02f : 1.0f)
            .scaleY(!isPerson ? 1.02f : 1.0f).setDuration(150).start();

        // Announce to TalkBack
        String announcement = isPerson
            ? "Set up for myself selected"
            : "Caregiver or teacher selected";
        (isPerson ? cardPerson : cardCaregiver).announceForAccessibility(announcement);
    }

    public int getSelectedMode() {
        return selectedMode;
    }
}
