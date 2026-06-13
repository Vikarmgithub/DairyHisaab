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
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;

    private Button btnGoogleSignIn, btnLogin, btnRegister;
    private EditText etEmail, etPassword;
    private TextView tvToggle;
    private boolean isLoginMode = true;

    private EditText etPhone, etOtp;
    private Button btnSendOtp, btnVerifyOtp;
    private LinearLayout layoutOtpVerify;
    private TextView tvResendOtp;

    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;

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
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnLogin        = findViewById(R.id.btnLogin);
        btnRegister     = findViewById(R.id.btnRegister);
        etEmail         = findViewById(R.id.etEmail);
        etPassword      = findViewById(R.id.etPassword);
        tvToggle        = findViewById(R.id.tvToggle);
        etPhone         = findViewById(R.id.etPhone);
        etOtp           = findViewById(R.id.etOtp);
        btnSendOtp      = findViewById(R.id.btnSendOtp);
        btnVerifyOtp    = findViewById(R.id.btnVerifyOtp);
        layoutOtpVerify = findViewById(R.id.layoutOtpVerify);
        tvResendOtp     = findViewById(R.id.tvResendOtp);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> loginWithEmail());
        btnRegister.setOnClickListener(v -> registerWithEmail());
        tvToggle.setOnClickListener(v -> toggleMode());
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        btnSendOtp.setOnClickListener(v -> sendOtp());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        tvResendOtp.setOnClickListener(v -> sendOtp());
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
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email aur password daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password kam se kam 6 characters ka hona chahiye", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        mAuth.getCurrentUser().sendEmailVerification()
                                .addOnCompleteListener(emailTask -> {
                                    mAuth.signOut();
                                    Toast.makeText(this,
                                        "Account ban gaya! " + email + " pe verification link bheja gaya.",
                                        Toast.LENGTH_LONG).show();
                                    toggleMode();
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
            btnRegister.setVisibility(View.GONE);
            tvToggle.setText("Naya account banao? Register karo");
        } else {
            btnLogin.setVisibility(View.GONE);
            btnRegister.setVisibility(View.VISIBLE);
            tvToggle.setText("Pehle se account hai? Login karo");
        }
    }

    // ─── PHONE OTP ───

    private void sendOtp() {
        String phone = etPhone.getText().toString().trim();
        if (!phone.startsWith("+")) {
            phone = "+91" + phone;
        }
        if (phone.length() < 10) {
            Toast.makeText(this, "Sahi mobile number daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        btnSendOtp.setEnabled(false);

        PhoneAuthOptions.Builder optionsBuilder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks);

        if (mResendToken != null) {
            optionsBuilder.setForceResendingToken(mResendToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build());
    }

    private void verifyOtp() {
        String otp = etOtp.getText().toString().trim();
        if (otp.isEmpty() || otp.length() < 6) {
            Toast.makeText(this, "6 digit OTP daalo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mVerificationId == null) {
            Toast.makeText(this, "Pehle OTP bhejein", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, otp);
        signInWithPhoneCredential(credential);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            showLoading(true);
            signInWithPhoneCredential(credential);
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            showLoading(false);
            btnSendOtp.setEnabled(true);
            Toast.makeText(LoginActivity.this,
                "OTP bhejna fail: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCodeSent(@NonNull String verificationId,
                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
            showLoading(false);
            mVerificationId = verificationId;
            mResendToken = token;
            layoutOtpVerify.setVisibility(View.VISIBLE);
            tvResendOtp.setVisibility(View.VISIBLE);
            btnSendOtp.setText("OTP Dobara Bhejo");
            btnSendOtp.setEnabled(true);
            Toast.makeText(LoginActivity.this, "OTP bhej diya! SMS check karo.", Toast.LENGTH_SHORT).show();
        }
    };

    private void signInWithPhoneCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseManager.getInstance().saveLoginInfo(this);
                        doAutoBackup();
                        goToMain();
                    } else {
                        Toast.makeText(this,
                            "OTP galat hai ya expire ho gaya. Dobara try karo.",
                            Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ─── GOOGLE ───

    private void signInWithGoogle() {
        showLoading(true);
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, t -> {
                            showLoading(false);
                            if (t.isSuccessful()) {
                                FirebaseManager.getInstance().saveLoginInfo(this);
                                doAutoBackup();
                                goToMain();
                            } else {
                                Toast.makeText(this, "Google login fail!", Toast.LENGTH_LONG).show();
                            }
                        });
            } catch (ApiException e) {
                showLoading(false);
                Toast.makeText(this, "Google login fail!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ─── HELPERS ───

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