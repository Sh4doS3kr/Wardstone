# Script para compilar CoreProtect (Version 1.21.x)
# Requiere Java 21+ instalado

$ErrorActionPreference = "Continue"

$projectDir = $PSScriptRoot
$srcDir = "$projectDir\src\main\java"
$resourcesDir = "$projectDir\src\main\resources"
$buildDir = "$projectDir\build"
$classesDir = "$buildDir\classes"
$libDir = "$projectDir\libs"
$outputJar = "$buildDir\CoreProtect-1.0.0.jar"

# Limpieza completa
if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
if (-not (Test-Path $libDir)) { New-Item -ItemType Directory -Force -Path $libDir | Out-Null }

Write-Host "Verificando dependencias..." -ForegroundColor Cyan

# Buscar API jar (Spigot o Paper)
$apiJar = ""
if (Test-Path "$libDir\spigot-api.jar") {
    $apiJar = "$libDir\spigot-api.jar"
    Write-Host "Usando Spigot API: $apiJar" -ForegroundColor Green
} elseif (Test-Path "$libDir\paper-api.jar") {
    $apiJar = "$libDir\paper-api.jar"
    Write-Host "Usando Paper API: $apiJar" -ForegroundColor Green
} else {
    Write-Host "Error: No se encuentra API JAR (spigot-api.jar o paper-api.jar)" -ForegroundColor Red
    exit 1
}

$vaultJar = "$libDir\vault-api.jar"
$bluemapJar = "$libDir\bluemap-api.jar"
if (-not (Test-Path $vaultJar)) {
    Write-Host "Descargando Vault API..." -ForegroundColor Yellow
    $vaultUrl = "https://jitpack.io/com/github/MilkBowl/VaultAPI/1.7.1/VaultAPI-1.7.1.jar"
    try {
        Invoke-WebRequest -Uri $vaultUrl -OutFile $vaultJar
        Write-Host "Vault descargado correctamente." -ForegroundColor Green
    } catch {
        Write-Host "Error al descargar Vault. Descargalo manualmente." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Compilando codigo fuente con Java 21..." -ForegroundColor Cyan

# Obtener todos los archivos Java
$javaFiles = Get-ChildItem -Path $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }

# Construir classpath
$classpath = "$apiJar;$vaultJar"
if (Test-Path $bluemapJar) {
    $classpath += ";$bluemapJar"
    Write-Host "BlueMap API encontrada: $bluemapJar" -ForegroundColor Green
} else {
    Write-Host "BlueMap API no encontrada (opcional). Descargando..." -ForegroundColor Yellow
    $bluemapUrl = "https://repo.bluecolored.de/releases/de/bluecolored/bluemap-api/2.7.7/bluemap-api-2.7.7.jar"
    try {
        Invoke-WebRequest -Uri $bluemapUrl -OutFile $bluemapJar
        $classpath += ";$bluemapJar"
        Write-Host "BlueMap API descargada correctamente." -ForegroundColor Green
    } catch {
        Write-Host "No se pudo descargar BlueMap API. La integracion con BlueMap no estara disponible." -ForegroundColor Yellow
    }
}

# Crear archivo de lista de fuentes (encoding ASCII para evitar BOM issues en javac list)
$sourceListFile = "$buildDir\sources.txt"
[System.IO.File]::WriteAllLines($sourceListFile, $javaFiles)

# Encontrar javac y deducir ruta de jar para asegurar misma version (JDK 21)
$javacPath = (Get-Command javac).Source
Write-Host "Usando javac en: $javacPath" -ForegroundColor Gray
$jarPath = $javacPath.Replace("javac.exe", "jar.exe")

if (-not (Test-Path $jarPath)) {
    Write-Host "No se encontro jar.exe junto a javac. Intentando buscar en PATH..." -ForegroundColor Yellow
    $jarPath = "jar"
} else {
    Write-Host "Usando jar en: $jarPath" -ForegroundColor Green
}

# Compilar
& $javacPath -encoding UTF-8 -source 21 -target 21 -cp $classpath -d $classesDir "@$sourceListFile" 2>&1 | Out-String

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error de compilacion!" -ForegroundColor Red
    exit 1
}

Write-Host "Copiando y procesando recursos..." -ForegroundColor Cyan

# Copiar recursos manualmente para asegurar encoding
Copy-Item -Path "$resourcesDir\*" -Destination $classesDir -Recurse -Force

# Reemplazar version en plugin.yml asegurando UTF-8 (Sin BOM)
$pluginYmlPath = "$classesDir\plugin.yml"
$pluginYmlContent = [System.IO.File]::ReadAllText($pluginYmlPath)
$pluginYmlContent = $pluginYmlContent -replace '\$\{project\.version\}', '1.0.0'
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($pluginYmlPath, $pluginYmlContent, $utf8NoBom)

Write-Host "Creando JAR..." -ForegroundColor Cyan

# Borrar archivo anterior si existe
if (Test-Path $outputJar) { Remove-Item $outputJar -Force }

# Crear JAR usando Compress-Archive
# Esta herramienta de PowerShell crea ZIPs estandares SIN los campos "Extended Timestamp" (EXTT)
# que causan el error ZipException: R0 en el remapper de PaperMC.
$tempZip = "$buildDir\temp_artifact.zip"
if (Test-Path $tempZip) { Remove-Item $tempZip -Force }

Push-Location $classesDir
Try {
    Compress-Archive -Path * -DestinationPath $tempZip -Force -ErrorAction Stop
} Catch {
    Write-Host "Error al comprimir: $_" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

# Renombrar a .jar
Move-Item -Path $tempZip -Destination $outputJar -Force

if (Test-Path $outputJar) {
    $fileInfo = Get-Item $outputJar
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Compilacion Clean (SafeZip) exitosa!" -ForegroundColor Green
    Write-Host "  JAR: $outputJar" -ForegroundColor Yellow
    Write-Host "  Tamano: $([math]::Round($fileInfo.Length / 1KB, 2)) KB" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "Error al crear el JAR: No se encuentra el archivo de salida" -ForegroundColor Red
    exit 1
}
