package com.example.dairyhisaab;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // 🔒 Demo Mode mein auto-backup (Firebase + Local) bhi block karo
        if (DemoLockHelper.isDemoActive(context)) {
            return Result.success();
        }

        DairyDataManager dm = DairyDataManager.getInstance(context);

        // ✅ Khali data pe backup skip karo
        if (dm.getCustomers().isEmpty() && dm.getEntries().isEmpty()) {
            return Result.success();
        }

        // 1. Firebase backup
        boolean firebaseOk = false;
        try {
            FirebaseManager fm = FirebaseManager.getInstance();
            if (fm.isLoggedIn()) {
                final boolean[] done = {false};
                final boolean[] success = {false};

                fm.uploadAllData(dm, new FirebaseManager.UploadCallback() {
                    public void onSuccess() {
                        success[0] = true;
                        done[0]    = true;
                    }
                    public void onFailure(String e) {
                        done[0] = true;
                    }
                });

                // Firebase async hai — max 10 sec wait karo
                int waited = 0;
                while (!done[0] && waited < 10000) {
                    Thread.sleep(200);
                    waited += 200;
                }
                firebaseOk = success[0];
            }
        } catch (Exception e) {
            firebaseOk = false;
        }

        // 2. Local file backup (hamesha karo, Firebase chahe succeed ho ya fail)
        boolean localOk = LocalBackupHelper.saveLocalBackup(context);

        // Dono fail hue to retry
        if (!firebaseOk && !localOk) return Result.retry();

        return Result.success();
    }
}
