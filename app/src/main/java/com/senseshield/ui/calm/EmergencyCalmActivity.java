package com.senseshield.ui.calm;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.senseshield.R;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryEvent;
import com.senseshield.utils.AccessibilityUtils;
import com.senseshield.utils.AppConstants;

/**
 * EmergencyCalmActivity — full calm session, launched by Emergency button or Quick Tile.
 *
 * Six phases in sequence:
 *   LANDING   (0) → "You are safe." 2-second auto-advance
 *   BREATHING (1) → 4-4-6 box breathing, 3 cycles, animated circle
 *   VISUAL    (2) → 5-4-3-2-1 grounding technique (NEW)
 *   SOUND     (3) → user's preferred sound via MediaPlayer
 *   COUNTDOWN (4) → countdown timer 30/60/90 s (NEW)
 *   REFLECTION(5) → "Did that help?" — stores effectiveness in DB
 */
public class EmergencyCalmActivity extends AppCompatActivity {

    // ─── Intent extras ────────────────────────────────────────────────────
    public static final String EXTRA_FROM_ALERT      = "from_alert";
    public static final String EXTRA_USER_ID         = "user_id";
    public static final String EXTRA_PREFERRED_SOUND = "preferred_sound";

    // ─── Breathing timing ─────────────────────────────────────────────────
    private static final int INHALE_SECONDS = 4;
    private static final int HOLD_SECONDS   = 4;
    private static final int EXHALE_SECONDS = 6;
    private static final int TOTAL_CYCLES   = 3;

    // ─── Phase constants ──────────────────────────────────────────────────
    private static final int PHASE_LANDING    = 0;
    private static final int PHASE_BREATHING  = 1;
    private static final int PHASE_VISUAL     = 2;
    private static final int PHASE_SOUND      = 3;
    private static final int PHASE_COUNTDOWN  = 4;
    private static final int PHASE_PMR        = 5;
    private static final int PHASE_REFLECTION = 6;

    // ─── PMR data (5 muscle groups) ───────────────────────────────────────
    private static final String[] PMR_EMOJIS   = { "✊", "💪", "🤷", "😬", "🧘" };
    private static final String[] PMR_MUSCLES  = { "Hands", "Arms", "Shoulders", "Face", "Whole Body" };
    private static final String[] PMR_TENSE    = {
        "Squeeze your fists as tight as you can",
        "Curl your arms up and tense your biceps",
        "Raise your shoulders up to your ears",
        "Scrunch your eyes and face tightly",
        "Tense every muscle in your body at once"
    };
    private static final String[] PMR_RELEASE  = {
        "Release... let your hands fall open",
        "Release... let your arms drop gently",
        "Release... let your shoulders drop",
        "Release... let your face soften",
        "Release... let your whole body go limp"
    };
    private static final int PMR_TENSE_SECONDS   = 5;
    private static final int PMR_RELEASE_SECONDS = 10;

    // ─── Visual grounding data (5-4-3-2-1) ───────────────────────────────
    private static final String[] GROUNDING_NUMBERS = {"5", "4", "3", "2", "1"};
    private static final String[] GROUNDING_EMOJIS  = {"👀", "🤚", "👂", "👃", "👅"};
    private static final String[] GROUNDING_SENSES  = {
        "things you can SEE",
        "things you can TOUCH",
        "things you can HEAR",
        "things you can SMELL",
        "things you can TASTE"
    };
    private static final String[] GROUNDING_HINTS = {
        "Look around slowly and name each one",
        "Notice textures, temperatures, surfaces",
        "Listen carefully — near and far away",
        "Any scents in the air or nearby",
        "What flavour can you notice right now?"
    };

    // ─── Views: phases ────────────────────────────────────────────────────
    private LinearLayout phaseLanding;
    private LinearLayout phaseBreathing;
    private LinearLayout phaseVisual;
    private LinearLayout phaseSound;
    private LinearLayout phaseCountdown;
    private LinearLayout phasePmr;
    private LinearLayout phaseReflection;

