$ErrorActionPreference = 'Stop'
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$parentRoot = Split-Path -Parent $sourceRoot
$destinationRoot = Join-Path $parentRoot 'YardMoney'
if ((Split-Path -Leaf $sourceRoot) -eq 'YardMoney') {
    Write-Host "Project already named YardMoney: $sourceRoot"
    return
}
if ((Split-Path -Leaf $sourceRoot) -ne 'Android App') {
    throw 'Run this script only from the Android App project.'
}
if (Test-Path -LiteralPath $destinationRoot) {
    throw "Destination already exists: $destinationRoot"
}
# Release this shell's working-directory reference before the directory rename.
Set-Location -LiteralPath $parentRoot
[Environment]::CurrentDirectory = $parentRoot
try {
    Rename-Item -LiteralPath $sourceRoot -NewName 'YardMoney'
} catch {
    throw 'Close Codex, Android Studio and terminals using Android App, then run this script from a separate PowerShell window. The folder has not been renamed.'
}
$sdkPath = (Join-Path $destinationRoot '.tooling/android-sdk').Replace('\', '/')
[IO.File]::WriteAllText((Join-Path $destinationRoot 'local.properties'), "sdk.dir=$sdkPath`r`n")
$guidePath = Join-Path $destinationRoot 'docs/14-testing-environment.md'
$guide = [IO.File]::ReadAllText($guidePath).Replace($sourceRoot, $destinationRoot)
[IO.File]::WriteAllText($guidePath, $guide)
Write-Host "Renamed project to $destinationRoot and updated the SDK path and testing guide."
