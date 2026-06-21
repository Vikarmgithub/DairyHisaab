package com.example.dairyhisaab;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

    // ── Demo lock + Device-ID linking (ek account = ek device, demo ek hi baar) ──
    private static final String KEY_DEMO_USED  = "demoUsed";
    private static final String KEY_DEVICE_ID  = "deviceId";

    private static FirebaseManager instance;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Gson gson = new Gson();

    // ── Realtime Database (Mithai app jaise device-wise backup) ──
    private final DatabaseReference rtdbRef;
    private static final String RTDB_ROOT = "dairy_backup";

    private FirebaseManager() {
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        rtdbRef = FirebaseDatabase.getInstance().getReference(RTDB_ROOT);
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

    // =====================================================================
    // 🔒 DEMO LOCK — Demo sirf ek baar mile (reinstall ke baad bhi nahi)
    // =====================================================================

    public interface BooleanCallback {
        void onSuccess(boolean value);
        void onFailure(String error);
    }

    public interface StringCallback {
        void onSuccess(String value);
        void onFailure(String error);
    }

    // Account pe demo "used" mark karo (ek baar demo start hote hi call karo)
    public void saveDemoUsed() {
        String uid = getCurrentUserId();
        if (uid == null) return;
        db.collection(COLLECTION_USERS).document(uid)
                .set(Collections.singletonMap(KEY_DEMO_USED, true), SetOptions.merge())
                .addOnSuccessListener(a -> Log.d(TAG, "Demo used flag saved"))
                .addOnFailureListener(e -> Log.e(TAG, "saveDemoUsed failed: " + e.getMessage()));
    }
    public void checkLicenseRevoked(String deviceId, BooleanCallback callback) {
    if (deviceId == null || deviceId.isEmpty()) { callback.onSuccess(false); return; }
    db.collection("revoked_licenses").document(deviceId)
            .get(Source.SERVER)
            .addOnSuccessListener(doc -> {
                Boolean revoked = doc.getBoolean("revoked");
                callback.onSuccess(revoked != null && revoked);
            })
            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
}
    // Firebase se check karo ki is account pe demo pehle use ho chuka hai kya
    public void checkDemoUsed(BooleanCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onSuccess(false); return; }
        db.collection(COLLECTION_USERS).document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    Boolean used = doc.getBoolean(KEY_DEMO_USED);
                    callback.onSuccess(used != null && used);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // =====================================================================
    // 🔒 DEVICE-ID DEMO LOCK — "ek device id sirf ek baar hi demo use kar sake"
    // Pehla wala lock (KEY_DEMO_USED) account/UID se juda tha, isliye reinstall
    // ke baad naya/dusra account banake demo dobara mil jaata tha. Ye naya lock
    // seedha DEVICE ID pe based hai (alag collection "device_locks"), isliye
    // reinstall ya naya account — kuch bhi badlo, same device pe demo sirf
    // ek hi baar milega.
    //
    // NOTE: Firestore Security Rules mein ye collection allow karna hoga, e.g.:
    //   match /device_locks/{deviceId} {
    //     allow read: if request.auth != null;
    //     allow create, update: if request.auth != null
    //                  && request.resource.data.demoUsed == true; // sirf true set kar sakte hain, hata nahi sakte
    //   }
    // =====================================================================
    private static final String COLLECTION_DEVICE_LOCKS = "device_locks";

    // Is device ID ne pehle demo use kiya hai kya — Firestore se confirm karo
    public void checkDeviceDemoUsed(String deviceId, BooleanCallback callback) {
        if (deviceId == null || deviceId.isEmpty()) { callback.onSuccess(false); return; }
        db.collection(COLLECTION_DEVICE_LOCKS).document(deviceId)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    Boolean used = doc.getBoolean(KEY_DEMO_USED);
                    callback.onSuccess(used != null && used);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // 🔑 License check — licenses collection mein deviceId field se match karke document dhundo.
    // Write client se kabhi possible nahi (rules mein blocked) — sirf admin Console se daalega.
    // Expiry check Firestore SERVER time se hota hai (phone ki date change karke bypass nahi ho sakta).
    public void checkLicenseValid(String deviceId, BooleanCallback callback) {
        if (deviceId == null || deviceId.isEmpty()) { callback.onSuccess(false); return; }
        String uid = getCurrentUserId();
        if (uid == null) { callback.onSuccess(false); return; }

        Map<String, Object> tsData = new HashMap<>();
        tsData.put("ts", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("server_time").document(uid).set(tsData)
            .addOnSuccessListener(v ->
                db.collection("server_time").document(uid).get(Source.SERVER)
                    .addOnSuccessListener(tsDoc -> {
                        com.google.firebase.Timestamp serverNow = tsDoc.getTimestamp("ts");
                        long serverTimeMillis = (serverNow != null)
                                ? serverNow.toDate().getTime()
                                : System.currentTimeMillis(); // fallback agar kuch gadbad ho

                        db.collection("licenses")
                            .whereEqualTo("deviceId", deviceId)
                            .limit(1)
                            .get(Source.SERVER)
                            .addOnSuccessListener(query -> {
                                if (query.isEmpty()) { callback.onSuccess(false); return; }
                                DocumentSnapshot doc = query.getDocuments().get(0);
                                Boolean valid = doc.getBoolean("valid");
                                boolean isValid = valid != null && valid;

                                if (isValid) {
                                    com.google.firebase.Timestamp expiresAt = doc.getTimestamp("expiresAt");
                                    if (expiresAt != null) {
                                        isValid = expiresAt.toDate().getTime() > serverTimeMillis;
                                    }
                                }
                                callback.onSuccess(isValid);
                            })
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                    })
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage())))
            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }


    // Device ID pe demo "used" permanently mark karo (demo start hote hi call karo)
    public void markDeviceDemoUsed(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return;
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_DEMO_USED, true);
        data.put("firstUsedAt", System.currentTimeMillis());
        db.collection(COLLECTION_DEVICE_LOCKS).document(deviceId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(a -> Log.d(TAG, "Device demo lock saved: " + deviceId))
                .addOnFailureListener(e -> Log.e(TAG, "markDeviceDemoUsed failed: " + e.getMessage()));
    }

    // =====================================================================
    // 🔗 DEVICE ID LINK — Account ek hi device se chale (pehli login wala device lock)
    // =====================================================================

    // Pehli baar login pe device ID Firebase mein save karo (agar already set nahi hai to)
    public void saveDeviceIdIfNew(String deviceId) {
        String uid = getCurrentUserId();
        if (uid == null || deviceId == null) return;
        DocumentReference ref = db.collection(COLLECTION_USERS).document(uid);
        ref.get(Source.SERVER).addOnSuccessListener(doc -> {
            if (!doc.exists() || !doc.contains(KEY_DEVICE_ID)) {
                ref.set(Collections.singletonMap(KEY_DEVICE_ID, deviceId), SetOptions.merge())
                        .addOnSuccessListener(a -> Log.d(TAG, "Device ID linked: " + deviceId))
                        .addOnFailureListener(e -> Log.e(TAG, "saveDeviceIdIfNew failed: " + e.getMessage()));
            }
        }).addOnFailureListener(e -> Log.e(TAG, "saveDeviceIdIfNew read failed: " + e.getMessage()));
    }

    // Firebase se linked device ID lao (BackupFragment isko use karega backup/restore ke liye)
    public void getLinkedDeviceId(StringCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onSuccess(null); return; }
        db.collection(COLLECTION_USERS).document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> callback.onSuccess(doc.getString(KEY_DEVICE_ID)))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // =====================================================================
    // 📱 REALTIME DATABASE BACKUP — Device ID ke naam se (Mithai app jaise)
    // Structure: dairy_backup/{deviceId}/{date}/customers, entries, payments, rateHistory
    // =====================================================================

    public interface RtdbCallback {
        void onSuccess(String message);
        void onFailure(String error);
    }

    public void backupToRealtimeDB(DairyDataManager dm, String deviceId, RtdbCallback callback) {
        String currentUid = getCurrentUserId();
        if (currentUid == null) { callback.onFailure("Login nahi hai!"); return; }
        if (dm.getCustomers().isEmpty() && dm.getEntries().isEmpty()) {
            callback.onFailure("⚠️ Koi data nahi — Backup skip!");
            return;
        }
        final String uid = currentUid; // 🔒 FIX: ANDROID_ID guessable tha, ab auth UID use karo (final for lambda)

        String dateKey    = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String backupTime = new SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()).format(new Date());

        String customersJson   = gson.toJson(dm.getCustomers());
        String entriesJson     = gson.toJson(dm.getEntries());
        String paymentsJson    = gson.toJson(dm.getPayments());
        String rateHistoryJson = gson.toJson(dm.getRateHistory());

        DatabaseReference todayRef = rtdbRef.child(uid).child(dateKey);
        todayRef.child("customers").setValue(customersJson);
        todayRef.child("entries").setValue(entriesJson);
        todayRef.child("payments").setValue(paymentsJson);
        todayRef.child("rateHistory").setValue(rateHistoryJson);
        todayRef.child("backupTime").setValue(backupTime)
            .addOnSuccessListener(v -> {
                // 10 din se purane delete karo
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Calendar cal = Calendar.getInstance();
                for (int i = 11; i <= 60; i++) {
                    cal.setTime(new Date());
                    cal.add(Calendar.DAY_OF_YEAR, -i);
                    rtdbRef.child(uid).child(sdf.format(cal.getTime())).removeValue();
                }
                callback.onSuccess("✅ Realtime DB Backup ho gaya!\n📅 " + dateKey
                    + "\n👥 " + dm.getCustomers().size() + " Members"
                    + "\n📋 " + dm.getEntries().size() + " Entries");
            })
            .addOnFailureListener(e -> callback.onFailure("❌ Backup Failed: " + e.getMessage()));
    }

    public void listRealtimeBackupDates(String deviceId, RtdbCallback callback) {
        String currentUid = getCurrentUserId();
        if (currentUid == null) { callback.onFailure("Login nahi hai!"); return; }
        final String uid = currentUid; // 🔒 FIX
        rtdbRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    callback.onFailure("❌ Koi Realtime backup nahi mila!");
                    return;
                }
                ArrayList<String> dates = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.getKey() != null) dates.add(child.getKey());
                }
                Collections.sort(dates, Collections.reverseOrder());
                callback.onSuccess(String.join(",", dates));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                callback.onFailure("❌ Firebase Error: " + error.getMessage());
            }
        });
    }

    public void restoreFromRealtimeDB(DairyDataManager dm, String deviceId,
                                      String dateKey, RtdbCallback callback) {
        String currentUid = getCurrentUserId();
        if (currentUid == null) { callback.onFailure("Login nahi hai!"); return; }
        final String uid = currentUid; // 🔒 FIX
        rtdbRef.child(uid).child(dateKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onFailure("❌ Is date ka backup nahi mila!");
                    return;
                }
                try {
                    String customersJson   = snapshot.child("customers").getValue(String.class);
                    String entriesJson     = snapshot.child("entries").getValue(String.class);
                    String paymentsJson    = snapshot.child("payments").getValue(String.class);
                    String rateHistoryJson = snapshot.child("rateHistory").getValue(String.class);
                    String backupTime      = snapshot.child("backupTime").getValue(String.class);

                    if (customersJson != null) {
                        List<Customer> list = gson.fromJson(customersJson,
                            new TypeToken<List<Customer>>(){}.getType());
                        dm.saveCustomers(list != null ? list : new ArrayList<>());
                    }
                    if (entriesJson != null) {
                        List<MilkEntry> list = gson.fromJson(entriesJson,
                            new TypeToken<List<MilkEntry>>(){}.getType());
                        dm.saveEntries(list != null ? list : new ArrayList<>());
                    }
                    if (paymentsJson != null) {
                        List<Payment> list = gson.fromJson(paymentsJson,
                            new TypeToken<List<Payment>>(){}.getType());
                        dm.savePayments(list != null ? list : new ArrayList<>());
                    }
                    if (rateHistoryJson != null) {
                        List<RateEntry> list = gson.fromJson(rateHistoryJson,
                            new TypeToken<List<RateEntry>>(){}.getType());
                        dm.saveRateHistory(list != null ? list : new ArrayList<>());
                    }

                    callback.onSuccess("✅ Restore ho gaya!\n📅 " + dateKey
                        + "\n⏱ Backup: " + (backupTime != null ? backupTime : "Unknown")
                        + "\n👥 " + dm.getCustomers().size() + " Members");
                } catch (Exception e) {
                    callback.onFailure("❌ Restore Error: " + e.getMessage());
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                callback.onFailure("❌ Firebase Error: " + error.getMessage());
            }
        });
    }
}
