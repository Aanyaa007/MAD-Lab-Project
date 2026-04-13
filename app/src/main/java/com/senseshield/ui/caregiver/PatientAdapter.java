package com.senseshield.ui.caregiver;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.senseshield.R;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.User;

import java.util.Calendar;
import java.util.List;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientVH> {

    public interface OnPatientClickListener {
        void onPatientClick(User patient);
    }

    private final Context               context;
    private final List<User>            patients;
    private final DatabaseHelper        db;
    private final OnPatientClickListener listener;

    public PatientAdapter(Context context, List<User> patients,
                          DatabaseHelper db, OnPatientClickListener listener) {
        this.context  = context;
        this.patients = patients;
        this.db       = db;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PatientVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_patient_card, parent, false);
        return new PatientVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientVH h, int position) {
        User patient = patients.get(position);

        // Avatar
        h.tvAvatar.setText(patient.getInitials());
        try {
            h.tvAvatar.getBackground().setTint(Color.parseColor(patient.getAvatarColor()));
        } catch (Exception e) {
            h.tvAvatar.getBackground().setTint(Color.parseColor("#7F77DD"));
        }

        // Name
        h.tvName.setText(patient.getName());

        // Events today
        int todayEvents = db.getEventCountForDays(patient.getId(), 1);
        h.tvEventsToday.setText(String.valueOf(todayEvents));
        String badgeHex = todayEvents == 0 ? "#AAAAAA" : todayEvents <= 2 ? "#1D9E75" : "#D85A30";
        try { h.tvEventsToday.getBackground().setTint(Color.parseColor(badgeHex)); }
        catch (Exception ignored) {}

        // Last event
        List<com.senseshield.models.SensoryEvent> recent =
                db.getRecentEvents(patient.getId(), 30);
        if (recent.isEmpty()) {
            h.tvLastEvent.setText("No events logged yet");
        } else {
            long ts = recent.get(0).getTimestamp();
            h.tvLastEvent.setText("Last: " + relativeTime(ts));
        }

        h.itemView.setOnClickListener(v -> listener.onPatientClick(patient));
    }

    @Override
    public int getItemCount() { return patients.size(); }

    private String relativeTime(long ts) {
        long diff = System.currentTimeMillis() - ts;
        long mins  = diff / 60000;
        long hours = mins  / 60;
        long days  = hours / 24;
        if (mins < 2)    return "just now";
        if (mins < 60)   return mins + " min ago";
        if (hours < 24)  return hours + " hr ago";
        if (days == 1)   return "yesterday";
        return days + " days ago";
    }

    static class PatientVH extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvName, tvLastEvent, tvEventsToday;
        PatientVH(@NonNull View v) {
            super(v);
            tvAvatar     = v.findViewById(R.id.tv_patient_avatar);
            tvName       = v.findViewById(R.id.tv_patient_name);
            tvLastEvent  = v.findViewById(R.id.tv_patient_last_event);
            tvEventsToday = v.findViewById(R.id.tv_events_today);
        }
    }
}
