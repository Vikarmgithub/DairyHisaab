package com.example.dairyhisaab;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            DairyDataManager dm = DairyDataManager.getInstance(context);
            Gson gson = new Gson();

            JsonObject root = new JsonObject();
            root.addProperty("app", "DairyHisaab");
            root.addProperty("version", 1);
            root.addProperty("date", DairyDataManager.today());
            root.add("customers", gson.toJsonTree(dm.getCustomers()));
            root.add("entries", gson.toJsonTree(dm.getEntries()));
            root.add("payments", gson.toJsonTree(dm.getPayments()));
            root.add("rateHistory", gson.toJsonTree(dm.getRateHistory()));

            String json = root.toString();
            boolean success = DriveHelper.uploadBackup(context, json);
            return success ? Result.success() : Result.retry();

        } catch (Exception e) {
            return Result.failure();
        }
    }
}