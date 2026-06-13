package com.example.dairyhisaab;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
        view.findViewById(R.id.btnChangePin).setOnClickListener(v -> showChangePinDialog());

        refreshStatus(view);
        refreshAccountStatus(view);
        return view;
    }

    // ── PIN Change Dialog ──
    private void showChangePinDialog() {
        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(getContext());
        title.setText("🔐 PIN Change Karo");
        title.setTextSize(17);
        title.setTextColor(0xFF1a3c5e);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 6);

        TextView sub = new TextView(getContext());
        sub.setText("Pehle purana PIN daalo, phir naya set karo.");
        sub.setTextSize(12);
        sub.setTextColor(0xFF9e7b5a);
        sub.setPadding(0, 0, 0, 24);

        EditText etOld = new EditText(getContext());
        etOld.setHint(dm.hasPin() ? "Purana PIN" : "PIN abhi set nahi hai — koi bhi daalo");
        etOld.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etOld.setPadding(20, 14, 20, 14);
        etOld.setBackgroundColor(0xFFF4F7FC);

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, 14);
        etOld.setLayoutParams(lp1);

        EditText etNew = new EditText(getContext());
        etNew.setHint("Naya PIN (4-6 digit)");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etNew.setPadding(20, 14, 20, 14);
        etNew.setBackgroundColor(0xFFF4F7FC);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, 0, 14);
        etNew.setLayoutParams(lp2);

        EditText etConfirm = new EditText(getContext());
        etConfirm.setHint("Naya PIN dobara daalo");
        etConfirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etConfirm.setPadding(20, 14, 20, 14);
        etConfirm.setBackgroundColor(0xFFF4F7FC);

        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.setMargins(0, 0, 0, 24);
        etConfirm.setLayoutParams(lp3);

        Button btnSave = new Button(getContext());
        btnSave.setText("✅ PIN SAVE KARO");
        btnSave.setBackgroundColor(0xFF1a3c5e);
        btnSave.setTextColor(Color.WHITE);

        btnSave.setOnClickListener(v -> {
            String oldPin = etOld.getText().toString().trim();
            String newPin = etNew.getText().toString().trim();
            String confirmPin = etConfirm.getText().toString().trim();

            // Agar PIN pehle se set hai toh verify karo
            if (dm.hasPin() && !dm.verifyPin(oldPin)) {
                Toast.makeText(getContext(), "❌ Purana PIN galat hai!", Toast.LENGTH_SHORT).show();
                etOld.setText("");
                return;
            }
            if (newPin.length() < 4) {
                Toast.makeText(getContext(), "Naya PIN kam se kam 4 digit ka chahiye!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPin.equals(confirmPin)) {
                Toast.makeText(getContext(), "❌ Naya PIN match nahi kiya!", Toast.LENGTH_SHORT).show();
                etConfirm.setText("");
                return;
            }
            dm.savePin(newPin);
            Toast.makeText(getContext(), "✅ PIN successfully change ho gaya!", Toast.LENGTH_SHORT).show();
            d.dismiss();
        });

        layout.addView(title);
        layout.addView(sub);
        layout.addView(etOld);
        layout.addView(etNew);
        layout.addView(etConfirm);
        layout.addView(btnSave);
        d.setContentView(layout);
        d.show();
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

    private void shareFileAny(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", file);
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
