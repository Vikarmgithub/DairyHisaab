package com.example.dairyhisaab;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FirebaseManager {

    private static final String TAG = "FirebaseManager";
    private static final String COLLECTION_USERS = "users";
    private static final int MAX_BACKUP_DAYS = 10;

    private static FirebaseManager instance;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Gson gson = new Gson();

    private FirebaseManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) instance = new FirebaseManager();
        return instance;
    }

    public FirebaseUser getCurrentUser() { return auth.getCurrentUser(); }
    public boolean isLoggedIn() { return auth.getCurrentUser() != null; }
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void saveLoginInfo(Context context) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String deviceModel = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        Map<String, Object> info = new HashMap<>();
        info.put("email", user.getEmail());
        info.put("name", user.getDisplayName() != null ? user.getDisplayName() : "");
        info.put("lastLogin", timestamp);
        info.put("deviceInfo", deviceModel);
        info.put("uid", user.getUid());
        db.collection(COLLECTION_USERS).document(user.getUid())
                .set(info, SetOptions.merge())
                .addOnSuccessListener(a -> Log.d(TAG, "Login info saved"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed: " + e.getMessage()));
    }

    public interface UploadCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface DownloadCallback {
        void onSuccess(Map<String, Object> data);
        void onFailure(String error);
    }

    public interface AvailableDatesCallback {
        void onSuccess(List<String> dates);
        void onFailure(String error);
    }

    // ── Rolling 10-day backup ──
    // Structure: users/{uid}/backups/{date}/  (customers, entries, payments, rateHistory)
    public void uploadAllData(DairyDataManager dm, UploadCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("Login nahi hai!"); return; }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

        Map<String, Object> data = new HashMap<>();
        data.put("lastBackup", timestamp);
        data.put("customers", gson.toJson(dm.getCustomers()));
        data.put("entries", gson.toJson(dm.getEntries()));
        data.put("payments", gson.toJson(dm.getPayments()));
        data.put("rateHistory", gson.toJson(dm.getRateHistory()));

        // Aaj ki date wale subfolder mein save karo
        db.collection(COLLECTION_USERS).document(uid)
                .collection("backups").document(today)
                .set(data)
                .addOnSuccessListener(a -> {
                    Log.d(TAG, "Backup saved for " + today);
                    // Update lastBackup metadata on main user doc
                    db.collection(COLLECTION_USERS).document(uid)
                            .set(Collections.singletonMap("lastBackup", timestamp), SetOptions.merge());
                    // 10 din se purani backups delete karo
                    deleteOldBackups(uid);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // 10 din se zyada purani backups delete karo
    private void deleteOldBackups(String uid) {
        db.collection(COLLECTION_USERS).document(uid)
                .collection("backups")
                .get(Source.SERVER)
                .addOnSuccessListener(snapshots -> {
                    List<String> dates = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        dates.add(doc.getId());
                    }
                    Collections.sort(dates); // oldest first
                    // Agar 10 se zyada hain to purane delete karo
                    int toDelete = dates.size() - MAX_BACKUP_DAYS;
                    for (int i = 0; i < toDelete; i++) {
                        String dateToDelete = dates.get(i);
                        db.collection(COLLECTION_USERS).document(uid)
                                .collection("backups").document(dateToDelete)
                                .delete()
                                .addOnSuccessListener(a -> Log.d(TAG, "Old backup deleted: " + dateToDelete))
                                .addOnFailureListener(e -> Log.e(TAG, "Delete failed: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to list backups: " + e.getMessage()));
    }

    // Available backup dates ki list (restore dialog ke liye)
    public void getAvailableBackupDates(AvailableDatesCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("Login nahi hai!"); return; }

        db.collection(COLLECTION_USERS).document(uid)
                .collection("backups")
                .get(Source.SERVER)
                .addOnSuccessListener(snapshots -> {
                    List<String> dates = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        dates.add(doc.getId());
                    }
                    Collections.sort(dates, Collections.reverseOrder()); // newest first
                    if (dates.isEmpty()) {
                        callback.onFailure("Koi backup nahi mila! Pehle backup karo.");
                    } else {
                        callback.onSuccess(dates);
                    }
                })
                .addOnFailureListener(e -> callback.onFailure("Dates fetch failed: " + e.getMessage()));
    }

    // Specific date ka backup download karo
    public void downloadDataForDate(String date, DownloadCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("Login nahi hai!"); return; }

        db.collection(COLLECTION_USERS).document(uid)
                .collection("backups").document(date)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) callback.onSuccess(doc.getData());
                    else callback.onFailure(date + " ka koi backup nahi mila!");
                })
                .addOnFailureListener(e -> callback.onFailure("Download failed: " + e.getMessage()));
    }

    // Legacy: latest backup download (purana code compatible)
    public void downloadAllData(DownloadCallback callback) {
        getAvailableBackupDates(new AvailableDatesCallback() {
            @Override
            public void onSuccess(List<String> dates) {
                // Sabse latest date ka backup lo
                downloadDataForDate(dates.get(0), callback);
            }
            @Override
            public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }

    public void restoreData(DairyDataManager dm, Map<String, Object> data) {
        if (data.containsKey("customers")) {
            List<Customer> list = gson.fromJson((String) data.get("customers"), new TypeToken<List<Customer>>(){}.getType());
            dm.saveCustomers(list != null ? list : new ArrayList<>());
        }
        if (data.containsKey("entries")) {
            List<MilkEntry> list = gson.fromJson((String) data.get("entries"), new TypeToken<List<MilkEntry>>(){}.getType());
            dm.saveEntries(list != null ? list : new ArrayList<>());
        }
        if (data.containsKey("payments")) {
            List<Payment> list = gson.fromJson((String) data.get("payments"), new TypeToken<List<Payment>>(){}.getType());
            dm.savePayments(list != null ? list : new ArrayList<>());
        }
        if (data.containsKey("rateHistory")) {
            List<RateEntry> list = gson.fromJson((String) data.get("rateHistory"), new TypeToken<List<RateEntry>>(){}.getType());
            dm.saveRateHistory(list != null ? list : new ArrayList<>());
        }
    }

    public void signOut() { auth.signOut(); }

    public String getUserEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : "";
    }

    public String getUserName() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.getDisplayName() != null ? user.getDisplayName() : "";
    }
}
