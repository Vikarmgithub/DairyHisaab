package com.example.dairyhisaab;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupFragment extends Fragment {

    private static final int REQUEST_RESTORE_FILE = 101;
    private DairyDataManager dm;
    private Gson gson = new Gson();
    private FirebaseManager firebaseManager;

    public BackupFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_backup, container, false);
        dm = DairyDataManager.getInstance(getContext());
        firebaseManager = FirebaseManager.getInstance();

        view.findViewById(R.id.btnBackup).setOnClickListener(v -> doLocalBackup());
        view.findViewById(R.id.btnDriveBackup).setOnClickListener(v -> doFirebaseBackup());
        view.findViewById(R.id.btnDriveRestore).setOnClickListener(v -> doFirebaseRestore());
        view.findViewById(R.id.btnRestore).setOnClickListener(v -> pickRestoreFile());
        view.findViewById(R.id.btnSignOut).setOnClickListener(v -> signOut());

        refreshStatus(view);
        refreshAccountStatus(view);
        return view;
    }

    private void doFirebaseBackup() {
        if (!firebaseManager.isLoggedIn()) {
            startActivity(new Intent(getActivity(), LoginActivity.class));
            return;
        }
        ProgressDialog pd = new ProgressDialog(getContext());
        pd.setMessage("☁️ Cloud pe backup ho raha hai...");
        pd.setCancelable(false);
        pd.show();
        firebaseManager.uploadAllData(dm, new FirebaseManager.UploadCallback() {
            @Override
            public void onSuccess() {
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(getContext(), "✅ Cloud Backup Successful!", Toast.LENGTH_LONG).show();
                    refreshAccountStatus(getView());
                });
            }
            @Override
            public void onFailure(String error) {
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(getContext(), "❌ Backup failed!\n" + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void doFirebaseRestore() {
        AdminRestoreHelper.requestRestore(getActivity(), dm, firebaseManager, () -> refreshStatus(getView()));
    }

    private void signOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Account se sign out karna chahte hain?")
                .setPositiveButton("Haan", (d, w) -> {
                    firebaseManager.signOut();
                    refreshAccountStatus(getView());
                    Toast.makeText(getContext(), "Sign out ho gaye.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void refreshAccountStatus(View view) {
        if (view == null) return;
        TextView tvAccount = view.findViewById(R.id.tvGoogleAccount);
        if (firebaseManager.isLoggedIn()) {
            tvAccount.setText("✅ " + firebaseManager.getUserEmail() +
                    "\n👤 " + firebaseManager.getUserName() +
                    "\n☁️ Cloud backup active");
            view.findViewById(R.id.btnSignOut).setVisibility(View.VISIBLE);
        } else {
            tvAccount.setText("❌ Login nahi hai\nCloud backup ke liye login karo");
            view.findViewById(R.id.btnSignOut).setVisibility(View.GONE);
        }
    }

    private void doLocalBackup() {
        try {
            String json = buildBackupJson();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
            String fileName = "DairyHisaab_Backup_" + timestamp + ".json";
            File file = saveToDownloads(fileName, json.getBytes("UTF-8"));
            if (file == null) { Toast.makeText(getContext(), "Backup save nahi hua!", Toast.LENGTH_SHORT).show(); return; }
            Toast.makeText(getContext(), "✅ Backup saved!\nDownloads/" + fileName, Toast.LENGTH_LONG).show();
            shareFileAny(file);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Backup .json select karo"), REQUEST_RESTORE_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RESTORE_FILE && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            confirmAndRestoreFromUri(data.getData());
        }
    }

    private void confirmAndRestoreFromUri(Uri uri) {
        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Confirm Restore")
                .setMessage("Restore karne se SAARA DATA replace ho jayega.\n\nSure hain?")
                .setPositiveButton("Haan", (d, w) -> doRestoreFromUri(uri))
                .setNegativeButton("Cancel", null).show();
    }

    private void doRestoreFromUri(Uri uri) {
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close(); is.close();
            restoreFromJson(sb.toString());
            refreshStatus(getView());
            Toast.makeText(getContext(), "✅ Restore successful!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String buildBackupJson() {
        JsonObject root = new JsonObject();
        root.addProperty("app", "DairyHisaab");
        root.addProperty("version", 1);
        root.addProperty("date", DairyDataManager.today());
        root.add("customers", gson.toJsonTree(dm.getCustomers()));
        root.add("entries", gson.toJsonTree(dm.getEntries()));
        root.add("payments", gson.toJsonTree(dm.getPayments()));
        root.add("rateHistory", gson.toJsonTree(dm.getRateHistory()));
        return root.toString();
    }

    private void restoreFromJson(String json) throws Exception {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("app") || !root.get("app").getAsString().equals("DairyHisaab"))
            throw new Exception("Valid DairyHisaab backup nahi hai!");
        if (root.has("customers")) dm.saveCustomers(gson.fromJson(root.get("customers"), new com.google.gson.reflect.TypeToken<List<Customer>>(){}.getType()));
        if (root.has("entries")) dm.saveEntries(gson.fromJson(root.get("entries"), new com.google.gson.reflect.TypeToken<List<MilkEntry>>(){}.getType()));
        if (root.has("payments")) dm.savePayments(gson.fromJson(root.get("payments"), new com.google.gson.reflect.TypeToken<List<Payment>>(){}.getType()));
        if (root.has("rateHistory")) dm.saveRateHistory(gson.fromJson(root.get("rateHistory"), new com.google.gson.reflect.TypeToken<List<RateEntry>>(){}.getType()));
    }

    private void refreshStatus(View view) {
        if (view == null) return;
        ((TextView) view.findViewById(R.id.tvDataStatus)).setText(
                "👥 Members:      " + dm.getCustomers().size() + "\n" +
                "🥛 Milk Entries: " + dm.getEntries().size() + "\n" +
                "💰 Payments:     " + dm.getPayments().size() + "\n" +
                "📊 Rate Records: " + dm.getRateHistory().size());
    }

    private File saveToDownloads(String fileName, byte[] data) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data); fos.close();
            return file;
        } catch (IOException e) { return saveToCache(fileName, data); }
    }

    private File saveToCache(String fileName, byte[] data) {
        try {
            File file = new File(requireContext().getCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data); fos.close();
            return file;
        } catch (IOException e) { return null; }
    }

    private Uri getFileUri(File file) {
        return FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
    }

    private void shareFileAny(File file) {
        try {
            Uri uri = getFileUri(file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Backup share karo"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    }
