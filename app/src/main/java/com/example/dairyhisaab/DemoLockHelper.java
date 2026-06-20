package com.example.dairyhisaab;

import android.content.Context;
import android.content.SharedPreferences;

// =====================================================================
// 🔒 DEMO LOCK HELPER — Demo chalte waqt Backup/Restore (manual + auto)
// block karne ke liye common check. Jahan bhi backup/restore trigger
// ho sakta hai (BackupFragment buttons, MainActivity auto-backup,
// AutoBackupWorker, AdminRestoreHelper) — sab yahi check use karte hain.
// =====================================================================
public class DemoLockHelper {

    private static final String PREFS_NAME     = "DairyLicensePrefs";
    private static final String KEY_ACTIVATED  = "IsAppFullyActivated";
    private static final String KEY_DEMO_START = "DemoStartTime";
    private static final long   DEMO_DURATION_MS = 24L * 60 * 60 * 1000; // 24 ghante

    // Abhi demo active hai kya? (Full license activate hone ke baad ye hamesha false rahega)
    public static boolean isDemoActive(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Full license activated -> demo lock ka sawal hi nahi
        if (prefs.getBoolean(KEY_ACTIVATED, false)) return false;

        long demoStart = prefs.getLong(KEY_DEMO_START, 0);
        if (demoStart <= 0) return false;

        long elapsed = System.currentTimeMillis() - demoStart;
        return elapsed < DEMO_DURATION_MS;
    }
}
