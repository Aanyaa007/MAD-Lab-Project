package com.senseshield.ui.caregiver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryProfile;
import com.senseshield.models.User;
import com.senseshield.ui.profile.ProfileActivity;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.UiClickUtils;

import java.util.Calendar;
import java.util.List;

/**
 * CaregiverHomeActivity — home screen for users who signed up as caregivers/teachers.
 * Shows a list of their linked patients and allows adding new ones.
 */
public class CaregiverHomeActivity extends AppCompatActivity
        implements PatientAdapter.OnPatientClickListener {

    private TextView         tvGreeting;
    private TextView         tvName;
    private TextView         tvAvatar;
    private RecyclerView     rvPatients;
    private LinearLayout     llEmpty;
    private MaterialButton   btnAddPatient;
    private MaterialButton   btnLogout;

    private DatabaseHelper   db;
    private int              caregiverId = -1;
    private User             caregiverUser;
    private List<User>       patients;
    private PatientAdapter   adapter;

    private int appliedThemeResId = -1;

    // Avatar color palette (same as profile)
    private static final String[] AVATAR_COLORS = {
            "#7F77DD", "#1D9E75", "#D85A30", "#378ADD", "#BA7517", "#D4537E"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = SenseShieldApp.getThemeResId(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caregiver_home);

        db = DatabaseHelper.getInstance(this);

        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
        caregiverId = prefs.getInt(AppConstants.PREF_CURRENT_USER_ID, -1);
        if (caregiverId != -1) caregiverUser = db.getUserById(caregiverId);

        bindViews();
        populateHeader();
        setupClickListeners();
        loadPatients();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SenseShieldApp.getThemeResId(this) != appliedThemeResId) {
            recreate();
            return;
        }
        setGreeting();
        loadPatients();
    }

    private void bindViews() {
        tvGreeting    = findViewById(R.id.tv_cg_greeting);
        tvName        = findViewById(R.id.tv_cg_name);
        tvAvatar      = findViewById(R.id.tv_cg_avatar);
        rvPatients    = findViewById(R.id.rv_patients);
        llEmpty       = findViewById(R.id.ll_empty_patients);
        btnAddPatient = findViewById(R.id.btn_add_patient);
        btnLogout     = findViewById(R.id.btn_cg_logout);

        rvPatients.setLayoutManager(new LinearLayoutManager(this));
    }

    private void populateHeader() {
        setGreeting();
        if (caregiverUser != null) {
            tvName.setText(caregiverUser.getName());
            tvAvatar.setText(caregiverUser.getInitials());
            try {
                tvAvatar.getBackground().setTint(
                        Color.parseColor(caregiverUser.getAvatarColor()));
            } catch (Exception e) {
                tvAvatar.getBackground().setTint(Color.parseColor("#7F77DD"));
            }
        }
    }

    private void setGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g;
        if      (hour >= 5  && hour < 12) g = "Good morning";
        else if (hour >= 12 && hour < 17) g = "Good afternoon";
        else                              g = "Good evening";
        tvGreeting.setText(g);
    }

    private void setupClickListeners() {
        UiClickUtils.setSafeClickListener(tvAvatar, v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        UiClickUtils.setSafeClickListener(btnAddPatient, v -> showAddPatientDialog());
        UiClickUtils.setSafeClickListener(btnLogout, v -> confirmLogout());
    }

    private void loadPatients() {
        if (caregiverId == -1) return;
        patients = db.getPatientsByCaregiver(caregiverId);

        if (patients.isEmpty()) {
            rvPatients.setVisibility(View.GONE);
            llEmpty.setVisibility(View.VISIBLE);
        } else {
            llEmpty.setVisibility(View.GONE);
            rvPatients.setVisibility(View.VISIBLE);
            adapter = new PatientAdapter(this, patients, db, this);
            rvPatients.setAdapter(adapter);
        }
    }

    @Override
    public void onPatientClick(User patient) {
        Intent intent = new Intent(this, PatientDetailActivity.class);
        intent.putExtra(PatientDetailActivity.EXTRA_PATIENT_ID, patient.getId());
        startActivity(intent);
    }

    // ── Add Patient Dialog ─────────────────────────────────────────────────────

    private void showAddPatientDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_patient, null);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Add a patient")
                .setView(dialogView)
                .setPositiveButton("Add", null) // Set later to prevent auto-dismiss on error
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            EditText etName = dialogView.findViewById(R.id.et_patient_name);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    etName.setError("Please enter a name");
                    return;
                }
                createPatient(name);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void createPatient(String name) {
        // Pick a color based on existing patient count for variety
        String color = AVATAR_COLORS[patients.size() % AVATAR_COLORS.length];

        User patient = new User(name, color, AppConstants.THEME_STANDARD);
        patient.setMode(AppConstants.MODE_PERSON);
        patient.setCaregiverId(caregiverId);

        long patientId = db.insertUser(patient);

        // Create a default sensory profile for them
        SensoryProfile profile = new SensoryProfile();
        profile.setUserId((int) patientId);
        profile.setPreferredCalmTools(AppConstants.CALM_BREATHING);
        profile.setPreferredSoundTrack(AppConstants.SOUND_RAIN);
        db.insertProfile(profile);

        loadPatients(); // Refresh list
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (d, w) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        SenseShieldApp.saveTheme(this, AppConstants.THEME_STANDARD);
        getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
                .edit().clear().commit();

        Intent intent = new Intent(this,
                com.senseshield.ui.SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
}