    // ─── Views: breathing ─────────────────────────────────────────────────
    private View         breathingCircle;
    private TextView     tvBreathingInstruction;
    private TextView     tvBreathingCount;
    private TextView     tvCycleCount;
    private TextView     btnStopBreathing;

    // ─── Views: visual grounding ──────────────────────────────────────────
    private TextView       tvGroundingStep;
    private TextView       tvGroundingNumber;
    private TextView       tvGroundingEmoji;
    private TextView       tvGroundingInstruction;
    private TextView       tvGroundingHint;
    private MaterialButton btnGroundingNext;
    private TextView       btnSkipGrounding;

    // ─── Views: sound ─────────────────────────────────────────────────────
    private TextView       tvSoundName;
    private ChipGroup      cgSoundPicker;
    private MaterialButton btnStopSound;

    // ─── Views: countdown ─────────────────────────────────────────────────
    private TextView   tvCountdownNumber;
    private ChipGroup  cgCountdownDuration;
    private TextView   btnSkipCountdown;

    // ─── Views: PMR ───────────────────────────────────────────────────────
    private TextView   tvPmrStep;
    private TextView   tvPmrEmoji;
    private TextView   tvPmrMuscle;
    private TextView   tvPmrAction;
    private TextView   tvPmrCount;
    private TextView   tvPmrPhaseLabel;
    private TextView   btnSkipPmr;

    // ─── Views: reflection ────────────────────────────────────────────────
    private MaterialButton btnYesHelped;
    private MaterialButton btnNoHelped;
    private TextView       btnSkipReflection;

    // ─── State ────────────────────────────────────────────────────────────
    private int            currentPhase     = PHASE_LANDING;
    private int            currentCycle     = 1;
    private int            groundingStep    = 0;
    private int            countdownSeconds = 60;
    private int            pmrStep          = 0;
    private boolean        pmrTensing       = true;
    private boolean        animationsEnabled;
    private MediaPlayer    mediaPlayer;
    private CountDownTimer breathingTimer;
    private CountDownTimer countdownTimer;
    private CountDownTimer pmrTimer;
    private Handler        handler = new Handler(Looper.getMainLooper());

    // ─── Data ─────────────────────────────────────────────────────────────
    private int            userId         = -1;
    private boolean        fromAlert      = false;
    private String         preferredSound = AppConstants.SOUND_RAIN;
    private long           sessionEventId = -1;
    private DatabaseHelper db;

    // ─── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean fromAuto = getIntent().getBooleanExtra("FROM_AUTO_TRIGGER", false);
        boolean noiseHigh = getIntent().getBooleanExtra("NOISE_HIGH", false);
        boolean hrHigh = getIntent().getBooleanExtra("HR_HIGH", false);
        TextView tvReason = findViewById(R.id.tv_reason);

