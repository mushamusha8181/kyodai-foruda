package com.mushamusha.giantfolder;

import android.app.Activity;
import android.app.AlertDialog;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SecurityHelper {
    private SecurityHelper() {}

    public static boolean canAuthenticate(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                KeyguardManager km=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);
                return km!=null && km.isDeviceSecure();
            }
            FingerprintManager fm=(FingerprintManager)c.getSystemService(Context.FINGERPRINT_SERVICE);
            return fm!=null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch(Exception e) { return false; }
    }

    public static void authenticate(Activity a, String title, Runnable success) {
        authenticate(a, title, success, () -> {});
    }

    public static void authenticate(Activity a, String title, Runnable success, Runnable failureOrCancel) {
        AtomicBoolean finished = new AtomicBoolean(false);
        Runnable ok = () -> { if (finished.compareAndSet(false, true)) success.run(); };
        Runnable fail = () -> { if (finished.compareAndSet(false, true)) failureOrCancel.run(); };

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                BiometricPrompt p = new BiometricPrompt.Builder(a)
                        .setTitle(title)
                        .setSubtitle("本人確認してください")
                        .setDeviceCredentialAllowed(true)
                        .build();
                p.authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { ok.run(); }
                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        if (msg != null && msg.length() > 0) Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                        fail.run();
                    }
                });
                return;
            } catch (Exception ignored) {}
        }

        if (Build.VERSION.SDK_INT >= 28) {
            try {
                BiometricPrompt p = new BiometricPrompt.Builder(a)
                        .setTitle(title)
                        .setSubtitle("本人確認してください")
                        .setNegativeButton("キャンセル", a.getMainExecutor(), (d, w) -> fail.run())
                        .build();
                p.authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { ok.run(); }
                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        if (code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED && msg != null && msg.length() > 0) {
                            Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                        }
                        fail.run();
                    }
                });
                return;
            } catch (Exception ignored) {}
        }

        try {
            FingerprintManager fm = (FingerprintManager) a.getSystemService(android.content.Context.FINGERPRINT_SERVICE);
            if (fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints()) {
                CancellationSignal signal = new CancellationSignal();
                final AlertDialog[] holder = new AlertDialog[1];
                AlertDialog dialog = new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setMessage("指紋センサーに触れてください")
                        .setNegativeButton("キャンセル", (d, w) -> {
                            signal.cancel();
                            fail.run();
                        })
                        .create();
                holder[0] = dialog;
                dialog.show();
                fm.authenticate(null, signal, 0, new FingerprintManager.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult r) {
                        if (holder[0] != null && holder[0].isShowing()) holder[0].dismiss();
                        ok.run();
                    }
                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        if (holder[0] != null && holder[0].isShowing()) holder[0].dismiss();
                        if (!signal.isCanceled() && msg != null && msg.length() > 0) Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
                        fail.run();
                    }
                }, null);
                return;
            }
        } catch (Exception ignored) {}

        Toast.makeText(a, "端末のロック解除または生体認証を利用できません", Toast.LENGTH_SHORT).show();
        fail.run();
    }
}
