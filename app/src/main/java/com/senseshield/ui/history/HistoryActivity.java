package com.senseshield.ui.history;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.senseshield.R;
import com.senseshield.SenseShieldApp;
import com.senseshield.database.DatabaseHelper;
import com.senseshield.models.SensoryEvent;
import com.senseshield.utils.AppConstants;
import com.senseshield.utils.UiClickUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HistoryActivity.java
 * Shows all logged sensory events, most recent first.
 * Empty state shown when no events exist yet.
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView   rvHistory;
    private LinearLayout   llEmptyState;
    private TextView       tvEventCount;
    private TextView       btnExport;
    private DatabaseHelper db;
    private int userId = -1;
    private List<SensoryEvent> currentEvents;

    private int appliedThemeResId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedThemeResId = SenseShieldApp.getThemeResId(this);
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = DatabaseHelper.getInstance(this);
        userId = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE)
                     .getInt(AppConstants.PREF_CURRENT_USER_ID, -1);

        rvHistory    = findViewById(R.id.rv_history);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvEventCount = findViewById(R.id.tv_event_count);
        btnExport    = findViewById(R.id.btn_export);

        TextView btnBack = findViewById(R.id.btn_back);
        UiClickUtils.setSafeClickListener(btnBack, v -> finish());
        UiClickUtils.setSafeClickListener(btnExport, v -> exportCsv());

        loadEvents();
    }

    private void loadEvents() {
        if (userId == -1) { showEmpty(); return; }

        List<SensoryEvent> events = db.getEventsByUser(userId);

        currentEvents = events;

        if (events.isEmpty()) {
            showEmpty();
            if (btnExport != null) btnExport.setVisibility(View.GONE);
        } else {
            if (llEmptyState != null) llEmptyState.setVisibility(View.GONE);
            if (rvHistory != null)    rvHistory.setVisibility(View.VISIBLE);
            if (btnExport != null)    btnExport.setVisibility(View.VISIBLE);

            if (tvEventCount != null) {
                tvEventCount.setText(events.size() + " moment" +
                    (events.size() == 1 ? "" : "s"));
            }

            rvHistory.setLayoutManager(new LinearLayoutManager(this));
            rvHistory.setAdapter(new HistoryAdapter(events));
        }
    }

    // ── CSV Export ────────────────────────────────────────────────────────────

    private void exportCsv() {
        if (currentEvents == null || currentEvents.isEmpty()) {
            Toast.makeText(this, "No events to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Time,Trigger,Severity,Location,Calm Tool,Effectiveness,Notes\n");

        for (SensoryEvent e : currentEvents) {
            Date d = new Date(e.getTimestamp());
            csv.append(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)).append(",");
            csv.append(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)).append(",");
            csv.append(csvEscape(e.getTriggerType())).append(",");
            csv.append(e.getSeverity()).append(",");
            csv.append(csvEscape(e.getLocationTag())).append(",");
            csv.append(csvEscape(e.getCalmToolUsed())).append(",");
            csv.append(e.getEffectiveness()).append(",");
            csv.append(csvEscape(e.getNotes())).append("\n");
        }

        try {
            File cacheDir = new File(getCacheDir(), "exports");
            cacheDir.mkdirs();
            String fileName = "senseshield_history_"
                    + new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                            .format(new Date()) + ".csv";
            File file = new File(cacheDir, fileName);
            FileWriter writer = new FileWriter(file);
            writer.write(csv.toString());
            writer.flush();
            writer.close();

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SenseShield History Export");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export history via…"));

        } catch (IOException ex) {
            Toast.makeText(this, "Export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void showEmpty() {
        if (llEmptyState != null) llEmptyState.setVisibility(View.VISIBLE);
        if (rvHistory != null)    rvHistory.setVisibility(View.GONE);
        if (tvEventCount != null) tvEventCount.setText("0 moments");
    }
}
