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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;

    private Button btnLogin, btnRegister;
    private EditText etEmail, etPassword, etName;
    private android.view.View layoutName;
    private TextView tvToggle;
    private boolean isLoginMode = false;



    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            boolean isEmailUser = mAuth.getCurrentUser().getProviderData()
                    .stream().anyMatch(info -> info.getProviderId().equals("password"));
            if (!isEmailUser || mAuth.getCurrentUser().isEmailVerified()) {
                goToMain();
                return;
            }
        }

        initViews();
        setupClickListeners();
        setupGoogleSignIn();
    }

    private void initViews() {
        progressBar     = findViewById(R.id.progressBar);
        btnLogin        = findViewById(R.id.btnLogin);
        btnRegister     = findViewById(R.id.btnRegister);
        etEmail         = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etName = findViewById(R.id.etName);
        layoutName = findViewById(R.id.layoutName);
        tvToggle        = findViewById(R.id.tvToggle);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> loginWithEmail());
        btnRegister.setOnClickListener(v -> registerWithEmail());
        tvToggle.setOnClickListener(v -> toggleMode());
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("391870949035-n2k3oier2ro9ob0kb6sa3i19v626123m.apps.googleusercontent.com")
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    // ─── EMAIL / PASSWORD ───

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
                            doAutoBackup();
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

    private void registerWithEmail() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Naam daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email aur password daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password kam se kam 6 characters ka hona chahiye", Toast.LENGTH_SHORT).show();
            return;
        }
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

    // ─── PHONE OTP ───

    private void doAutoBackup() {
        FirebaseManager.getInstance().uploadAllData(
            DairyDataManager.getInstance(this),
            new FirebaseManager.UploadCallback() {
                public void onSuccess() {}
                public void onFailure(String e) {}
            }
        );
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}