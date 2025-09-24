# HyperOS Navigation Bypass (3rd-party launcher helper)

**One-line:** An Android helper app that uses **Shizuku** to bypass HyperOS restrictions which disable or modify navigation button behavior when using third-party launchers.

> ⚠️ This app requires the user to run/install Shizuku (or Sui) and grant the app Shizuku permission. It does **not** root the device by itself — it uses Shizuku's system binder to call privileged APIs when permitted. For details about Shizuku see their repo and Maven package. ([GitHub][1])

---

## Features

* Attempts to run a system-level `settings` command via Shizuku to re-enable expected navigation button behavior.
* Automatically falls back to normal (limited) `Runtime.exec()` if Shizuku is not available.
* Programmatic UI — no XML layouts required (small single-activity app).
* Graceful handling of Shizuku not running, permission denial, and command failure.

---

## How it works (high-level)

1. On startup the app checks whether the Shizuku service binder is available and whether the app has Shizuku permission.
2. If permitted, it invokes Shizuku's process creation (`Shizuku.newProcess`) via reflection to run a shell command with elevated privileges.
3. If Shizuku is unavailable or permission denied, the app shows UI to request permission or open the Shizuku manager, then falls back to a normal shell process with limited privileges.
4. The command currently used (example) is:

   ```bash
   settings put global force_fsg_nav_bar 1
   ```
---

## Requirements

* Android 6.0+ (Shizuku requires 6.0+).
* Shizuku (or Sui) installed and started on the device; the user must grant the app `Shizuku` permission. See Shizuku docs and Maven for API usage. ([shizuku.rikka.app][2])
* Minimum SDK / target SDK: use what your project already targets (your activity uses modern APIs; adjust `minSdkVersion` as required).

---

## Build / Gradle dependency

Add the Shizuku API AAR from Maven Central. At time of writing, `dev.rikka.shizuku:api:13.1.5` is a stable available version — you can update to a newer version if needed. ([repo.maven.apache.org][3])

**Gradle (module `build.gradle`):**

```gradle
dependencies {
    // Shizuku API - adjust version if newer is available
    implementation 'dev.rikka.shizuku:api:13.1.5'
}
```

Also ensure your `AndroidManifest.xml` has the activity and any required permissions (your app requests no special manifest permission for Shizuku itself — Shizuku handles elevated access after user grants it).

---

## Usage (developer / tester)

1. Build & install the APK on your test device or emulator.
2. Install and start **Shizuku** (or **Sui**). The easiest path:

   * Install the official Shizuku manager from Play Store / GitHub.
   * Start Shizuku in manager (via ADB or by launching the manager and tapping *Start*).
   * Grant the app permission when prompted (your app will call `Shizuku.requestPermission(REQ_CODE)`).
3. Open the app. If Shizuku is running and permission is granted the app will run the privileged command and show the success screen.
4. If Shizuku isn't running, the app will show a countdown then attempt to open the Shizuku manager page so the user can start it.

Helpful: If you're on an emulator and want to interact with Shizuku via ADB, use the Shizuku docs for the recommended ADB start steps. ([shizuku.rikka.app][2])

---

## Typical commands (for debugging)

* Logcat while testing:

  ```bash
  adb logcat -s ShizukuApp *:S
  ```
* (If using Frida or other tooling to test) run with your custom device id (as you prefer) — e.g.

  ```
  frida -D emulator-5554 -U -f com.example.myapplication --no-pause
  ```

  *(Adjust Frida flags to your workflow.)*

---

## Troubleshooting

* **Shizuku not running / app opens Play Store:** Make sure Shizuku is installed and started. The app attempts to open the manager if it can’t find the package.
* **Permission denied:** Use the "Grant Shizuku Permission" button in the app, or open the Shizuku manager and grant permission for your package.
* **Command exit code non-zero / no effect:** The fallback `Runtime.exec()` may not have permission to change global settings. Ensure Shizuku is used for privileged operations.
* **Reflection call fails (private API changes):** The app uses reflection to access `Shizuku.newProcess(...)`. If the Shizuku API changes upstream, update the reflection logic or migrate to the official public API calls documented in the repo. ([GitHub][1])

---

## Security & Legal

* This tool attempts to change system settings using elevated privileges. **Only use on devices you own or have permission to test.**
* Changes to system settings might be harmful or persistent — always test on a disposable device or take a backup.
* The project is provided for research/educational or internal testing purposes only.

---

## License

MIT — see `LICENSE` in this repo.

---

## Credits

* Built using the Shizuku API by RikkaApps. See the Shizuku project for documentation and release notes. ([GitHub][1])

---

## Suggested repo structure

```
/
├─ app/                      # Android Studio project
├─ README.md
```
