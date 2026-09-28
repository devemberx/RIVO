# Gboard setup for AAOS emulators

Use APK sideloading when Google Play is unavailable. Select the APK for the
**emulator's** CPU architecture, regardless of the host OS. The commands use
the default Android SDK location and `emulator-5554`; set `SDK_ROOT` or
`DEVICE` first if yours differ.

## Install the APK

Check the emulator's CPU architecture before choosing an APK:

```bash
if [ "$(uname -s)" = Darwin ]; then
  SDK_ROOT="${SDK_ROOT:-$HOME/Library/Android/sdk}"
else
  SDK_ROOT="${SDK_ROOT:-$HOME/Android/Sdk}"
fi
ADB="$SDK_ROOT/platform-tools/adb"
DEVICE="${DEVICE:-emulator-5554}"
"$ADB" -s "$DEVICE" shell getprop ro.product.cpu.abi
```

Choose a **single APK** for Gboard `18.0.3.954559732` (Android 8.0+, API 26+),
not an APK bundle that needs split installation:

| Emulator ABI | APK variant |
| --- | --- |
| `arm64-v8a` | [ARM64 APK, nodpi](https://www.apkmirror.com/apk/google-inc/gboard/gboard-the-google-keyboard-18-0-3-954559732-release/gboard-the-google-keyboard-18-0-3-954559732-release-arm64-v8a-4-android-apk-download/) (`175940518`) |
| `x86_64` | [x86_64 APK, nodpi](https://www.apkmirror.com/apk/google-inc/gboard/gboard-the-google-keyboard-18-0-3-954559732-release/gboard-the-google-keyboard-18-0-3-954559732-release-x86_64-android-apk-download/) (`175940520`) |
| `x86` | [x86 APK, nodpi](https://www.apkmirror.com/apk/google-inc/gboard/gboard-the-google-keyboard-18-0-3-954559732-release/gboard-the-google-keyboard-18-0-3-954559732-release-x86-2-android-apk-download/) (`175940519`) |

Download the matching **APK** (not a bundle) and save it as `gboard.apk`.
Verify it before installation (JDK 17 must be available):

```bash
"$SDK_ROOT/build-tools/34.0.0/apksigner" \
  verify --verbose --print-certs gboard.apk
```

Check that verification succeeds and compare the signer certificate SHA-256
with the fingerprint on the selected APK page. A valid APK signature alone
does not establish who published the APK.

Install for the active driver profile and select Gboard as its default keyboard:

```bash
ANDROID_USER=$("$ADB" -s "$DEVICE" shell am get-current-user | tr -d '\r')
IME=com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME

"$ADB" -s "$DEVICE" install --user "$ANDROID_USER" -r gboard.apk
"$ADB" -s "$DEVICE" shell ime enable --user "$ANDROID_USER" "$IME"
"$ADB" -s "$DEVICE" shell ime set --user "$ANDROID_USER" "$IME"
"$ADB" -s "$DEVICE" shell settings --user "$ANDROID_USER" get secure default_input_method
```

The last command must print the value of `IME`. Use the active driver profile,
rather than assuming system user `0`. Repeat the selection after switching profiles
or resetting emulator data. See the [ADB documentation](https://developer.android.com/tools/adb)
for installation commands.

## Enable Korean input

The emulator needs its own internet connection to download Korean input data:

```bash
"$ADB" -s "$DEVICE" shell svc wifi enable
"$ADB" -s "$DEVICE" shell cmd wifi status
```

Confirm in the emulator's Wi-Fi settings that it is connected and has internet
access; enabling Wi-Fi alone does not download the language data.

1. Open a text field, then tap the settings icon in Gboard's toolbar.
2. Select **Languages → Add keyboard → Korean → 2-set (두벌식) → Done**.
3. Keep the emulator connected while Korean input data downloads.
4. Use the on-screen keys to type `안녕 한글` and confirm that letters form syllables.

If input remains separate letters such as `ㅎㅏ`, check the emulator's connection,
allow the language data to finish downloading, and reopen the input field.
See [Gboard language settings](https://support.google.com/gboard/answer/7068494?hl=en).

## Use a smaller keyboard

Open Gboard's toolbar menu and select **Floating**. Drag the bottom handle to move
the keyboard away from chat content. This also avoids the full-screen input view
that can appear in landscape mode. Switch languages by holding the space bar.
