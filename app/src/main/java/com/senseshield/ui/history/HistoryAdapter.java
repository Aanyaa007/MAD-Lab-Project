package com.senseshield.ui.history;

import android.content.res.ColorStateList;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.senseshield.R;
import com.senseshield.models.SensoryEvent;
import com.senseshield.utils.AppConstants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HistoryAdapter.java
 * Binds a list of SensoryEvents to history row cards.
 *
 * Each row shows:
 *   • Color dot  — severity color (green → red)
 *   • Emoji icon — trigger type
 *   • Title      — human-readable trigger name
 *   • Subtitle   — time + location
 *   • Right icon — calm tool used (emoji)
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.EventViewHolder> {

    private final List<SensoryEvent> events;

    public HistoryAdapter(List<SensoryEvent> events) {
        this.events = events;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_history_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(events.get(position));
    }

    @Override
    public int getItemCount() { return events.size(); }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class EventViewHolder extends RecyclerView.ViewHolder {

        private final View     severityDot;
        private final TextView tvTriggerIcon;
        private final TextView tvTriggerName;
        private final TextView tvTime;
        private final TextView tvLocation;
        private final TextView tvCalmTool;

        private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault());

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            severityDot   = itemView.findViewById(R.id.view_severity_dot);
            tvTriggerIcon = itemView.findViewById(R.id.tv_trigger_icon);
            tvTriggerName = itemView.findViewById(R.id.tv_trigger_name);
            tvTime        = itemView.findViewById(R.id.tv_time);
            tvLocation    = itemView.findViewById(R.id.tv_location);
            tvCalmTool    = itemView.findViewById(R.id.tv_calm_tool);
        }

        void bind(SensoryEvent event) {
            // Severity color dot
            int colorRes = severityColorRes(event.getSeverity());
            int color = itemView.getContext().getColor(colorRes);
            severityDot.setBackgroundTintList(ColorStateList.valueOf(color));

            // Content description for accessibility
            String severityLabel = AppConstants.SENSITIVITY_LABELS[
                Math.min(event.getSeverity() - 1, AppConstants.SENSITIVITY_LABELS.length - 1)];
            severityDot.setContentDescription("Severity: " + severityLabel);
            Log.d("TRIGGER_RAW", "DB value: " + event.getTriggerType());
            // Trigger icon + name
            tvTriggerIcon.setText(triggerEmoji(event.getTriggerType()));
            tvTriggerName.setText(triggerName(event.getTriggerType()));

            // Time
            tvTime.setText(TIME_FMT.format(new Date(event.getTimestamp())));

            // Location
            tvLocation.setText(locationLabel(event.getLocationTag()));

            // Calm tool used
            tvCalmTool.setText(calmToolEmoji(event.getCalmToolUsed()));
            tvCalmTool.setContentDescription(calmToolName(event.getCalmToolUsed()));

            // Full row content description for TalkBack
            itemView.setContentDescription(
                triggerName(event.getTriggerType()) + ", " +
                severityLabel + ", " +
                TIME_FMT.format(new Date(event.getTimestamp())) + ", " +
                locationLabel(event.getLocationTag())
            );
        }

        // ─── Mapping helpers ─────────────────────────────────────────────────

        private int severityColorRes(int severity) {
            switch (severity) {
                case 1: return R.color.severity_1;
                case 2: return R.color.severity_2;
                case 3: return R.color.severity_3;
                case 4: return R.color.severity_4;
                case 5: return R.color.severity_5;
                default: return R.color.severity_3;
            }
        }

        private String triggerEmoji(String trigger) {
            if (trigger == null) return "❓";

            switch (trigger) {
                case AppConstants.TRIGGER_NOISE:       return "🔊";
                case AppConstants.TRIGGER_HEART_RATE:  return "❤️";
                case AppConstants.TRIGGER_MULTIPLE:    return "⚠️";
                case AppConstants.TRIGGER_LIGHT:       return "💡";
                case AppConstants.TRIGGER_CROWD:       return "👥";
                case AppConstants.TRIGGER_TEXTURE:     return "✋";
                case AppConstants.TRIGGER_SMELL:       return "👃";
                case AppConstants.TRIGGER_CHANGE:      return "🔄";
                default:                               return "❓";
            }
        }

        private String triggerName(String trigger) {
            if (trigger == null) return "Unknown";

            switch (trigger) {

                case "NOISE":
                    return "Loud sounds";

                case "HEART_RATE":
                    return "Elevated heart rate";

                case "MULTIPLE":
                    return "Multiple triggers";

                case "LIGHT":
                    return "Bright lights";

                case "CROWD":
                    return "Crowded place";

                case "TEXTURE":
                    return "Touch / texture";

                case "SMELL":
                    return "Strong smell";

                case "CHANGE":
                    return "Sudden change";

                default:
                    return "Unknown trigger (" + trigger + ")";
            }
        }

        private String locationLabel(String location) {
            if (location == null) return "Unknown";
            switch (location) {
                case AppConstants.LOCATION_HOME:   return "Home";
                case AppConstants.LOCATION_SCHOOL: return "School";
                case AppConstants.LOCATION_PUBLIC: return "Public";
                default:                           return "Other";
            }
        }

        private String calmToolEmoji(String tool) {
            if (tool == null) return "";
            switch (tool) {
                case AppConstants.CALM_BREATHING: return "🌬️";
                case AppConstants.CALM_SOUND:     return "🎵";
                case AppConstants.CALM_VISUAL:    return "👁️";
                case AppConstants.CALM_COUNTDOWN: return "🔢";
                default:                          return "";
            }
        }

        private String calmToolName(String tool) {
            if (tool == null) return "";
            switch (tool) {
                case AppConstants.CALM_BREATHING: return "Used breathing exercise";
                case AppConstants.CALM_SOUND:     return "Used sound therapy";
                case AppConstants.CALM_VISUAL:    return "Used visual grounding";
                case AppConstants.CALM_COUNTDOWN: return "Used countdown";
                default:                          return "";
            }
        }
    }
}
