param([string]$Serial)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $projectRoot
try {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PdemoInstall=true --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Demo build failed.' }
    $adb = Join-Path $projectRoot '.tooling/android-sdk/platform-tools/adb.exe'
    if (!(Test-Path -LiteralPath $adb)) { $adb = 'adb' }
    $deviceArgs = @()
    if ($Serial) { $deviceArgs = @('-s', $Serial) }
    & $adb @deviceArgs install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Demo installation failed.' }
    & $adb @deviceArgs install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Demo loader installation failed.' }
    $seedOutput = & $adb @deviceArgs shell am instrument -w jm.yardmoney.demo.test/jm.yardmoney.demo.DemoDataInstaller
    $seedOutput | Write-Output
    if ($LASTEXITCODE -ne 0 -or !($seedOutput -match 'INSTRUMENTATION_CODE: -1') -or $seedOutput -match 'INSTRUMENTATION_RESULT: error=') {
        throw 'Demo data loading failed.'
    }
    & $adb @deviceArgs shell am start -n jm.yardmoney.demo/jm.yardmoney.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'Demo launch failed.' }
} finally { Pop-Location }
