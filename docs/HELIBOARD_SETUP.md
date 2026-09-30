# HeliBoard setup for AAOS emulators

1. Download `HeliBoard_*-release.apk` from the
   [official GitHub releases](https://github.com/HeliBorg/HeliBoard/releases/latest)
   and save it as `heliboard.apk`.
2. With the emulator running, install it for the active Android user and select
   it as the keyboard:

   ```bash
   USER_ID=$(adb shell am get-current-user | tr -d '\r')
   IME=helium314.keyboard/helium314.keyboard.latin.LatinIME
   adb install --user "$USER_ID" heliboard.apk
   adb shell ime enable --user "$USER_ID" "$IME"
   adb shell ime set --user "$USER_ID" "$IME"
   ```

3. Open **HeliBoard → Languages & Layouts**, turn off **Use system languages**,
   then add **Korean** and choose its layout. See the
   [HeliBoard language guide](https://github.com/HeliBorg/HeliBoard/wiki/1.-Languages).
