"""
SenseShield+ — Sensory Overload Risk Model — Training Script
=============================================================
Generates a research-grounded synthetic dataset, trains a small neural network,
and exports a TFLite model ready for on-device inference.

HOW TO RUN:
    pip install tensorflow scikit-learn numpy pandas
    python train_model.py

OUTPUT (copy both files into your Android project):
    sensory_overload_model.tflite  →  app/src/main/assets/
    model_metadata.json            →  app/src/main/assets/

Research sources encoded in the synthetic generator:
  [1] Marco et al. (2011) - Sensory processing in autism: a review of neurophysiologic findings
  [2] Baranek et al. (2006) - Sensory processing subtypes in autism
  [3] Green et al. (2013) - Sensory over-responsivity in ASD
  [4] Schmidt et al. (2018) - WESAD: wearable stress & affect detection (arousal curve shapes)
  [5] Kern et al. (2006) - Sensory processing in the everyday life of children with ASD
"""

import numpy as np
import pandas as pd
import json
import os
import sys

# ── Dependency check ──────────────────────────────────────────────────────────
try:
    import tensorflow as tf
    from sklearn.model_selection import train_test_split
    from sklearn.metrics import mean_absolute_error, accuracy_score
    print(f"TensorFlow {tf.__version__}  |  NumPy {np.__version__}")
except ImportError as e:
    print(f"\nMissing dependency: {e}")
    print("Run:  pip install tensorflow scikit-learn numpy pandas\n")
    sys.exit(1)

np.random.seed(42)
tf.random.set_seed(42)

# =============================================================================
# SECTION 1 — USER SENSITIVITY TYPES
# Based on Baranek et al. (2006): five sensory processing subtypes in ASD.
# Each entry: (mean, std) for each of 6 sensitivity dimensions, scale 1–5.
# =============================================================================

USER_TYPES = {
    # ~35% of ASD individuals: globally hyperresponsive across all modalities
    "high_all": {
        "weight":   0.35,
        "noise":    (4.2, 0.6),
        "light":    (4.0, 0.7),
        "crowd":    (4.3, 0.5),
        "texture":  (3.8, 0.8),
        "smell":    (3.9, 0.7),
        "change":   (4.1, 0.6),
    },
    # ~20%: primary auditory hyperresponsivity
    "noise_dominant": {
        "weight":   0.20,
        "noise":    (4.8, 0.3),
        "light":    (2.5, 0.8),
        "crowd":    (3.2, 0.9),
        "texture":  (2.3, 0.8),
        "smell":    (2.1, 0.7),
        "change":   (2.8, 0.9),
    },
    # ~15%: primary visual / light hyperresponsivity
    "light_dominant": {
        "weight":   0.15,
        "noise":    (2.4, 0.8),
        "light":    (4.7, 0.4),
        "crowd":    (2.8, 0.9),
        "texture":  (2.5, 0.8),
        "smell":    (2.2, 0.7),
        "change":   (2.6, 0.8),
    },
    # ~15%: primary social / crowd hyperresponsivity
    "social_dominant": {
        "weight":   0.15,
        "noise":    (3.0, 0.9),
        "light":    (2.5, 0.8),
        "crowd":    (4.9, 0.2),
        "texture":  (3.5, 0.8),
        "smell":    (2.4, 0.7),
        "change":   (3.8, 0.7),
    },
    # ~15%: moderate, mild sensitivity across all
    "moderate": {
        "weight":   0.15,
        "noise":    (2.8, 0.7),
        "light":    (2.6, 0.7),
        "crowd":    (3.0, 0.8),
        "texture":  (2.4, 0.7),
        "smell":    (2.2, 0.6),
        "change":   (2.9, 0.7),
    },
}

DIMS = ["noise", "light", "crowd", "texture", "smell", "change"]


def sample_sensitivity(user_type):
    """Draw one sensitivity vector (1–5 per dimension) for a given user type."""
    t = USER_TYPES[user_type]
    return {
        d: float(np.clip(np.random.normal(t[d][0], t[d][1]), 1.0, 5.0))
        for d in DIMS
    }


# =============================================================================
# SECTION 2 — TIME-OF-DAY RISK MULTIPLIER
# From Kern et al. (2006) + WESAD cortisol/arousal curves.
# Peaks: morning transition (7–9 am) and afternoon fatigue (2–5 pm).
# =============================================================================

def time_risk_multiplier(hour):
    """Return a float in [0.7, 1.6] reflecting hour-of-day baseline risk."""
    if   7 <= hour <= 9:   return 1.5   # morning prep / commute / school start
    elif hour == 10:       return 1.1
    elif hour == 11:       return 1.1
    elif hour == 12:       return 1.3   # lunch social noise
    elif 13 <= hour <= 15: return 1.4   # early afternoon fatigue
    elif 16 <= hour <= 17: return 1.5   # WESAD stress peak window
    elif 18 <= hour <= 20: return 1.0   # evening wind-down
    elif 21 <= hour <= 23: return 0.85
    else:                  return 0.70  # 0–6 am, very quiet


