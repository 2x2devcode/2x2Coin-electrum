package com.x2x.wallet;

import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

/**
 * Gates sensitive actions behind biometrics when available, otherwise the wallet PIN.
 */
public final class AuthHelper {

    public interface Callback {
        void onAuthenticated();
        default void onCancelled() {}
    }

    private AuthHelper() {}

    public static void requireAuth(AppCompatActivity activity, WalletStorage storage,
                                   String reason, Callback cb) {
        if (!storage.hasPin()) {
            // Legacy wallets created before PIN support — ask to set one, then continue.
            promptSetPinThen(activity, storage, () -> requireAuth(activity, storage, reason, cb));
            return;
        }

        int can = BiometricManager.from(activity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (can == BiometricManager.BIOMETRIC_SUCCESS) {
            BiometricPrompt prompt = new BiometricPrompt(activity,
                    ContextCompat.getMainExecutor(activity),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            cb.onAuthenticated();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                    || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                    || errorCode == BiometricPrompt.ERROR_CANCELED) {
                                // Fall back to PIN when user dismisses biometrics.
                                promptPin(activity, storage, reason, cb);
                            } else {
                                promptPin(activity, storage, reason, cb);
                            }
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            // Keep the prompt open; no-op.
                        }
                    });
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Authenticate")
                    .setSubtitle(reason)
                    .setNegativeButtonText("Use PIN")
                    .build();
            prompt.authenticate(info);
        } else {
            promptPin(activity, storage, reason, cb);
        }
    }

    public static void promptPin(AppCompatActivity activity, WalletStorage storage,
                                 String reason, Callback cb) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("6-digit PIN");
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Enter PIN")
                .setMessage(reason)
                .setView(input)
                .setCancelable(true)
                .setPositiveButton("OK", (d, w) -> {
                    String pin = input.getText().toString().trim();
                    if (storage.verifyPin(pin)) {
                        cb.onAuthenticated();
                    } else {
                        Toast.makeText(activity, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                        cb.onCancelled();
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> cb.onCancelled())
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
        dialog.show();
    }

    public static void promptSetPinThen(AppCompatActivity activity, WalletStorage storage,
                                        Runnable after) {
        EditText pin1 = new EditText(activity);
        pin1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin1.setHint("Choose a 6-digit PIN");
        AlertDialog first = new AlertDialog.Builder(activity)
                .setTitle("Set wallet PIN")
                .setMessage("A PIN protects backup, send, and delete on this device.")
                .setView(pin1)
                .setCancelable(false)
                .setPositiveButton("Next", null)
                .create();
        first.setOnShowListener(d -> first.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String p = pin1.getText().toString().trim();
                    if (!p.matches("\\d{6}")) {
                        Toast.makeText(activity, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }
                    first.dismiss();
                    confirmPin(activity, storage, p, after);
                }));
        if (first.getWindow() != null) {
            first.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
        first.show();
    }

    private static void confirmPin(AppCompatActivity activity, WalletStorage storage,
                                   String expected, Runnable after) {
        EditText pin2 = new EditText(activity);
        pin2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin2.setHint("Confirm PIN");
        AlertDialog second = new AlertDialog.Builder(activity)
                .setTitle("Confirm PIN")
                .setView(pin2)
                .setCancelable(false)
                .setPositiveButton("Save", null)
                .create();
        second.setOnShowListener(d -> second.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String p = pin2.getText().toString().trim();
                    if (!expected.equals(p)) {
                        Toast.makeText(activity, "PINs do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    storage.setPin(p);
                    second.dismiss();
                    after.run();
                }));
        if (second.getWindow() != null) {
            second.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
        second.show();
    }

    /** Block screenshots/recents previews for activities that show secrets. */
    public static void enableFlagSecure(AppCompatActivity activity) {
        activity.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
    }
}
