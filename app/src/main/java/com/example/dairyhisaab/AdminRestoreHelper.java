package com.example.dairyhisaab;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.widget.Toast;
import androidx.fragment.app.FragmentActivity;
import java.util.HashMap;
import java.util.Map;

public class AdminRestoreHelper {

    // 🔒 CRITICAL: Ye code khud "self-approve" bypass nahi rok sakta — woh
    // Firebase Console > Firestore > Rules mein fix karna hoga. Wahan ye rule
    // zaroor lagao:
    //   match /restore_requests/{id} {
    //     allow create: if request.auth != null
    //                    && request.resource.data.status == "pending";
    //     allow read: if request.auth != null;
    //     allow update, delete: if false; // sirf Admin SDK/Cloud Function se allowed
    //   }
    // Bina iske, koi bhi client apna khud ka status="approved" likh sakta hai.

    public static void requestRestore(FragmentActivity activity, DairyDataManager dm, FirebaseManager fm, Runnable onSuccess) {
        // 🔒 Demo Mode mein cloud restore bhi block karo
        if (DemoLockHelper.isDemoActive(activity)) {
            Toast.makeText(activity, "🚫 Demo Mode mein Restore allowed nahi hai.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("Admin Approval Chahiye")
            .setMessage("Cloud restore ke liye admin approval zaroori hai.\n\nRequest bhejein?")
            .setPositiveButton("Haan", (d, w) -> sendRequest(activity, dm, fm, onSuccess))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static void sendRequest(FragmentActivity activity, DairyDataManager dm, FirebaseManager fm, Runnable onSuccess) {
        String requestId = String.valueOf(System.currentTimeMillis());
        Map<String, Object> req = new HashMap<>();
        req.put("userEmail", fm.getUserEmail());
        req.put("status", "pending");
        req.put("timestamp", System.currentTimeMillis());

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("restore_requests").document(requestId)
            .set(req)
            .addOnSuccessListener(v -> activity.runOnUiThread(() -> {
                Toast.makeText(activity, "Request bhej di! Admin approve kare.", Toast.LENGTH_LONG).show();
                waitForApproval(activity, dm, fm, requestId, onSuccess);
            }))
            .addOnFailureListener(e -> activity.runOnUiThread(() ->
                Toast.makeText(activity, "Request fail: " + e.getMessage(), Toast.LENGTH_LONG).show()));
    }

    private static void waitForApproval(FragmentActivity activity, DairyDataManager dm, FirebaseManager fm, String requestId, Runnable onSuccess) {
        ProgressDialog wp = new ProgressDialog(activity);
        wp.setMessage("Admin approval ka wait kar rahe hain...\n\nCancel karne ke liye back dabao.");
        wp.setCancelable(true);
        wp.show();

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable[] checker = new Runnable[1];
        checker[0] = () -> {
            if (!activity.isFinishing() && wp.isShowing()) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("restore_requests").document(requestId)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap != null && snap.exists()) {
                            String st = (String) snap.get("status");
                            if ("approved".equals(st)) {
                                wp.dismiss();
                                doRestore(activity, dm, fm, requestId, onSuccess);
                            } else if ("rejected".equals(st)) {
                                wp.dismiss();
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("restore_requests").document(requestId).delete();
                                Toast.makeText(activity, "Admin ne reject kar diya.", Toast.LENGTH_LONG).show();
                            } else {
                                handler.postDelayed(checker[0], 5000);
                            }
                        }
                    });
            }
        };
        handler.postDelayed(checker[0], 5000);
    }

    private static void doRestore(FragmentActivity activity, DairyDataManager dm, FirebaseManager fm, String requestId, Runnable onSuccess) {
        ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage("☁️ Cloud se data la raha hai...");
        pd.setCancelable(false);
        pd.show();

        fm.downloadAllData(new FirebaseManager.DownloadCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Object> data) {
                activity.runOnUiThread(() -> {
                    pd.dismiss();
                    try {
                        fm.restoreData(dm, data);

                        // Request document delete karo
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("restore_requests").document(requestId).delete();

                        Toast.makeText(activity,
                            "✅ Restore Successful!\nApp ab reload ho rahi hai...",
                            Toast.LENGTH_LONG).show();

                        // ✅ BUG FIX #2: App ko restart karo taaki saare fragments
                        //    fresh data load karein. Pehle sirf BackupFragment ka
                        //    status update hota tha, baaki screens purana data dikhati theen.
                        new android.os.Handler().postDelayed(() -> {
                            Intent intent = new Intent(activity, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            activity.startActivity(intent);
                            activity.finish();
                        }, 1500); // 1.5 sec delay taaki toast dikhe

                    } catch (Exception ex) {
                        Toast.makeText(activity,
                            "❌ Restore Error: " + ex.getMessage(),
                            Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                activity.runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(activity, "❌ " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
