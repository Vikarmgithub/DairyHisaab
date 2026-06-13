package com.example.dairyhisaab;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source; // ✅ BUG FIX: Source import add kiya
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FirebaseManager {

    private static final String TAG = "FirebaseManager";
    private static final String COLLECTION_USERS = "users";
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

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

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

    public void uploadAllData(DairyDataManager dm, UploadCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("Login nahi hai!"); return; }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        Map<String, Object> data = new HashMap<>();
        data.put("lastBackup", timestamp);
        data.put("customers", gson.toJson(dm.getCustomers()));
        data.put("entries", gson.toJson(dm.getEntries()));
        data.put("payments", gson.toJson(dm.getPayments()));
        data.put("rateHistory", gson.toJson(dm.getRateHistory()));
        db.collection(COLLECTION_USERS).document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(a -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public interface DownloadCallback {
        void onSuccess(Map<String, Object> data);
        void onFailure(String error);
    }

    // ✅ BUG FIX #1: Source.SERVER add kiya — ab hamesha cloud se fresh data aayega,
    //    offline cache se nahi. Pehle cache se empty/purana data aata tha aur
    //    "Restore Successful!" dikha deta tha bina kuch restore kiye.
    public void downloadAllData(DownloadCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) { callback.onFailure("Login nahi hai!"); return; }
        db.collection(COLLECTION_USERS).document(uid)
                .get(Source.SERVER) // ✅ FIXED: pehle yahan sirf .get() tha
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) callback.onSuccess(doc.getData());
                    else callback.onFailure("Koi backup nahi mila! Pehle backup karo.");
                })
                .addOnFailureListener(e -> callback.onFailure("Download failed: " + e.getMessage()));
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
