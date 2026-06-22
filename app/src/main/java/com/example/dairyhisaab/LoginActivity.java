package com.example.dairyhisaab;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;

public class LoginActivity extends AppCompatActivity {

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;

    private Button btnLogin, btnRegister;
    private EditText etEmail, etPassword, etName;
    private View layoutName;
    private TextView tvToggle;
    private boolean isLoginMode = true;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // 🩺 Agar pichli baar app crash hui thi, to exact reason pehle dikhao —
        // aur jab tak user OK na daba de, MainActivity pe redirect MAT karo
        // (warna dialog turant gayab ho jata hai aur crash phir se ho jata hai)
        String lastCrash = CrashHandler.getLastCrash(this);
        if (lastCrash != null) {
            CrashHandler.clearLastCrash(this);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("⚠️ Pichla Crash Detail")
                    .setMessage(lastCrash)
                    .setPositiveButton("OK", (d, w) -> proceedAfterCrashCheck())
                    .setCancelable(false)
                    .show();
            return;
        }

        proceedAfterCrashCheck();
    }

    private void proceedAfterCrashCheck() {
        if (mAuth.getCurrentUser() != null) {
            boolean isEmailUser = false;
            for (com.google.firebase.auth.UserInfo info : mAuth.getCurrentUser().getProviderData()) {
                if ("password".equals(info.getProviderId())) {
                    isEmailUser = true;
                    break;
                }
            }
            if (!isEmailUser || mAuth.getCurrentUser().isEmailVerified()) {
                goToMain();
                return;
            }
        }

        initViews();
        setupLoginMode();
        setupClickListeners();
        setupGoogleSignIn();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progressBar);
        btnLogin    = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        etName      = findViewById(R.id.etName);
        layoutName  = findViewById(R.id.layoutName);
        tvToggle    = findViewById(R.id.tvToggle);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            try {
                loginWithEmail();
            } catch (Exception e) {
                showCrashDialog(e);
            }
        });
        btnRegister.setOnClickListener(v -> {
            try {
                registerWithEmail();
            } catch (Exception e) {
                showCrashDialog(e);
            }
        });
        tvToggle.setOnClickListener(v -> toggleMode());
    }

    // Logcat ke bina bhi exact crash reason dekhne ke liye — error ko app crash
    // hone se pehle hi pakad ke dialog mein dikhao (class + message + line number)
    private void showCrashDialog(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n\n");
        for (StackTraceElement el : e.getStackTrace()) {
            if (el.getClassName().contains("dairyhisaab")) {
                sb.append(el.toString()).append("\n");
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Login Crash Caught")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("391870949035-n2k3oier2ro9ob0kb6sa3i19v626123m.apps.googleusercontent.com")
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void loginWithEmail() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email aur password daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        if (mAuth.getCurrentUser().isEmailVerified()) {
                            FirebaseManager.getInstance().saveLoginInfo(this);
                            autoRestoreIfEmpty(); // Sirf restore — backup nahi
                            goToMain();
                        } else {
                            mAuth.signOut();
                            Toast.makeText(this, "Pehle email verify karo! Inbox check karo.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Login pe sirf restore — agar local data khaali ho to cloud se le aao
    // Backup ab sirf data save hone pe hoga (DairyDataManager handle karta hai)
    private void autoRestoreIfEmpty() {
        DairyDataManager dm = DairyDataManager.getInstance(this);
        boolean hasLocalData = !dm.getCustomers().isEmpty()
                            || !dm.getEntries().isEmpty()
                            || !dm.getPayments().isEmpty();
        if (hasLocalData) return; // Data hai, kuch nahi karna

        // Khaali hai — cloud se silently restore karo
        // setRestoring(true) pehle karo takи restore ke dauraan koi auto-backup na ho
        dm.setRestoring(true);
        FirebaseManager.getInstance().downloadAllData(new FirebaseManager.DownloadCallback() {
            @Override
            public void onSuccess(java.util.Map<String, Object> data) {
                FirebaseManager.getInstance().restoreData(dm, data);
                // Thodi der baad restoring flag hatao — 3 sec delay taaki
                // save operations complete ho jayein pehle
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    dm.setRestoring(false);
                }, 3000);
                runOnUiThread(() ->
                    Toast.makeText(LoginActivity.this,
                        "☁️ Cloud se data restore ho gaya!", Toast.LENGTH_LONG).show()
                );
            }
            @Override
            public void onFailure(String error) {
                // Cloud pe koi backup nahi — fresh start
                dm.setRestoring(false);
            }
        });
    }

    private void registerWithEmail() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (name.isEmpty()) { Toast.makeText(this, "Naam daalo", Toast.LENGTH_SHORT).show(); return; }
        if (email.isEmpty() || password.isEmpty()) { Toast.makeText(this, "Email aur password daalo", Toast.LENGTH_SHORT).show(); return; }
        if (password.length() < 6) { Toast.makeText(this, "Password kam se kam 6 characters ka hona chahiye", Toast.LENGTH_SHORT).show(); return; }
        showLoading(true);
        String finalName = name;
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(finalName).build();
                        mAuth.getCurrentUser().updateProfile(profileUpdates);
                        mAuth.getCurrentUser().sendEmailVerification()
                                .addOnCompleteListener(emailTask -> {
                                    mAuth.signOut();
                                    Toast.makeText(this, "Account ban gaya! " + email + " pe verification link bheja gaya.", Toast.LENGTH_LONG).show();
                                });
                    } else {
                        Toast.makeText(this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setupLoginMode() {
        isLoginMode = true;
        btnLogin.setVisibility(View.VISIBLE);
        layoutName.setVisibility(View.GONE);
        btnRegister.setVisibility(View.GONE);
        tvToggle.setText("Naya account banao? Register karo");
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            btnLogin.setVisibility(View.VISIBLE);
            layoutName.setVisibility(View.GONE);
            btnRegister.setVisibility(View.GONE);
            tvToggle.setText("Naya account banao? Register karo");
        } else {
            btnLogin.setVisibility(View.GONE);
            layoutName.setVisibility(View.VISIBLE);
            btnRegister.setVisibility(View.VISIBLE);
            tvToggle.setText("Pehle se account hai? Login karo");
        }
    }

    private void goToMain() { startActivity(new Intent(this, MainActivity.class)); finish(); }
    private void showLoading(boolean show) { progressBar.setVisibility(show ? View.VISIBLE : View.GONE); }
}
