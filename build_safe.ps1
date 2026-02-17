# Script de construccion seguro para evitar ZipException: R0
# Usa .NET directamente para asegurar que no se escriben metadatos EXTT

$ErrorActionPreference = "Continue"

$projectDir = $PSScriptRoot
$srcDir = "$projectDir\src\main\java"
$resourcesDir = "$projectDir\src\main\resources"
$buildDir = "$projectDir\build"
$classesDir = "$buildDir\classes"
$outputJar = "$buildDir\Wardstone-1.0.0.jar"

# 1. Limpieza
if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path "$projectDir\libs" | Out-Null

# 2. Compilacion (Usando javac del sistema)
$javac = "javac"
if (Get-Command "javac" -ErrorAction SilentlyContinue) {
    $javac = (Get-Command "javac").Source
}

Write-Host "Compilando con $javac ..." -ForegroundColor Cyan

# Lista de fuentes
$javaFiles = Get-ChildItem -Path $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$sourceList = "$buildDir\sources.txt"
[System.IO.File]::WriteAllLines($sourceList, $javaFiles)

# Dependencias
$classpath = "$projectDir\libs\spigot-api.jar;$projectDir\libs\vault-api.jar"

# Javac execution
$logFile = "$buildDir\build_log.txt"
try {
    & $javac -encoding UTF-8 -source 21 -target 21 -cp "$classpath" -d "$classesDir" "@$sourceList" 2>&1 | Out-File -FilePath $logFile -Encoding UTF8
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error en compilacion. Ver log:" -ForegroundColor Red
        Get-Content $logFile | Select-Object -Last 20
        exit 1
    } else {
        Write-Host "Compilacion exitosa. Warnings:" -ForegroundColor Yellow
        Get-Content $logFile | Select-Object -First 5
    }
} catch {
    Write-Host "Excepcion critica: $_" -ForegroundColor Red
    exit 1
}

# 3. Copiar recursos
Copy-Item -Path "$resourcesDir\*" -Destination $classesDir -Recurse -Force
# Fix plugin.yml version
$yamlPath = "$classesDir\plugin.yml"
$content = [System.IO.File]::ReadAllText($yamlPath)
[System.IO.File]::WriteAllText($yamlPath, $content.Replace('${project.version}', '1.0.0'))

# 4. Crear ZIP .NET puro sin metadatos extendidos
Write-Host "Creando JAR limpio con .NET..." -ForegroundColor Cyan

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = $outputJar

$compressionLevel = [System.IO.Compression.CompressionLevel]::Optimal
$includeBaseDirectory = $false

# Funcion para zip recursivo con normalizacion de tiempo
function Add-FilesToZip($zipArchive, $sourcePath, $relativePath) {
    foreach ($file in Get-ChildItem $sourcePath) {
        if ($file.PSIsContainer) {
             # Crear entrada de directorio (opcional en zip, pero buena practica)
             # $zipArchive.CreateEntry($relativePath + $file.Name + "/") | Out-Null
             Add-FilesToZip $zipArchive $file.FullName ($relativePath + $file.Name + "/")
        } else {
             $entryName = $relativePath + $file.Name
             $entry = $zipArchive.CreateEntry($entryName, $compressionLevel)
             $entry.LastWriteTime = [DateTimeOffset]::new(2024, 1, 1, 0, 0, 0, [TimeSpan]::Zero) # Normalizar tiempo
             
             # Copiar contenido stream
             $stream = $entry.Open()
             $fileStream = [System.IO.File]::OpenRead($file.FullName)
             $fileStream.CopyTo($stream)
             $fileStream.Close()
             $stream.Close()
        }
    }
}

$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
Add-FilesToZip $zip $classesDir ""
$zip.Dispose()

Write-Host "Hecho. JAR en: $outputJar" -ForegroundColor Green
Write-Host "Tamano: $( (Get-Item $outputJar).Length ) bytes" -ForegroundColor Yellow
