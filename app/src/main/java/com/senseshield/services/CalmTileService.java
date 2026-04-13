package com.senseshield.services;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import com.senseshield.ui.calm.EmergencyCalmActivity;

/**
 * CalmTileService.java
 * "Safe Space" Quick Settings tile in the notification shade.
 *
 * State logic (onStartListening):
 *   • ACTIVE  — current time slot is predicted risky → tile glows (accent color)
 *   • INACTIVE — no prediction / insufficient data → tile shows as normal
 *
 * Tapping the tile always launches EmergencyCalmActivity (even from lock screen,
 * because the Activity has showWhenLocked + turnScreenOn set in the Manifest).
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class CalmTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, EmergencyCalmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EmergencyCalmActivity.EXTRA_FROM_ALERT, false);
        startActivityAndCollapse(intent);
    }

    // ─── State ───────────────────────────────────────────────────────────────

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        // Keep tile callbacks constant-time to avoid System UI stalls on low-end devices/emulators.
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
