package com.example.dairyhisaab;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocalBackupHelper {

    private static final String TAG = "LocalBackupHelper";
    private static final String BACKUP_FOLDER = "DairyHisaab";
    private static final String BACKUP_FILE   = "DairyHisaab_AutoBackup.json";

    /**
     * App ki internal storage mein backup save karo.
     * Path: /sdcard/DairyHisaab/DairyHisaab_AutoBackup.json
     * Yeh file File Manager se access ho sakti hai.
     */
    public static boolean saveLocalBackup(Context context) {
        try {
            DairyDataManager dm = DairyDataManager.getInstance(context);
            Gson gson = new Gson();

            JsonObject root = new JsonObject();
            root.addProperty("app", "DairyHisaab");
            root.addProperty("version", 1);
            root.addProperty("backupTime",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            root.add("customers",   gson.toJsonTree(dm.getCustomers()));
            root.add("entries",     gson.toJsonTree(dm.getEntries()));
            root.add("payments",    gson.toJsonTree(dm.getPayments()));
            root.add("rateHistory", gson.toJsonTree(dm.getRateHistory()));

            String json = root.toString();

            // Primary: External public Downloads/DairyHisaab/
            File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                BACKUP_FOLDER
            );
            if (!dir.exists()) dir.mkdirs();

            File backupFile = new File(dir, BACKUP_FILE);
            FileWriter fw = new FileWriter(backupFile);
            fw.write(json);
            fw.close();

            Log.d(TAG, "Local backup saved: " + backupFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Local backup failed: " + e.getMessage());

            // Fallback: App private storage
            try {
                File dir = new File(context.getFilesDir(), BACKUP_FOLDER);
                if (!dir.exists()) dir.mkdirs();
                File backupFile = new File(dir, BACKUP_FILE);
                DairyDataManager dm = DairyDataManager.getInstance(context);
                Gson gson = new Gson();
                JsonObject root = new JsonObject();
                root.addProperty("app", "DairyHisaab");
                root.addProperty("version", 1);
                root.addProperty("backupTime",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                root.add("customers",   gson.toJsonTree(dm.getCustomers()));
                root.add("entries",     gson.toJsonTree(dm.getEntries()));
                root.add("payments",    gson.toJsonTree(dm.getPayments()));
                root.add("rateHistory", gson.toJsonTree(dm.getRateHistory()));
                FileWriter fw = new FileWriter(backupFile);
                fw.write(root.toString());
                fw.close();
                Log.d(TAG, "Fallback local backup saved: " + backupFile.getAbsolutePath());
                return true;
            } catch (IOException e2) {
                Log.e(TAG, "Fallback also failed: " + e2.getMessage());
                return false;
            }
        }
    }

    /** Backup file ka path return karo (user ko dikhane ke liye) */
    public static String getBackupPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            + "/" + BACKUP_FOLDER + "/" + BACKUP_FILE;
    }
}
