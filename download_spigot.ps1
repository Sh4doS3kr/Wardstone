$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$metadataFile = "c:\Users\alex\Documents\CoreProtect\spigot-metadata.xml"
$content = Get-Content $metadataFile

$timestamp = [regex]::Match($content, "<timestamp>(.*?)</timestamp>").Groups[1].Value
$buildNumber = [regex]::Match($content, "<buildNumber>(.*?)</buildNumber>").Groups[1].Value

if (-not $timestamp -or -not $buildNumber) {
    Write-Host "Error parsing metadata"
    exit 1
}

$version = "1.21-R0.1-$timestamp-$buildNumber"
$url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.21-R0.1-SNAPSHOT/spigot-api-$version.jar"
$outFile = "c:\Users\alex\Documents\CoreProtect\libs\spigot-api.jar"

Write-Host "Downloading $url..."
Invoke-WebRequest -Uri $url -OutFile $outFile
Write-Host "Downloaded to $outFile"
