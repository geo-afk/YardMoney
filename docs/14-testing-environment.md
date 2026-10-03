# Testing YardMoney on Windows

The project uses Java 24 at `C:\Program Files\Java\jdk-24`, the SDK in `.tooling/android-sdk`, and the checked-in Gradle wrapper. No global Gradle installation is needed.

## Physical device

Enable Developer options and USB debugging, connect a USB data cable, and accept the computer's debugging authorization on the unlocked phone. Windows may require the manufacturer's USB driver.

```powershell
Set-Location 'C:\Users\KoolAid\Pictures\Projects\YardMoney'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& '.\.tooling\android-sdk\platform-tools\adb.exe' devices
.\scripts\verify.ps1 -DeviceTests
```

The device should show `device`, not `unauthorized` or `offline`. The verification script builds and installs the debug app and instrumentation APK automatically. Without a device, omit `-DeviceTests` to run host tests, builds and lint.

After a successful run, open `app/build/reports/androidTests/connected/debug/index.html`. Open YardMoney on the phone for manual checks using [the device checklist](07-device-validation.md). Use fictional records; back up any existing financial records before manual editing or restore testing.

## Emulator

Open this project in Android Studio. In Device Manager, create a Pixel virtual device with an Android 16/API 36 image and start it. Ensure it appears in the project ADB device list, then use the same verification command. Real hardware remains useful for camera and biometric checks.

## Failures found during the first device run

On 2 October 2026, the Pixel 8 Pro reported 20 passed and 11 failed tests. Ten UI tests failed inside Espresso 3.5.0 because it called the unavailable reflective `InputManager.getInstance` method. Espresso 3.7.0 uses the system service instead. The project now explicitly selects Espresso 3.7.0, runner 1.7.0 and AndroidX JUnit 1.3.0, with updated dependency locks.

The remaining failure exposed a receipt detection defect: a capped brightness threshold falsely detected a paper boundary on a uniform white image. Detection now retains the required contrast above the background. The regression test also covers uniform dark, gray and bright images, while the existing quadrilateral test checks successful receipt detection.

Both device runs passed all 31 tests on the Pixel 8 Pro, including the expanded uniform-image regression test in the second run. Full `verify.ps1 -DeviceTests` succeeded: 61 host tests, debug and optimized unsigned release builds, and lint with zero errors and 34 warnings. This does not replace manual camera, authentication, accessibility or real-receipt accuracy validation. Archived APK verification files describe their original artifacts; use the newly built `app/build/outputs/apk/debug/app-debug.apk` for these fixes.

Sources: [Android device setup](https://developer.android.com/studio/run/device), [virtual devices](https://developer.android.com/studio/run/managing-avds), [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).
