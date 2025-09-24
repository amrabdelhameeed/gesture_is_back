package com.example.myapplication;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;

/**
 * MainActivity that tries to run a system command via Shizuku (if available).
 * - Shows a "Finished" screen on success (programmatic UI, no XML)
 * - If Shizuku not running -> shows 3s countdown then launches Shizuku app
 * - If permission missing -> shows a button to request permission
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ShizukuApp";
    private final int REQ_CODE = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Track execution method for logging
    private boolean executedWithShizuku = false;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == REQ_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "✅ SHIZUKU PERMISSION GRANTED - Will execute with elevated privileges!");
                    runWithShizuku();
                } else {
                    Log.e(TAG, "❌ SHIZUKU PERMISSION DENIED - Showing permission UI");
                    mainHandler.post(this::showPermissionRequestScreen);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Minimal task description so app doesn't look weird in recent tasks
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new android.app.ActivityManager.TaskDescription(null, null, 0));
        }

        Log.i(TAG, "🚀 App started - Checking Shizuku status...");
        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        // If Shizuku already connected:
        if (Shizuku.pingBinder()) {
            Log.i(TAG, "✅ Shizuku service is connected and running!");
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✅ App already has Shizuku permission - proceeding with elevated execution");
                runWithShizuku();
            } else {
                Log.i(TAG, "⏳ Requesting Shizuku permission from user...");
                Shizuku.requestPermission(REQ_CODE);
                // show a button so user can manually re-request if needed
                showPermissionRequestScreen();
            }
        } else {
            Log.w(TAG, "⚠️ Shizuku service not connected - waiting for connection...");
            // Listen for binder and attempt to run/request permission when received
            Shizuku.addBinderReceivedListenerSticky(() -> {
                Log.i(TAG, "✅ Shizuku service connected!");
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "✅ Permission already granted - proceeding");
                    runWithShizuku();
                } else {
                    Log.i(TAG, "⏳ Requesting permission...");
                    Shizuku.requestPermission(REQ_CODE);
                    mainHandler.post(this::showPermissionRequestScreen);
                }
            });

            // Show the "not running" screen right away (countdown -> open Shizuku app)
            showShizukuNotRunningScreen();
        }
    }

    private void runWithShizuku() {
        new Thread(() -> {
            try {
                String command = "settings put global force_fsg_nav_bar 1";
                Log.i(TAG, "🎯 Executing command: " + command);

                int exitCode = executeShellCommand(command);

                Log.i(TAG, "✅ Command finished with exit code: " + exitCode);

                // Log execution method summary
                if (executedWithShizuku) {
                    Log.i(TAG, "🔥 EXECUTION METHOD: SHIZUKU (Elevated Privileges)");
                } else {
                    Log.w(TAG, "⚠️ EXECUTION METHOD: NORMAL JAVA PROCESS (Limited Privileges)");
                }

                if (exitCode == 0 && executedWithShizuku) {
                    mainHandler.post(this::showSuccessScreen);
                    mainHandler.postDelayed(this::finishAndKill, 1200); // show success shortly
                } else {
                    // Determine failure reason and show appropriate UI
                    if (!Shizuku.pingBinder()) {
                        mainHandler.post(this::showShizukuNotRunningScreen);
                    } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        mainHandler.post(this::showPermissionRequestScreen);
                    } else {
                        Log.e(TAG, "❌ Command failed for unknown reason or limited privileges");
                        mainHandler.post(this::showGenericFailureScreen);
                        mainHandler.postDelayed(this::finishAndKill, 2000);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "💥 Fatal error executing command", e);
                mainHandler.post(() -> {
                    showShizukuNotRunningScreen();
                });
            }
        }).start();
    }

    /**
     * Show "Finished" with icon (no XML).
     */
    private void showSuccessScreen() {
        Log.i(TAG, "🎉 Showing success screen...");
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(0xFF000000); // black background

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        layout.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        // Use a built-in check drawable; you can replace with your own drawable if you like
        icon.setImageResource(android.R.drawable.checkbox_on_background);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconLp.bottomMargin = dp(12);
        icon.setLayoutParams(iconLp);

        TextView text = new TextView(this);
        text.setText("✅ Finished");
        text.setTextSize(22f);
        text.setTextColor(0xFFFFFFFF);
        text.setGravity(Gravity.CENTER);

        layout.addView(icon);
        layout.addView(text);

        root.addView(layout);
        setContentView(root);
    }

    /**
     * Show 3s countdown then open Shizuku app.
     */
    private void showShizukuNotRunningScreen() {
        Log.w(TAG, "⚠️ Shizuku not running - showing countdown");
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(0xFF000000);

        TextView countdownView = new TextView(this);
        countdownView.setTextSize(20f);
        countdownView.setTextColor(0xFFFFFFFF);
        countdownView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        countdownView.setLayoutParams(tvLp);

        root.addView(countdownView);
        setContentView(root);

        new Thread(() -> {
            for (int i = 2; i > 0; i--) {
                final int sec = i;
                mainHandler.post(() -> countdownView.setText("⚠️ Shizuku not running\nOpening in " + sec + "s"));
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            // Try to open Shizuku app (manager) so the user can start it
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (intent != null) {
                    startActivity(intent);
                } else {
                    // fallback: open Play Store page (best effort)
                    Intent play = new Intent(Intent.ACTION_VIEW);
                    play.setData(android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"));
                    startActivity(play);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch Shizuku app", e);
            }
            mainHandler.post(this::finishAndKill);
        }).start();
    }
    private void finishAndKill() { Log.i(TAG, "🔚 Finishing and killing app..."); try { runOnUiThread(() -> { try { finishAffinity(); } catch (Throwable ignored) { } }); } finally { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(0); } }
    /**
     * Show button to request permission again.
     */
    private void showPermissionRequestScreen() {
        Log.w(TAG, "⚠️ Shizuku permission missing - showing grant button");
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(0xFF000000);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        layout.setLayoutParams(lp);

        TextView info = new TextView(this);
        info.setText("⚠️ Shizuku permission required");
        info.setTextSize(18f);
        info.setTextColor(0xFFFFFFFF);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        infoLp.bottomMargin = dp(10);
        info.setLayoutParams(infoLp);

        Button button = new Button(this);
        button.setText("Grant Shizuku Permission");
        button.setOnClickListener(v -> Shizuku.requestPermission(REQ_CODE));

        layout.addView(info);
        layout.addView(button);
        root.addView(layout);
        setContentView(root);
    }

    /**
     * Generic failure screen (no XML)
     */
    private void showGenericFailureScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(0xFF000000);

        TextView tv = new TextView(this);
        tv.setText("❌ Failed to execute command");
        tv.setTextSize(18f);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        tv.setLayoutParams(tvLp);

        root.addView(tv);
        setContentView(root);
    }

    /**
     * Execute using reflection to Shizuku.newProcess first, fallback to Runtime.exec.
     * Returns exit code or -1 on failure.
     */
    private int executeShellCommand(String command) {
        Log.i(TAG, "🔧 Attempting Shizuku reflection method first...");

        try {
            // METHOD 1: Try Shizuku reflection (elevated privileges)
            java.lang.reflect.Method newProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);

            Log.i(TAG, "🔓 Reflection access granted - calling Shizuku.newProcess()");

            Object remoteProcess = newProcessMethod.invoke(null,
                    new String[]{"sh", "-c", command}, null, null);

            Log.i(TAG, "✅ Shizuku.newProcess() successful - command is running with ELEVATED PRIVILEGES!");
            executedWithShizuku = true; // Mark as Shizuku execution

            java.lang.reflect.Method getInputStreamMethod = remoteProcess.getClass().getMethod("getInputStream");
            java.io.InputStream inputStream = (java.io.InputStream) getInputStreamMethod.invoke(remoteProcess);

            java.lang.reflect.Method waitForMethod = remoteProcess.getClass().getMethod("waitFor");

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = (Integer) waitForMethod.invoke(remoteProcess);

            Log.i(TAG, "🔥 SHIZUKU EXECUTION COMPLETE!");
            Log.i(TAG, "🔥 Exit Code: " + exitCode);
            Log.i(TAG, "🔥 Output: " + output.toString().trim());
            Log.i(TAG, "🔥 Privileges: ELEVATED (System-level access)");

            return exitCode;

        } catch (Exception e) {
            Log.w(TAG, "❌ Shizuku reflection failed: " + e.getMessage());
            Log.w(TAG, "⬇️ Falling back to normal Java Runtime.exec()...");
            executedWithShizuku = false; // Mark as normal execution

            try {
                // METHOD 2: Fallback to normal execution (limited privileges)
                Log.w(TAG, "🔄 Using Runtime.getRuntime().exec() - LIMITED PRIVILEGES!");

                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                int exitCode = process.waitFor();

                Log.w(TAG, "⚠️ NORMAL JAVA EXECUTION COMPLETE!");
                Log.w(TAG, "⚠️ Exit Code: " + exitCode);
                Log.w(TAG, "⚠️ Output: " + output.toString().trim());
                Log.w(TAG, "⚠️ Privileges: LIMITED (May not work for system settings)");

                return exitCode;
            } catch (IOException | InterruptedException fallbackException) {
                Log.e(TAG, "💥 ALL EXECUTION METHODS FAILED!", fallbackException);
                executedWithShizuku = false;
                return -1;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "🧹 Cleaning up permission listener...");
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    // small helper to convert dp to px
    private int dp(int dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
