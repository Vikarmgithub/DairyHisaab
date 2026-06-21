package com.example.dairyhisaab;

import android.content.Context;
import android.util.Log;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocalBackupHelper {

    private static final String TAG = "LocalBackupHelper";
    private static final String BACKUP_FOLDER = "DairyHisaab";
    private static final String BACKUP_FILE   = "DairyHisaab_AutoBackup.enc";

    /**
     * 🔒 FIX: Pehle yeh backup public Downloads/DairyHisaab/ mein PLAINTEXT JSON ke
     * roop mein save hota tha — koi bhi app (storage permission ke saath) ya PC se
     * connect karke koi bhi yeh file padh sakta tha (customer data, payments, sab).
     *
     * Ab:
     *   1) File app-PRIVATE external storage mein hai
     *      (/Android/data/com.example.dairyhisaab/files/DairyHisaab/) — sirf yeh app
     *      bina kisi permission ke yahan likh/padh sakti hai, doosri app access nahi
     *      kar sakti.
     *   2) Content AES-256-GCM se ENCRYPTED hai. Encryption key Android Keystore
     *      (device hardware) mein generate + lock hoti hai — key kabhi file ke roop
     *      mein bahar nahi aati. Sirf isi app ko, isi device pe, decrypt karne diya
     *      jaata hai. File ko copy/share karne se bhi kisi aur device/app pe decrypt
     *      nahi ho sakti — sirf gibberish dikhega.
     *
     * NOTE: Yeh backup device-locked hai (naye phone pe restore nahi hoga). Real
     * cross-device recovery FirebaseManager ke cloud backup se hoti hai — woh isse
     * alag hai aur affected nahi hua.
     */
    public static boolean saveLocalBackup(Context context) {
        try {
            String json = buildBackupJson(context);

            File dir = new File(context.getExternalFilesDir(null), BACKUP_FOLDER);
            if (!dir.exists()) dir.mkdirs();

            File backupFile = new File(dir, BACKUP_FILE);
            // EncryptedFile fails to write if the target file already exists.
            if (backupFile.exists()) backupFile.delete();

            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            EncryptedFile encryptedFile = new EncryptedFile.Builder(
                    context,
                    backupFile,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            try (OutputStream out = encryptedFile.openFileOutput()) {
                out.write(json.getBytes("UTF-8"));
            }

            Log.d(TAG, "Encrypted local backup saved: " + backupFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Encrypted local backup failed: " + e.getMessage());

            // Fallback: app's internal private storage (still private, not public).
            // Not encrypted here since this only triggers on Keystore/IO failure,
            // but it is NOT exposed to other apps either way.
            try {
                File dir = new File(context.getFilesDir(), BACKUP_FOLDER);
                if (!dir.exists()) dir.mkdirs();
                File backupFile = new File(dir, "DairyHisaab_AutoBackup.json");
                String json = buildBackupJson(context);
                try (FileOutputStream fos = new FileOutputStream(backupFile)) {
                    fos.write(json.getBytes("UTF-8"));
                }
                Log.d(TAG, "Fallback local backup saved (private storage): " + backupFile.getAbsolutePath());
                return true;
            } catch (IOException e2) {
                Log.e(TAG, "Fallback also failed: " + e2.getMessage());
                return false;
            }
        }
    }

    private static String buildBackupJson(Context context) {
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
        return root.toString();
    }

    /** Backup file ka path return karo (user ko dikhane ke liye) */
    public static String getBackupPath(Context context) {
        return new File(context.getExternalFilesDir(null), BACKUP_FOLDER + File.separator + BACKUP_FILE)
                .getAbsolutePath();
    }
}
