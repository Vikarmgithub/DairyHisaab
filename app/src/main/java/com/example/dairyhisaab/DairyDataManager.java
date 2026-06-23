package com.example.dairyhisaab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DairyDataManager {

    private static final String TAG = "DairyDataManager";
    private static final String BACKUP_WORK_NAME = "DairyAutoBackup";

    private static DairyDataManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private static final String KEY_CUSTOMERS    = "dairy_customers";
    private static final String KEY_DAIRY_NAME   = "dairy_name"; // Settings se editable
    private static final String KEY_ENTRIES      = "dairy_entries";
    private static final String KEY_PAYMENTS     = "dairy_payments";
    private static final String KEY_RATE_HISTORY = "dairy_rateHistory";
    private static final String KEY_PIN          = "dairy_delete_pin";
    private static final String KEY_PIN_SALT     = "dairy_pin_salt"; // 🔒 har install ka apna random salt

    // Agar 2 second ke andar kai baar save hua to sirf 1 baar backup hoga
    private final Handler backupHandler = new Handler(Looper.getMainLooper());
    private Runnable backupRunnable;

    // Restore ke waqt backup loop rokne ke liye
    private boolean isRestoring = false;

    private DairyDataManager(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = getOrCreateEncryptedPrefs(appContext);
        migrateFromPlainPrefsIfNeeded(appContext);
        // Raat 9 baje ka daily auto-backup schedule karo
        scheduleDailyBackup(appContext);
    }

    // 🔒 FIX: Pehle dairy data plain SharedPreferences mein save hota tha (rooted
    // phone ya file-extraction se padha ja sakta tha). Ab AES-256 encrypted prefs
    // use karte hain — key Android Keystore (hardware) mein lock hoti hai, file
    // copy karne se bhi kisi aur device pe decrypt nahi ho sakti.
    private SharedPreferences getOrCreateEncryptedPrefs(Context context) {
        try {
            androidx.security.crypto.MasterKey masterKey =
                new androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "dairy_hisaab_secure",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Keystore fail hone pe bhi app chalti rahe — purane plain prefs pe fallback
            Log.e(TAG, "Encrypted prefs init failed, falling back to plain prefs: " + e.getMessage());
            return context.getSharedPreferences("dairy_hisaab", Context.MODE_PRIVATE);
        }
    }

    // Purane plain "dairy_hisaab" prefs se naye encrypted prefs mein ek baar data
    // copy karo (sirf agar naya khaali hai aur purana data maujood hai), phir purana
    // plaintext data clear kar do taaki disk pe na reh jaaye.
    private void migrateFromPlainPrefsIfNeeded(Context context) {
        if (prefs.contains(KEY_CUSTOMERS)) return; // already migrated / already has data

        SharedPreferences oldPrefs = context.getSharedPreferences("dairy_hisaab", Context.MODE_PRIVATE);
        Map<String, ?> oldData = oldPrefs.getAll();
        if (oldData.isEmpty()) return; // naya install — kuch migrate karne ko nahi

        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : oldData.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof String)  editor.putString(entry.getKey(), (String) v);
            else if (v instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) v);
            else if (v instanceof Long)    editor.putLong(entry.getKey(), (Long) v);
            else if (v instanceof Integer) editor.putInt(entry.getKey(), (Integer) v);
            else if (v instanceof Float)   editor.putFloat(entry.getKey(), (Float) v);
        }
        editor.apply();

        oldPrefs.edit().clear().apply(); // purana plaintext data hata do
        Log.d(TAG, "Migrated plain prefs -> encrypted prefs");
    }

    public static synchronized DairyDataManager getInstance(Context context) {
        if (instance == null) instance = new DairyDataManager(context);
        return instance;
    }

    // ── Raat 9 baje daily WorkManager backup ──
    private void scheduleDailyBackup(Context context) {
        // Aaj raat 9 baje tak kitna waqt bacha hai?
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 21);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);

        // Agar 9 baj chuke hain to kal raat 9 baje schedule karo
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = target.getTimeInMillis() - now.getTimeInMillis();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest backupRequest = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        // KEEP: agar pehle se schedule hai to change mat karo
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                backupRequest
        );

        Log.d(TAG, "Daily backup scheduled at 9 PM");
    }

    // ── PIN ──
    public boolean hasPin() { return prefs.contains(KEY_PIN); }
    // 🔒 FIX: PIN plaintext mein save hota tha — ab salted SHA-256 hash save karte hain
    public void savePin(String pin) { prefs.edit().putString(KEY_PIN, hashPin(pin, getOrCreateSalt())).apply(); }

    public boolean verifyPin(String pin) {
        if (pin == null) return false;
        String stored = prefs.getString(KEY_PIN, "");

        // Naye (per-install random) salt se check
        if (hashPin(pin, getOrCreateSalt()).equals(stored)) return true;

        // 🔄 MIGRATION: agar user ka PIN purane static-salt se hash hua tha
        // (app update se pehle), use purane salt se bhi verify karo. Match
        // hote hi naye random salt pe migrate karke re-save kar do — taaki
        // aage se hamesha naya salt use ho.
        if (!prefs.contains(KEY_PIN_SALT)) {
            String legacyHash = hashPin(pin, "dairyhisaab_pin_salt_v1");
            if (legacyHash.equals(stored)) {
                savePin(pin); // re-hash + save with new random salt
                return true;
            }
        }
        return false;
    }

    // 🔒 FIX: Pehle salt hardcoded/static tha ("dairyhisaab_pin_salt_v1") — sab installs
    // mein same hone se ek precomputed table sab users ke liye kaam karti thi. Ab har
    // install apna khud ka random 16-byte salt generate karta hai (pehli baar PIN set
    // hote waqt), SharedPreferences mein save hota hai, aur hashing mein use hota hai.
    private String getOrCreateSalt() {
        String existing = prefs.getString(KEY_PIN_SALT, null);
        if (existing != null) return existing;

        byte[] saltBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(saltBytes);
        String salt = android.util.Base64.encodeToString(saltBytes, android.util.Base64.NO_WRAP);
        prefs.edit().putString(KEY_PIN_SALT, salt).apply();
        return salt;
    }

    private String hashPin(String pin, String salt) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes("UTF-8"));
            byte[] hash = md.digest(pin.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin; // fallback, ideally unreachable
        }
    }

    // ── Restore flag ──
    public void setRestoring(boolean restoring) {
        this.isRestoring = restoring;
    }

    // App background mein jaane se pehle (onPause/onStop) is se turant backup
    // force kar sakte ho — debounce ka wait nahi karna padega, data miss nahi hoga
    public void flushPendingBackupNow() {
        if (backupRunnable == null) return; // koi pending backup nahi hai
        backupHandler.removeCallbacks(backupRunnable);
        backupRunnable.run();
        backupRunnable = null;
    }

    // ── Auto cloud backup (debounced) ──
    // 🔧 SCALE FIX: pehle 2 second debounce thi — matlab agar tum lagataar
    // entries add karte ho (jaise 20 entries ek session mein), to har 2-sec
    // gap pe POORA dataset firestore mein dobara upload hota tha (cost +
    // Firestore ki 1MB/document limit ki taraf jaldi badhte). Ab 3 minute
    // debounce hai — ek poori data-entry session (jab tak tum ruk-ruk ke
    // kaam karte ho) sirf EK upload mein coalesce ho jayegi. Raat 9 baje
    // wala daily WorkManager backup already safety-net hai, isliye thoda
    // delay safe hai — local data (encrypted) turant save hota hai, sirf
    // cloud-copy thoda der se jaata hai.
    private static final long CLOUD_BACKUP_DEBOUNCE_MS = 3 * 60 * 1000; // 3 minute

    private void triggerCloudBackup() {
        if (isRestoring) return;
        if (!FirebaseManager.getInstance().isLoggedIn()) return;

        if (backupRunnable != null) backupHandler.removeCallbacks(backupRunnable);
        backupRunnable = () ->
            FirebaseManager.getInstance().uploadAllData(this,
                new FirebaseManager.UploadCallback() {
                    public void onSuccess() { Log.d(TAG, "Auto backup done"); }
                    public void onFailure(String e) { Log.e(TAG, "Auto backup failed: " + e); }
                }
            );
        backupHandler.postDelayed(backupRunnable, CLOUD_BACKUP_DEBOUNCE_MS);
    }

    // ── Save / Load helpers ──
    private <T> void save(String key, List<T> list) {
        prefs.edit().putString(key, gson.toJson(list)).apply();
        triggerCloudBackup(); // Har save ke baad backup
    }
    private <T> List<T> load(String key, Type type) {
        String json = prefs.getString(key, null);
        if (json == null) return new ArrayList<>();
        List<T> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    // ── Customers ──
    // 🏷️ Dairy ka naam — Settings se editable, slip print/reports/WhatsApp sab
    // jagah yahi ek jagah se aata hai. Agar customer ne kabhi set nahi kiya,
    // to Firebase signup ke waqt diya naam fallback ke roop mein use hota hai.
    public String getDairyName() {
        String custom = prefs.getString(KEY_DAIRY_NAME, null);
        if (custom != null && !custom.trim().isEmpty()) return custom;

        try {
            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                return user.getDisplayName();
            }
        } catch (Exception ignored) {}

        return "Dairy Hisaab";
    }

    public void setDairyName(String name) {
        prefs.edit().putString(KEY_DAIRY_NAME, name).apply();
    }

    public List<Customer> getCustomers() {
        return load(KEY_CUSTOMERS, new TypeToken<List<Customer>>(){}.getType());
    }
    public void saveCustomers(List<Customer> list) { save(KEY_CUSTOMERS, list); }

    // ── Milk Entries ──
    public List<MilkEntry> getEntries() {
        return load(KEY_ENTRIES, new TypeToken<List<MilkEntry>>(){}.getType());
    }
    public void saveEntries(List<MilkEntry> list) { save(KEY_ENTRIES, list); }

    // ── Payments ──
    public List<Payment> getPayments() {
        return load(KEY_PAYMENTS, new TypeToken<List<Payment>>(){}.getType());
    }
    public void savePayments(List<Payment> list) { save(KEY_PAYMENTS, list); }

    // ── Rate History ──
    public List<RateEntry> getRateHistory() {
        return load(KEY_RATE_HISTORY, new TypeToken<List<RateEntry>>(){}.getType());
    }
    public void saveRateHistory(List<RateEntry> list) { save(KEY_RATE_HISTORY, list); }

    // ── Active rate for given date ──
    public RateEntry getActiveRate(String date) {
        List<RateEntry> history = getRateHistory();
        if (history == null || history.isEmpty()) return null;
        RateEntry best = null;
        for (RateEntry r : history) {
            if (r.date.compareTo(date) <= 0) {
                if (best == null || r.date.compareTo(best.date) > 0) best = r;
            }
        }
        return best;
    }

    // ── Bill helpers ──
    public double totalBill(String customerId) {
        double total = 0;
        for (MilkEntry e : getEntries()) {
            if (e.cid.equals(customerId)) total += e.qty * e.rate;
        }
        return total;
    }
    public double totalPaid(String customerId) {
        double total = 0;
        for (Payment p : getPayments()) {
            if (p.cid.equals(customerId)) total += p.amount;
        }
        return total;
    }
    public double outstanding(String customerId) {
        return totalBill(customerId) - totalPaid(customerId);
    }

    public static double calcPPL(double fat, double rate, double base) {
        return Math.round((fat * rate / 100.0 + base) * 100.0) / 100.0;
    }

    public String nextMemberCode() {
        List<Customer> customers = getCustomers();
        int max = 0;
        for (Customer c : customers) {
            if (c.memberCode != null) {
                try {
                    int n = Integer.parseInt(c.memberCode.replaceAll("\\D", ""));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return String.format(Locale.getDefault(), "M%03d", max + 1);
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static String formatDate(String d) {
        if (d == null) return "—";
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(d);
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date);
        } catch (Exception e) { return d; }
    }
}
