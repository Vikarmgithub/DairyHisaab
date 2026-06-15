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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> doLogout());

        refreshStatus(view);
        refreshAccountStatus(view);
        return view;
    }

    // ── PIN Change Dialog ──
    private void showChangePinDialog() {
        // oldPinVerified = true hoga jab purana PIN sahi dala ya password se verify hua
        final boolean[] oldPinVerified = {!dm.hasPin()}; // PIN nahi hai to verify ki zaroorat nahi

        Dialog d = new Dialog(getContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        // Title
        TextView title = new TextView(getContext());
        title.setText("🔐 PIN Change Karo");
        title.setTextSize(17);
        title.setTextColor(0xFF1a3c5e);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 6);

        // Subtitle
        TextView sub = new TextView(getContext());
        sub.setText("Pehle purana PIN daalo, phir naya set karo.");
        sub.setTextSize(12);
        sub.setTextColor(0xFF9e7b5a);
        sub.setPadding(0, 0, 0, 20);

        // Purana PIN field
        EditText etOld = new EditText(getContext());
        etOld.setHint(dm.hasPin() ? "Purana PIN" : "PIN abhi set nahi hai — koi bhi daalo");
        etOld.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etOld.setPadding(20, 14, 20, 14);
        etOld.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, 8);
        etOld.setLayoutParams(lp1);

        // ✅ "PIN bhool gaye?" link — sirf tab dikhe jab PIN set ho aur Firebase login ho
        TextView tvForgotPin = new TextView(getContext());
        tvForgotPin.setText("🔑 PIN bhool gaye? Account password se verify karo");
        tvForgotPin.setTextSize(12);
        tvForgotPin.setTextColor(0xFF1565C0);
        tvForgotPin.setPadding(4, 0, 4, 0);
        LinearLayout.LayoutParams lpForgot = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpForgot.setMargins(0, 0, 0, 20);
        tvForgotPin.setLayoutParams(lpForgot);

        // Sirf tab dikhao jab PIN set ho aur Firebase login ho
        tvForgotPin.setVisibility(dm.hasPin() && firebaseManager.isLoggedIn()
                ? View.VISIBLE : View.GONE);

        // Verified badge — jab password se verify ho jaye to dikhega
        TextView tvVerifiedBadge = new TextView(getContext());
        tvVerifiedBadge.setText("✅ Password se verify ho gaya! Ab naya PIN daalo.");
        tvVerifiedBadge.setTextSize(12);
        tvVerifiedBadge.setTextColor(0xFF2E7D32);
        tvVerifiedBadge.setBackgroundColor(0xFFE8F5E9);
        tvVerifiedBadge.setPadding(16, 10, 16, 10);
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBadge.setMargins(0, 0, 0, 16);
        tvVerifiedBadge.setLayoutParams(lpBadge);
        tvVerifiedBadge.setVisibility(View.GONE); // pehle hidden

        // Naya PIN
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

        // Naya PIN confirm
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

        // Save button
        Button btnSave = new Button(getContext());
        btnSave.setText("✅ PIN SAVE KARO");
        btnSave.setBackgroundColor(0xFF1a3c5e);
        btnSave.setTextColor(Color.WHITE);

        // ── "PIN bhool gaye?" click → password verify dialog ──
        tvForgotPin.setOnClickListener(v -> showPasswordVerifyDialog(verified -> {
            if (verified) {
                oldPinVerified[0] = true;
                // Purana PIN field disable karo, badge dikhao
                etOld.setText("✓ verified");
                etOld.setEnabled(false);
                etOld.setBackgroundColor(0xFFE8F5E9);
                tvForgotPin.setVisibility(View.GONE);
                tvVerifiedBadge.setVisibility(View.VISIBLE);
            }
        }));

        // ── Save button click ──
        btnSave.setOnClickListener(v -> {
            String oldPin = etOld.getText().toString().trim();
            String newPin = etNew.getText().toString().trim();
            String confirmPin = etConfirm.getText().toString().trim();

            // Purana PIN check — sirf tab jab password se verify nahi hua
            if (!oldPinVerified[0]) {
                if (dm.hasPin() && !dm.verifyPin(oldPin)) {
                    Toast.makeText(getContext(), "❌ Purana PIN galat hai!", Toast.LENGTH_SHORT).show();
                    etOld.setText("");
                    return;
                }
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
        layout.addView(tvForgotPin);
        layout.addView(tvVerifiedBadge);
        layout.addView(etNew);
        layout.addView(etConfirm);
        layout.addView(btnSave);
        d.setContentView(layout);
        d.show();
    }

    // ── Firebase Password Verify Dialog ──
    interface VerifyCallback { void onResult(boolean verified); }

    private void showPasswordVerifyDialog(VerifyCallback callback) {
        Dialog pd = new Dialog(getContext());
        pd.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(getContext());
        title.setText("🔑 Account Password Se Verify Karo");
        title.setTextSize(15);
        title.setTextColor(0xFF1a3c5e);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);

        TextView sub = new TextView(getContext());
        sub.setText("Apna Firebase account password daalo:\n" + firebaseManager.getUserEmail());
        sub.setTextSize(12);
        sub.setTextColor(0xFF666666);
        sub.setPadding(0, 0, 0, 20);

        EditText etPassword = new EditText(getContext());
        etPassword.setHint("Account Password");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setPadding(20, 14, 20, 14);
        etPassword.setBackgroundColor(0xFFF4F7FC);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 20);
        etPassword.setLayoutParams(lp);

        Button btnVerify = new Button(getContext());
        btnVerify.setText("🔓 VERIFY KARO");
        btnVerify.setBackgroundColor(0xFF1565C0);
        btnVerify.setTextColor(Color.WHITE);

        btnVerify.setOnClickListener(v -> {
            String password = etPassword.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(getContext(), "Password daalo", Toast.LENGTH_SHORT).show();
                return;
            }

            btnVerify.setEnabled(false);
            btnVerify.setText("Verify ho raha hai...");

            // Firebase re-authentication
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null || user.getEmail() == null) {
                Toast.makeText(getContext(), "Login nahi hai!", Toast.LENGTH_SHORT).show();
                pd.dismiss();
                callback.onResult(false);
                return;
            }

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
            user.reauthenticate(credential)
                .addOnSuccessListener(unused -> {
                    pd.dismiss();
                    callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    btnVerify.setEnabled(true);
                    btnVerify.setText("🔓 VERIFY KARO");
                    etPassword.setText("");
                    Toast.makeText(getContext(), "❌ Galat password! Dobara try karo.", Toast.LENGTH_SHORT).show();
                    callback.onResult(false);
                });
        });

        layout.addView(title);
        layout.addView(sub);
        layout.addView(etPassword);
        layout.addView(btnVerify);
        pd.setContentView(layout);
        pd.show();
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
            @Override public void onSuccess() {
                requireActivity().runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(getContext(), "✅ Cloud Backup Successful!", Toast.LENGTH_LONG).show();
                    refreshAccountStatus(getView());
                });
            }
            @Override public void onFailure(String error) {
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

    private void doLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("App se logout karna chahte hain?")
                .setPositiveButton("Haan", (d, w) -> {
                    firebaseManager.signOut();
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) getActivity().finish();
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
        if (requestCode == REQUEST_RESTORE_FILE && resultCode == android.app.Activity.RESULT_OK
                && data != null && data.getData() != null) {
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
        if (root.has("customers"))   dm.saveCustomers(gson.fromJson(root.get("customers"),   new com.google.gson.reflect.TypeToken<List<Customer>>(){}.getType()));
        if (root.has("entries"))     dm.saveEntries(gson.fromJson(root.get("entries"),       new com.google.gson.reflect.TypeToken<List<MilkEntry>>(){}.getType()));
        if (root.has("payments"))    dm.savePayments(gson.fromJson(root.get("payments"),     new com.google.gson.reflect.TypeToken<List<Payment>>(){}.getType()));
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