def _hour_weights():
    """Realistic sampling weight per hour (people log during waking hours)."""
    w = np.ones(24) * 0.3
    w[7:9]   = 2.5
    w[9:12]  = 2.0
    w[12:14] = 2.2
    w[14:18] = 2.5
    w[18:21] = 1.8
    w[21:24] = 1.0
    return w / w.sum()


# =============================================================================
# SECTION 3 — SYNTHETIC SAMPLE GENERATOR
# =============================================================================

def generate_sample(sensitivity, hour, is_weekend):
    """
    Simulate one snapshot in time and return (feature_dict, ground_truth_risk).

    Risk formula encodes:
      1. Sensitivity-weighted trigger exposure     [Marco et al. 2011]
      2. Event-frequency stress buildup (tanh)     [WESAD arousal curve]
      3. Intensity amplifier
      4. Multi-trigger synergy (non-linear)        [Green et al. 2013]
      5. Time-of-day multiplier                    [Kern et al. 2006]
      6. Weekend / weekday adjustment
    """
    # --- Simulate trigger event counts in last 3 hours ---
    trigger_events = {}
    active_triggers = 0
    for d in DIMS:
        prob = 0.05 + (sensitivity[d] - 1) * 0.12   # 0.05 → 0.53
        count = int(np.random.poisson(prob * 3))
        trigger_events[d] = min(count, 8)
        if count > 0:
            active_triggers += 1

    total_3h = sum(trigger_events.values())
    total_1h = int(total_3h * np.random.uniform(0.15, 0.65))

    avg_sens = float(np.mean([sensitivity[d] for d in DIMS]))
    if total_3h > 0:
        avg_intensity = float(np.clip(np.random.normal(avg_sens * 0.8, 0.5), 1.0, 5.0))
    else:
        avg_intensity = float(np.random.uniform(1.0, 2.0))

    # --- Risk formula ---

    # 1. Exposure score: sensitivity × frequency per dimension
    exposure = 0.0
    for d in DIMS:
        s_norm = (sensitivity[d] - 1) / 4.0
        f_norm = min(trigger_events[d], 5) / 5.0
        exposure += s_norm * f_norm
    exposure /= len(DIMS)

    # 2. Frequency stress buildup (WESAD-shaped tanh curve)
    freq_factor = float(np.tanh(total_3h / 4.0))

    # 3. Intensity amplifier
    intensity_factor = (avg_intensity - 1) / 4.0

    # 4. Multi-trigger synergy (non-linear spike for co-occurring triggers)
    if   active_triggers >= 3: synergy = 1.0 + 0.4 * (active_triggers - 2) / 4.0
    elif active_triggers == 2: synergy = 1.2
    else:                      synergy = 1.0

    # 5 & 6. Environmental context
    time_mult    = time_risk_multiplier(hour)
    weekend_fact = 0.85 if is_weekend else 1.0

    raw = (0.45 * exposure + 0.30 * freq_factor + 0.25 * intensity_factor)
    raw *= synergy * time_mult * weekend_fact
    raw += float(np.random.normal(0, 0.04))   # unmeasured noise
    risk = float(np.clip(raw, 0.0, 1.0))

    # --- Build normalised feature vector (must match Android SensoryRiskPredictor) ---
    features = {
        "time_of_day_norm":   hour / 23.0,
        "is_weekend":         float(is_weekend),
        "noise_exposure":     (sensitivity["noise"]   - 1) / 4.0 * min(trigger_events["noise"],   5) / 5.0,
        "light_exposure":     (sensitivity["light"]   - 1) / 4.0 * min(trigger_events["light"],   5) / 5.0,
        "crowd_exposure":     (sensitivity["crowd"]   - 1) / 4.0 * min(trigger_events["crowd"],   5) / 5.0,
        "texture_exposure":   (sensitivity["texture"] - 1) / 4.0 * min(trigger_events["texture"], 5) / 5.0,
        "smell_exposure":     (sensitivity["smell"]   - 1) / 4.0 * min(trigger_events["smell"],   5) / 5.0,
        "change_exposure":    (sensitivity["change"]  - 1) / 4.0 * min(trigger_events["change"],  5) / 5.0,
        "event_freq_1h":      min(total_1h,  10) / 10.0,
        "event_freq_3h":      min(total_3h,  20) / 20.0,
        "avg_intensity":      (avg_intensity - 1) / 4.0,
        "overall_sensitivity":(avg_sens      - 1) / 4.0,
    }
    return features, risk


# =============================================================================
# SECTION 4 — DATASET GENERATION
# =============================================================================

FEATURE_COLS = [
    "time_of_day_norm", "is_weekend",
    "noise_exposure", "light_exposure", "crowd_exposure",
    "texture_exposure", "smell_exposure", "change_exposure",
    "event_freq_1h", "event_freq_3h",
    "avg_intensity", "overall_sensitivity",
]


