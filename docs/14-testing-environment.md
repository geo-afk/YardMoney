# Testing YardMoney on Windows

The project uses Java 24 at `C:\Program Files\Java\jdk-24`, the SDK in `.tooling/android-sdk`, and the checked-in Gradle wrapper. No global Gradle installation is needed.

## Physical device

Enable Developer options and USB debugging, connect a USB data cable, and accept the computer's debugging authorization on the unlocked phone. Windows may require the manufacturer's USB driver.

```powershell
Set-Location 'C:\path\to\YardMoney'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& '.\.tooling\android-sdk\platform-tools\adb.exe' devices
.\scripts\verify.ps1 -DeviceTests
```

The device should show `device`, not `unauthorized` or `offline`. Device tests build and install a separate `jm.yardmoney.testhost` app and instrumentation APK automatically, preserving the normal and demo installations. Without a device, omit `-DeviceTests` to run host tests, builds and lint.

After a successful run, open `app/build/reports/androidTests/connected/debug/index.html`. Open YardMoney on the phone for manual checks using [the device checklist](07-device-validation.md). Use fictional records; back up any existing financial records before manual editing or restore testing.

## Emulator

Open this project in Android Studio. In Device Manager, create a Pixel virtual device with an Android 16/API 36 image and start it. Ensure it appears in the project ADB device list, then use the same verification command. Real hardware remains useful for camera and biometric checks.
