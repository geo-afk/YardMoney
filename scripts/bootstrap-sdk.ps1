param([string]$SdkRoot = (Join-Path $PSScriptRoot '..\.tooling\android-sdk'))
$ErrorActionPreference = 'Stop'
$SdkRoot = [System.IO.Path]::GetFullPath($SdkRoot)
$workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $SdkRoot.StartsWith($workspaceRoot + [System.IO.Path]::DirectorySeparatorChar)) { throw 'SDK bootstrap must stay inside this workspace.' }
New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
$downloadRoot = Join-Path $workspaceRoot '.tooling\downloads'
New-Item -ItemType Directory -Force -Path $downloadRoot | Out-Null
$indexPath = Join-Path $downloadRoot 'repository.xml'
Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-1.xml' -OutFile $indexPath
[xml]$index = Get-Content -LiteralPath $indexPath -Raw
foreach ($spec in @(@('platforms;android-36','platform-36_r02.zip','platforms\android-36'),@('build-tools;36.0.0','build-tools_r36_windows.zip','build-tools\36.0.0'),@('platform-tools','platform-tools_r37.0.1-win.zip','platform-tools'))) {
    $package = $index.SelectNodes('//*[local-name()="remotePackage"]') | Where-Object { $_.path -eq $spec[0] } | Where-Object { $_.SelectNodes('.//*[local-name()="url"]') | Where-Object { $_.InnerText -eq $spec[1] } } | Select-Object -First 1
    if (-not $package) { throw "Official package not found: $($spec[1])" }
    $archive = $package.SelectNodes('.//*[local-name()="archive"]') | Where-Object { $_.complete.url -eq $spec[1] } | Select-Object -First 1
    $checksumNode = $archive.SelectSingleNode('.//*[local-name()="checksum"]')
    $zipPath = Join-Path $downloadRoot $spec[1]
    if (-not (Test-Path $zipPath)) { Invoke-WebRequest ("https://dl.google.com/android/repository/" + $spec[1]) -OutFile $zipPath }
    $algorithm = if ($checksumNode.type -eq 'sha256') { 'SHA256' } else { 'SHA1' }
    if ((Get-FileHash $zipPath -Algorithm $algorithm).Hash.ToLowerInvariant() -ne $checksumNode.InnerText.Trim().ToLowerInvariant()) { throw 'SDK archive checksum mismatch.' }
    $unpack = Join-Path $downloadRoot ($spec[1] + '.unpacked')
    if (-not (Test-Path $unpack)) { Expand-Archive -LiteralPath $zipPath -DestinationPath $unpack }
    $source = Get-ChildItem -LiteralPath $unpack -Directory | Select-Object -First 1
    $target = Join-Path $SdkRoot $spec[2]
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Copy-Item -Path (Join-Path $source.FullName '*') -Destination $target -Recurse -Force
    Write-Output "Installed $($spec[0]) from verified official archive."
}
$localProperties = 'sdk.dir=' + $SdkRoot.Replace('\','/')
Set-Content -LiteralPath (Join-Path $workspaceRoot 'local.properties') -Value $localProperties -Encoding ascii
Write-Output "SDK path written to local.properties. No global settings or license-acceptance records changed."