def generate_dataset(n=60_000):
    print(f"\nGenerating {n:,} synthetic training samples …")
    type_names   = list(USER_TYPES.keys())
    type_weights = [USER_TYPES[t]["weight"] for t in type_names]
    hour_weights = _hour_weights()
    rows = []

    for i in range(n):
        utype = np.random.choice(type_names, p=type_weights)
        sens  = sample_sensitivity(utype)
        hour  = int(np.random.choice(24, p=hour_weights))
        wknd  = np.random.random() < 0.3

        feats, risk = generate_sample(sens, hour, wknd)
        row = {**feats, "overload_risk": risk}
        rows.append(row)

        if (i + 1) % 15_000 == 0:
            print(f"  {i+1:,} / {n:,}")

    df = pd.DataFrame(rows)
    print(f"Done.  Shape: {df.shape}")
    print(f"Risk   mean={df['overload_risk'].mean():.3f}  "
          f"std={df['overload_risk'].std():.3f}  "
          f"high(>0.6)={( df['overload_risk'] > 0.6).mean()*100:.1f}%")
    return df


# =============================================================================
# SECTION 5 — MODEL
# =============================================================================

def build_model(n_features):
    """
    Compact neural net (12 → 32 → 16 → 8 → 1).
    Designed to convert cleanly to TFLite with dynamic-range quantization.
    """
    inp = tf.keras.Input(shape=(n_features,), name="sensory_features")
    x = tf.keras.layers.Dense(32, activation="relu")(inp)
    x = tf.keras.layers.Dropout(0.2)(x)
    x = tf.keras.layers.Dense(16, activation="relu")(x)
    x = tf.keras.layers.Dropout(0.1)(x)
    x = tf.keras.layers.Dense(8,  activation="relu")(x)
    out = tf.keras.layers.Dense(1, activation="sigmoid", name="risk_score")(x)

    model = tf.keras.Model(inp, out)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="mse",
        metrics=["mae"],
    )
    return model


def train(df):
    X = df[FEATURE_COLS].values.astype(np.float32)
    y = df["overload_risk"].values.astype(np.float32)

    X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.15, random_state=42)
    print(f"\nTrain: {len(X_tr):,}  |  Test: {len(X_te):,}")

    model = build_model(len(FEATURE_COLS))
    model.summary()

    model.fit(
        X_tr, y_tr,
        validation_split=0.15,
        epochs=50,
        batch_size=256,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(patience=6, restore_best_weights=True),
            tf.keras.callbacks.ReduceLROnPlateau(factor=0.5, patience=3, verbose=1),
        ],
        verbose=1,
    )

    loss, mae = model.evaluate(X_te, y_te, verbose=0)
    y_pred = model.predict(X_te, verbose=0).flatten()
    acc = accuracy_score((y_te > 0.5).astype(int), (y_pred > 0.5).astype(int))
    print(f"\nTest  MAE={mae:.4f}   Binary-accuracy(thresh=0.5)={acc:.3f}")
    return model


# =============================================================================
# SECTION 6 — EXPORT
# =============================================================================

def export_tflite(model, path="sensory_overload_model.tflite"):
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]   # dynamic-range quantization
    tflite_bytes = conv.convert()
    with open(path, "wb") as f:
        f.write(tflite_bytes)
    kb = os.path.getsize(path) / 1024
    print(f"\nTFLite saved → {path}  ({kb:.1f} KB)")


def export_metadata(path="model_metadata.json"):
    meta = {
        "version": "1.0",
        "description": "Sensory overload risk prediction for SenseShield+",
        "feature_names": FEATURE_COLS,
        "input_size": len(FEATURE_COLS),
        "output": "overload_risk_0_to_1",
        "risk_thresholds": {
            "low":      [0.00, 0.35],
            "moderate": [0.35, 0.60],
            "high":     [0.60, 0.80],
            "critical": [0.80, 1.00],
        },
        "risk_labels":  ["Low", "Moderate", "High", "Critical"],
        "risk_colors":  ["#4CAF50", "#FF9800", "#F44336", "#9C27B0"],
        "risk_advice": [
            "You're doing well. Keep up your routine.",
            "Things might feel a bit much soon. Consider a short break.",
            "Your senses may be getting overloaded. Try a calming tool.",
            "High overload risk. Tap Emergency Calm now.",
        ],
        "research_sources": [
            "Marco et al. (2011) – Sensory processing in autism",
            "Baranek et al. (2006) – Sensory processing subtypes in ASD",
            "Green et al. (2013) – Sensory over-responsivity in ASD",
            "Schmidt et al. (2018) – WESAD stress & affect dataset",
            "Kern et al. (2006) – Sensory processing in everyday life of children with ASD",
        ],
    }
    with open(path, "w") as f:
        json.dump(meta, f, indent=2)
    print(f"Metadata saved → {path}")


# =============================================================================
# MAIN
# =============================================================================

if __name__ == "__main__":
    print("=" * 62)
    print("  SenseShield+ — Sensory Overload Risk Model — Training")
    print("=" * 62)

    df    = generate_dataset(60_000)
    model = train(df)
    export_tflite(model)
    export_metadata()

    print("\n" + "=" * 62)
    print("  ALL DONE")
    print("  Copy both output files into your Android project:")
    print("    sensory_overload_model.tflite  →  app/src/main/assets/")
    print("    model_metadata.json            →  app/src/main/assets/")
    print("=" * 62)