        if (fromAuto && tvReason != null) {

            if (noiseHigh && hrHigh) {
                tvReason.setText("We noticed both your environment and body signals were elevated, so we opened calm mode to support you.");

            } else if (noiseHigh) {
                tvReason.setText("We detected a loud environment, so we opened calm mode to help you settle.");

            } else if (hrHigh) {
                tvReason.setText("Your heart rate seemed elevated, so we opened calm mode to help you relax.");
            }
        }
        setTheme(R.style.Theme_SenseShield_Calm);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_emergency_calm);

        db = DatabaseHelper.getInstance(this);
        animationsEnabled = AccessibilityUtils.areAnimationsEnabled(this);

        readExtras();
        bindViews();
        setupClickListeners();
        logSessionStart();

        showPhase(PHASE_LANDING);
        handler.postDelayed(() -> showPhase(PHASE_BREATHING), 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBreathingTimer();
        stopCountdownTimer();
        stopPmrTimer();
        releaseMediaPlayer();
        handler.removeCallbacksAndMessages(null);
    }

    // ─── Extras ───────────────────────────────────────────────────────────

    private void readExtras() {
        Intent i       = getIntent();
        fromAlert      = i.getBooleanExtra(EXTRA_FROM_ALERT,     false);
        userId         = i.getIntExtra(EXTRA_USER_ID,            -1);
        preferredSound = i.getStringExtra(EXTRA_PREFERRED_SOUND);
        if (preferredSound == null) preferredSound = AppConstants.SOUND_RAIN;
    }

    // ─── View binding ─────────────────────────────────────────────────────

    private void bindViews() {
        phaseLanding    = findViewById(R.id.phase_landing);
        phaseBreathing  = findViewById(R.id.phase_breathing);
        phaseVisual     = findViewById(R.id.phase_visual);
        phaseSound      = findViewById(R.id.phase_sound);
        phaseCountdown  = findViewById(R.id.phase_countdown);
        phasePmr        = findViewById(R.id.phase_pmr);
        phaseReflection = findViewById(R.id.phase_reflection);

        // PMR views
        tvPmrStep       = findViewById(R.id.tv_pmr_step);
        tvPmrEmoji      = findViewById(R.id.tv_pmr_emoji);
        tvPmrMuscle     = findViewById(R.id.tv_pmr_muscle);
        tvPmrAction     = findViewById(R.id.tv_pmr_action);
        tvPmrCount      = findViewById(R.id.tv_pmr_count);
        tvPmrPhaseLabel = findViewById(R.id.tv_pmr_phase_label);
        btnSkipPmr      = findViewById(R.id.btn_skip_pmr);

        // Breathing
        breathingCircle        = findViewById(R.id.breathing_circle);
        tvBreathingInstruction = findViewById(R.id.tv_breathing_instruction);
        tvBreathingCount       = findViewById(R.id.tv_breathing_count);
        tvCycleCount           = findViewById(R.id.tv_cycle_count);
        btnStopBreathing       = findViewById(R.id.btn_stop_breathing);

        // Visual grounding
        tvGroundingStep        = findViewById(R.id.tv_grounding_step);
        tvGroundingNumber      = findViewById(R.id.tv_grounding_number);
        tvGroundingEmoji       = findViewById(R.id.tv_grounding_emoji);
        tvGroundingInstruction = findViewById(R.id.tv_grounding_instruction);
        tvGroundingHint        = findViewById(R.id.tv_grounding_hint);
        btnGroundingNext       = findViewById(R.id.btn_grounding_next);
        btnSkipGrounding       = findViewById(R.id.btn_skip_grounding);

        // Sound
        tvSoundName   = findViewById(R.id.tv_sound_name);
        cgSoundPicker = findViewById(R.id.cg_sound_picker);
        btnStopSound  = findViewById(R.id.btn_stop_sound);

        // Countdown
        tvCountdownNumber   = findViewById(R.id.tv_countdown_number);
        cgCountdownDuration = findViewById(R.id.cg_countdown_duration);
        btnSkipCountdown    = findViewById(R.id.btn_skip_countdown);

        // Reflection
        btnYesHelped      = findViewById(R.id.btn_yes_helped);
        btnNoHelped       = findViewById(R.id.btn_no_helped);
        btnSkipReflection = findViewById(R.id.btn_skip_reflection);
    }

    // ─── Click listeners ──────────────────────────────────────────────────

    private void setupClickListeners() {
        // Skip breathing → visual grounding
        if (btnStopBreathing != null)
            btnStopBreathing.setOnClickListener(v -> {
                stopBreathingTimer();
                showPhase(PHASE_VISUAL);
            });

        // Visual grounding: advance step
        if (btnGroundingNext != null)
            btnGroundingNext.setOnClickListener(v -> advanceGroundingStep());

        // Visual grounding: skip → sound
        if (btnSkipGrounding != null)
            btnSkipGrounding.setOnClickListener(v -> showPhase(PHASE_SOUND));

        // Sound chip selection
        if (cgSoundPicker != null)
            cgSoundPicker.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                playSound(chipIdToTrackName(id));
                if (tvSoundName != null) tvSoundName.setText(chipIdToDisplayName(id));
            });

        // "I feel better" → countdown
        if (btnStopSound != null)
            btnStopSound.setOnClickListener(v -> {
                stopSound();
                showPhase(PHASE_COUNTDOWN);
            });

        // Countdown duration chips — restart timer with new duration
        if (cgCountdownDuration != null)
            cgCountdownDuration.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int id = checkedIds.get(0);
                if      (id == R.id.chip_30s) countdownSeconds = 30;
                else if (id == R.id.chip_90s) countdownSeconds = 90;
                else                          countdownSeconds = 60;
                stopCountdownTimer();
                startCountdownTimer();
            });

        // Skip countdown → PMR
        if (btnSkipCountdown != null)
            btnSkipCountdown.setOnClickListener(v -> {
                stopCountdownTimer();
                showPhase(PHASE_PMR);
            });

        // Skip PMR → reflection
        if (btnSkipPmr != null)
            btnSkipPmr.setOnClickListener(v -> {
                stopPmrTimer();
                showPhase(PHASE_REFLECTION);
            });

        // Reflection buttons
        if (btnYesHelped  != null) btnYesHelped.setOnClickListener(v  -> finishSession(5));
        if (btnNoHelped   != null) btnNoHelped.setOnClickListener(v   -> finishSession(2));
        if (btnSkipReflection != null) btnSkipReflection.setOnClickListener(v -> finishSession(0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    private void showPhase(int phase) {
        currentPhase = phase;
        setPhaseVisible(phaseLanding,    phase == PHASE_LANDING);
        setPhaseVisible(phaseBreathing,  phase == PHASE_BREATHING);
        setPhaseVisible(phaseVisual,     phase == PHASE_VISUAL);
        setPhaseVisible(phaseSound,      phase == PHASE_SOUND);
        setPhaseVisible(phaseCountdown,  phase == PHASE_COUNTDOWN);
        setPhaseVisible(phasePmr,        phase == PHASE_PMR);
        setPhaseVisible(phaseReflection, phase == PHASE_REFLECTION);

        switch (phase) {
            case PHASE_BREATHING:  startBreathingExercise(); break;
            case PHASE_VISUAL:     startVisualGrounding();   break;
            case PHASE_SOUND:      startSoundPhase();        break;
            case PHASE_COUNTDOWN:  startCountdownPhase();    break;
            case PHASE_PMR:        startPmrPhase();          break;
            case PHASE_REFLECTION:
                AccessibilityUtils.announce(phaseReflection,
                    getString(R.string.calm_did_it_help));
                break;
        }
    }

    private void setPhaseVisible(View phase, boolean visible) {
        if (phase == null) return;
        if (animationsEnabled && !visible) {
            phase.animate().alpha(0f).setDuration(300)
                .withEndAction(() -> phase.setVisibility(View.GONE)).start();
        } else if (animationsEnabled) {
            phase.setAlpha(0f);
            phase.setVisibility(View.VISIBLE);
            phase.animate().alpha(1f).setDuration(400).start();
        } else {
            phase.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BREATHING EXERCISE
    // ═══════════════════════════════════════════════════════════════════════

    private void startBreathingExercise() {
        currentCycle = 1;
        updateCycleLabel();
        AccessibilityUtils.announce(phaseBreathing,
            "Breathing exercise. Breathe in for 4, hold for 4, breathe out for 6.");
        runInhalePhase();
    }

    private void runInhalePhase() {
        setBreathingState(getString(R.string.calm_breathe_in), INHALE_SECONDS,
            getResources().getColor(R.color.breathe_inhale, getTheme()), 240);
        breathingTimer = new CountDownTimer(INHALE_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) { updateCountdown((int)(ms / 1000) + 1); }
            @Override public void onFinish()       { runHoldPhase(); }
        }.start();
    }

    private void runHoldPhase() {
        setBreathingState(getString(R.string.calm_hold), HOLD_SECONDS,
            getResources().getColor(R.color.breathe_hold, getTheme()), 240);
        breathingTimer = new CountDownTimer(HOLD_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) { updateCountdown((int)(ms / 1000) + 1); }
            @Override public void onFinish()       { runExhalePhase(); }
        }.start();
    }

    private void runExhalePhase() {
        setBreathingState(getString(R.string.calm_breathe_out), EXHALE_SECONDS,
            getResources().getColor(R.color.breathe_exhale, getTheme()), 120);
        breathingTimer = new CountDownTimer(EXHALE_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) { updateCountdown((int)(ms / 1000) + 1); }
            @Override public void onFinish() {
                if (currentCycle < TOTAL_CYCLES) {
                    currentCycle++;
                    updateCycleLabel();
                    runInhalePhase();
                } else {
                    showPhase(PHASE_VISUAL);  // breathing done → visual grounding
                }
            }
        }.start();
    }

    private void setBreathingState(String instruction, int seconds,
                                   int circleColor, int targetSizeDp) {
        if (tvBreathingInstruction != null) tvBreathingInstruction.setText(instruction);
        updateCountdown(seconds);
        if (breathingCircle == null) return;

        int targetPx = (int)(targetSizeDp * getResources().getDisplayMetrics().density);
        if (animationsEnabled) {
            ValueAnimator sizeAnim = ValueAnimator.ofInt(
                breathingCircle.getLayoutParams().width, targetPx);
            sizeAnim.setDuration(seconds * 1000L);
            sizeAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            sizeAnim.addUpdateListener(a -> {
                int val = (int) a.getAnimatedValue();
                ViewGroup.LayoutParams lp = breathingCircle.getLayoutParams();
                lp.width = val; lp.height = val;
                breathingCircle.setLayoutParams(lp);
            });
            sizeAnim.start();

            ValueAnimator colorAnim = ValueAnimator.ofArgb(getCurrentCircleColor(), circleColor);
            colorAnim.setDuration(600);
            colorAnim.addUpdateListener(a ->
                breathingCircle.getBackground().setTint((int) a.getAnimatedValue()));
            colorAnim.start();
        } else {
            ViewGroup.LayoutParams lp = breathingCircle.getLayoutParams();
            lp.width = targetPx; lp.height = targetPx;
            breathingCircle.setLayoutParams(lp);
            breathingCircle.getBackground().setTint(circleColor);
        }
    }

    private int getCurrentCircleColor() {
        return getResources().getColor(R.color.breathe_inhale, getTheme());
    }

    private void updateCountdown(int seconds) {
        if (tvBreathingCount != null) tvBreathingCount.setText(String.valueOf(seconds));
    }

    private void updateCycleLabel() {
        if (tvCycleCount != null)
            tvCycleCount.setText("Cycle " + currentCycle + " of " + TOTAL_CYCLES);
    }

    private void stopBreathingTimer() {
        if (breathingTimer != null) { breathingTimer.cancel(); breathingTimer = null; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VISUAL GROUNDING  (5-4-3-2-1 technique)
    // ═══════════════════════════════════════════════════════════════════════

    private void startVisualGrounding() {
        groundingStep = 0;
        updateGroundingStep(0);
        AccessibilityUtils.announce(phaseVisual,
            "Grounding exercise. Step 1 of 5. Name 5 things you can see.");
    }

    /** Tap "Done ✓" → advance step; after step 5 move to SOUND phase. */
    private void advanceGroundingStep() {
        groundingStep++;
        if (groundingStep >= GROUNDING_NUMBERS.length) {
            showPhase(PHASE_SOUND);
        } else {
            updateGroundingStep(groundingStep);
        }
    }

    private void updateGroundingStep(int step) {
        if (tvGroundingStep        != null) tvGroundingStep.setText("Step " + (step + 1) + " of 5");
        if (tvGroundingNumber      != null) tvGroundingNumber.setText(GROUNDING_NUMBERS[step]);
        if (tvGroundingEmoji       != null) tvGroundingEmoji.setText(GROUNDING_EMOJIS[step]);
        if (tvGroundingInstruction != null) tvGroundingInstruction.setText(GROUNDING_SENSES[step]);
        if (tvGroundingHint        != null) tvGroundingHint.setText(GROUNDING_HINTS[step]);

        // Subtle fade on each step change
        if (animationsEnabled && phaseVisual != null) {
            phaseVisual.setAlpha(0.5f);
            phaseVisual.animate().alpha(1f).setDuration(300).start();
        }

        AccessibilityUtils.announce(phaseVisual,
            "Step " + (step + 1) + " of 5. " +
            GROUNDING_NUMBERS[step] + " " + GROUNDING_SENSES[step] + ". " +
            GROUNDING_HINTS[step]);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SOUND THERAPY
    // ═══════════════════════════════════════════════════════════════════════

    private void startSoundPhase() {
        playSound(preferredSound);
        updateSoundLabel(preferredSound);
        AccessibilityUtils.announce(phaseSound,
            "Sound therapy. Playing " + soundTrackToDisplayName(preferredSound));
    }

    private void playSound(String trackConstant) {
        releaseMediaPlayer();
        int rawResId = soundTrackToRawResId(trackConstant);
        if (rawResId == 0) return;
        try {
            mediaPlayer = MediaPlayer.create(this, rawResId);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0.7f, 0.7f);
                mediaPlayer.start();
            }
        } catch (Exception e) { mediaPlayer = null; }
    }

    private void stopSound() { releaseMediaPlayer(); }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void updateSoundLabel(String trackConstant) {
        if (tvSoundName != null) tvSoundName.setText(soundTrackToDisplayName(trackConstant));
    }

    private int soundTrackToRawResId(String t) {
        if (t == null) return R.raw.sound_rain;
        switch (t) {
            case AppConstants.SOUND_RAIN:         return R.raw.sound_rain;
            case AppConstants.SOUND_OCEAN:        return R.raw.sound_ocean;
            case AppConstants.SOUND_FOREST:       return R.raw.sound_forest;
            case AppConstants.SOUND_WHITE_NOISE:  return R.raw.sound_white_noise;
            case AppConstants.SOUND_GENTLE_MUSIC: return R.raw.sound_gentle_music;
            default:                              return R.raw.sound_rain;
        }
    }

    private String soundTrackToDisplayName(String t) {
        if (t == null) return "Rain";
        switch (t) {
            case AppConstants.SOUND_RAIN:         return "Rain";
            case AppConstants.SOUND_OCEAN:        return "Ocean";
            case AppConstants.SOUND_FOREST:       return "Forest";
            case AppConstants.SOUND_WHITE_NOISE:  return "White noise";
            case AppConstants.SOUND_GENTLE_MUSIC: return "Gentle music";
            default:                              return "Rain";
        }
    }

    private String chipIdToTrackName(int chipId) {
        if      (chipId == R.id.chip_sound_rain)   return AppConstants.SOUND_RAIN;
        else if (chipId == R.id.chip_sound_ocean)  return AppConstants.SOUND_OCEAN;
        else if (chipId == R.id.chip_sound_forest) return AppConstants.SOUND_FOREST;
        else if (chipId == R.id.chip_sound_white)  return AppConstants.SOUND_WHITE_NOISE;
        else                                        return AppConstants.SOUND_RAIN;
    }

    private String chipIdToDisplayName(int chipId) {
        if      (chipId == R.id.chip_sound_rain)   return "Rain";
        else if (chipId == R.id.chip_sound_ocean)  return "Ocean";
        else if (chipId == R.id.chip_sound_forest) return "Forest";
        else if (chipId == R.id.chip_sound_white)  return "White noise";
        else                                        return "Rain";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COUNTDOWN TIMER
    // ═══════════════════════════════════════════════════════════════════════

    private void startCountdownPhase() {
        countdownSeconds = 60; // default each time
        if (tvCountdownNumber != null) tvCountdownNumber.setText("60");
        startCountdownTimer();
        AccessibilityUtils.announce(phaseCountdown,
            "Countdown timer. 60 seconds. Take your time and breathe slowly.");
    }

    private void startCountdownTimer() {
        stopCountdownTimer();
        if (tvCountdownNumber != null) tvCountdownNumber.setText(String.valueOf(countdownSeconds));
        countdownTimer = new CountDownTimer(countdownSeconds * 1000L, 1000) {
            @Override public void onTick(long ms) {
                int secs = (int)(ms / 1000) + 1;
                if (tvCountdownNumber != null) tvCountdownNumber.setText(String.valueOf(secs));
            }
            @Override public void onFinish() {
                if (tvCountdownNumber != null) tvCountdownNumber.setText("0");
                handler.postDelayed(() -> showPhase(PHASE_PMR), 800);
            }
        }.start();
    }

    private void stopCountdownTimer() {
        if (countdownTimer != null) { countdownTimer.cancel(); countdownTimer = null; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROGRESSIVE MUSCLE RELAXATION
    // ═══════════════════════════════════════════════════════════════════════

    private void startPmrPhase() {
        pmrStep    = 0;
        pmrTensing = true;
        updatePmrStep();
        AccessibilityUtils.announce(phasePmr,
            "Progressive muscle relaxation. We will work through 5 muscle groups.");
    }

    private void updatePmrStep() {
        if (tvPmrStep   != null) tvPmrStep.setText("Group " + (pmrStep + 1) + " of " + PMR_MUSCLES.length);
        if (tvPmrEmoji  != null) tvPmrEmoji.setText(PMR_EMOJIS[pmrStep]);
        if (tvPmrMuscle != null) tvPmrMuscle.setText(PMR_MUSCLES[pmrStep]);
        runPmrTensePhase();
    }

    private void runPmrTensePhase() {
        pmrTensing = true;
        if (tvPmrAction     != null) tvPmrAction.setText(PMR_TENSE[pmrStep]);
        if (tvPmrPhaseLabel != null) tvPmrPhaseLabel.setText("TENSE");
        if (tvPmrCount      != null) tvPmrCount.setText(String.valueOf(PMR_TENSE_SECONDS));

        AccessibilityUtils.announce(phasePmr, PMR_TENSE[pmrStep] + ". Hold for 5 seconds.");

        stopPmrTimer();
        pmrTimer = new CountDownTimer(PMR_TENSE_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) {
                int secs = (int)(ms / 1000) + 1;
                if (tvPmrCount != null) tvPmrCount.setText(String.valueOf(secs));
            }
            @Override public void onFinish() { runPmrReleasePhase(); }
        }.start();
    }

    private void runPmrReleasePhase() {
        pmrTensing = false;
        if (tvPmrAction     != null) tvPmrAction.setText(PMR_RELEASE[pmrStep]);
        if (tvPmrPhaseLabel != null) tvPmrPhaseLabel.setText("RELEASE");
        if (tvPmrCount      != null) tvPmrCount.setText(String.valueOf(PMR_RELEASE_SECONDS));

        AccessibilityUtils.announce(phasePmr, PMR_RELEASE[pmrStep] + ". Relax for 10 seconds.");

        stopPmrTimer();
        pmrTimer = new CountDownTimer(PMR_RELEASE_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) {
                int secs = (int)(ms / 1000) + 1;
                if (tvPmrCount != null) tvPmrCount.setText(String.valueOf(secs));
            }
            @Override public void onFinish() {
                pmrStep++;
                if (pmrStep >= PMR_MUSCLES.length) {
                    // All groups done → reflection
                    showPhase(PHASE_REFLECTION);
                } else {
                    updatePmrStep();
                }
            }
        }.start();
    }

    private void stopPmrTimer() {
        if (pmrTimer != null) { pmrTimer.cancel(); pmrTimer = null; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SESSION LOGGING + REFLECTION
    // ═══════════════════════════════════════════════════════════════════════

    private void logSessionStart() {
        if (userId == -1) return;
        SensoryEvent event = new SensoryEvent(
            userId, AppConstants.TRIGGER_UNKNOWN,
            AppConstants.SEVERITY_MODERATE, AppConstants.LOCATION_OTHER);
        event.setCalmToolUsed(AppConstants.CALM_BREATHING);
        event.setFromProactiveAlert(fromAlert);
        sessionEventId = db.insertEvent(event);
    }

    private void finishSession(int effectiveness) {
        stopSound();
        stopBreathingTimer();
        stopCountdownTimer();
        if (sessionEventId != -1) {
            SensoryEvent event = new SensoryEvent();
            event.setId((int) sessionEventId);
            event.setEffectiveness(effectiveness);
            event.setCalmToolUsed(AppConstants.CALM_SOUND);
            db.updateEvent(event);
        }
        finish();
    }
}
