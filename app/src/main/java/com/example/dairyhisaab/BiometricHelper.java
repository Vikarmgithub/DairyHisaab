package com.example.dairyhisaab;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.concurrent.Executor;

public class BiometricHelper {

    /**
     * Fingerprint/Pattern/Password check karo.
     * Agar phone mein koi bhi lock nahi hai → PIN dialog dikhao.
     *
     * @param fragment   calling fragment
     * @param title      dialog title
     * @param onSuccess  lock pass hone ke baad kya karna hai
     */
    public static void authenticate(Fragment fragment, String title, Runnable onSuccess) {
        Context ctx = fragment.requireContext();
        BiometricManager bm = BiometricManager.from(ctx);

        int canAuth = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            // Phone mein fingerprint/pattern/password hai — biometric prompt dikhao
            showBiometricPrompt(fragment, title, onSuccess);
        } else {
            // Koi lock nahi — PIN se kaam chalao
            DairyDataManager dm = DairyDataManager.getInstance(ctx);
            if (!dm.hasPin()) {
                showSetPinDialog(fragment, dm, onSuccess);
            } else {
                showVerifyPinDialog(fragment, dm, onSuccess);
            }
        }
    }

    // ── Biometric Prompt ──
    private static void showBiometricPrompt(Fragment fragment, String title, Runnable onSuccess) {
        Executor executor = ContextCompat.getMainExecutor(fragment.requireContext());

        BiometricPrompt biometricPrompt = new BiometricPrompt(fragment, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        onSuccess.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // User ne cancel kiya ya error — kuch nahi karte
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Wrong fingerprint — prompt khud retry karta hai
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Apna fingerprint, pattern ya password use karo")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    // ── PIN Set Dialog (jab phone mein lock nahi) ──
    private static void showSetPinDialog(Fragment fragment, DairyDataManager dm, Runnable onSuccess) {
        Context ctx = fragment.requireContext();
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(false);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(ctx);
        title.setText("🔐 PIN SET KARO");
        title.setTextSize(16);
        title.setTextColor(0xFF1A3A5C);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 8);

        TextView sub = new TextView(ctx);
        sub.setText("Phone mein lock nahi hai.\nYe PIN aapki app protect karega.");
        sub.setTextSize(12);
        sub.setTextColor(0xFF78909C);
        sub.setPadding(0, 0, 0, 24);

        EditText etPin = new EditText(ctx);
        etPin.setHint("4-6 digit PIN daalo");
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setPadding(20, 14, 20, 14);
        etPin.setBackgroundColor(0xFFEBF3FB);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, 14);
        etPin.setLayoutParams(lp1);

        EditText etPin2 = new EditText(ctx);
        etPin2.setHint("PIN dobara daalo");
        etPin2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin2.setPadding(20, 14, 20, 14);
        etPin2.setBackgroundColor(0xFFEBF3FB);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 0, 0, 24);
        etPin2.setLayoutParams(lp2);

        Button btnSet = new Button(ctx);
        btnSet.setText("✅ PIN SET KARO");
        btnSet.setBackgroundColor(0xFF1A3A5C);
        btnSet.setTextColor(Color.WHITE);

        btnSet.setOnClickListener(v -> {
            String p1 = etPin.getText().toString().trim();
            String p2 = etPin2.getText().toString().trim();
            if (p1.length() < 4) {
                Toast.makeText(ctx, "Kam se kam 4 digit ka PIN chahiye!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!p1.equals(p2)) {
                Toast.makeText(ctx, "PIN match nahi kiya!", Toast.LENGTH_SHORT).show();
                etPin2.setText("");
                return;
            }
            dm.savePin(p1);
            Toast.makeText(ctx, "✅ PIN set ho gaya!", Toast.LENGTH_SHORT).show();
            d.dismiss();
            onSuccess.run();
        });

        layout.addView(title);
        layout.addView(sub);
        layout.addView(etPin);
        layout.addView(etPin2);
        layout.addView(btnSet);
        d.setContentView(layout);
        d.show();
    }

    // ── PIN Verify Dialog ──
    private static void showVerifyPinDialog(Fragment fragment, DairyDataManager dm, Runnable onSuccess) {
        Context ctx = fragment.requireContext();
        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(true);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 56, 56, 56);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(ctx);
        title.setText("🔐 PIN DAALO");
        title.setTextSize(16);
        title.setTextColor(0xFF1A3A5C);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);

        EditText etPin = new EditText(ctx);
        etPin.setHint("PIN daalo");
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setPadding(20, 14, 20, 14);
        etPin.setBackgroundColor(0xFFEBF3FB);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 20);
        etPin.setLayoutParams(lp);

        Button btnOk = new Button(ctx);
        btnOk.setText("✅ VERIFY KARO");
        btnOk.setBackgroundColor(0xFF1A3A5C);
        btnOk.setTextColor(Color.WHITE);

        btnOk.setOnClickListener(v -> {
            if (dm.verifyPin(etPin.getText().toString().trim())) {
                d.dismiss();
                onSuccess.run();
            } else {
                Toast.makeText(ctx, "❌ Galat PIN!", Toast.LENGTH_SHORT).show();
                etPin.setText("");
            }
        });

        layout.addView(title);
        layout.addView(etPin);
        layout.addView(btnOk);
        d.setContentView(layout);
        d.show();
    }
}
