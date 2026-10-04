param([switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $projectRoot
try {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
    & .\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build, tests or lint failed.' }
    if ($DeviceTests) {
        & .\gradlew.bat :app:connectedDebugAndroidTest -PdeviceTestInstall=true --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Android device tests failed.' }
    }
} finally {
    Pop-Location
}
