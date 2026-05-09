# Script para compilar CoreProtect (Version 1.21.x)
# Requiere Java 21+ instalado

$ErrorActionPreference = "Continue"

$projectDir = $PSScriptRoot
$srcDir = "$projectDir\src\main\java"
$resourcesDir = "$projectDir\src\main\resources"
$buildDir = "$projectDir\build"
$classesDir = "$buildDir\classes"
$libDir = "$projectDir\libs"
$outputJar = "$buildDir\Wardstone-1.0.0.jar"

# Limpieza completa e inicializacion
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
if (-not (Test-Path $libDir)) { New-Item -ItemType Directory -Force -Path $libDir | Out-Null }

Write-Host "Verificando dependencias..." -ForegroundColor Cyan

$placeholderJar = "$libDir\placeholderapi.jar"
if (-not (Test-Path $placeholderJar)) {
    Write-Host "Descargando PlaceholderAPI..." -ForegroundColor Yellow
    # Usando el link de HelpChat que suele ser mas estable para descargas programaticas
    $papiUrl = "https://repo.helpch.at/repository/public/me/clip/placeholderapi/2.11.6/placeholderapi-2.11.6.jar"
    try {
        Invoke-WebRequest -Uri $papiUrl -OutFile $placeholderJar -TimeoutSec 60
        Write-Host "PlaceholderAPI descargada correctamente." -ForegroundColor Green
    } catch {
        Write-Host "Error al descargar: $($_.Exception.Message)" -ForegroundColor Red
        # Intentar mirror de GitHub como respaldo
        Write-Host "Intentando mirror de GitHub..." -ForegroundColor Yellow
        try {
            # GitHub requiere -k si hay problemas de SSL en el entorno del usuario
            curl.exe -k -L -o $placeholderJar https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.11.6/PlaceholderAPI-2.11.6.jar
            Write-Host "Mirror de GitHub funciono." -ForegroundColor Green
        } catch {
            Write-Host "No se pudo descargar PlaceholderAPI. Los placeholders fallaran." -ForegroundColor Red
        }
    }
}

# Construir classpath con TODOS los jars en libs
$jars = Get-ChildItem -Path $libDir -Filter "*.jar"
$classpathParts = @()
foreach ($jar in $jars) {
    if ($jar.Length -gt 1024) { # Evitar jars corruptos de intentos fallidos previos
        $classpathParts += $jar.FullName
    }
}
$classpath = $classpathParts -join ";"

Write-Host "Compilando..." -ForegroundColor Cyan

# Obtener todos los archivos Java
$javaFiles = Get-ChildItem -Path $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }

$sourceListFile = "$buildDir\sources.txt"
[System.IO.File]::WriteAllLines($sourceListFile, $javaFiles)

$javacPath = (Get-Command javac).Source
& $javacPath -encoding UTF-8 -source 21 -target 21 -cp $classpath -d $classesDir "@$sourceListFile"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error de compilacion!" -ForegroundColor Red
    exit 1
}

Write-Host "Creando JAR..." -ForegroundColor Cyan
Copy-Item -Path "$resourcesDir\*" -Destination $classesDir -Recurse -Force

# Shade JDA + dependencies into the JAR
$jdaDepsDir = "$libDir\jda-deps"
if (-not (Test-Path $jdaDepsDir)) { New-Item -ItemType Directory -Force -Path $jdaDepsDir | Out-Null }

$jdaDeps = @{
    "jda" = "$libDir\jda.jar"
    "nv-websocket-client" = "$jdaDepsDir\nv-websocket-client-2.14.jar"
    "okhttp" = "$jdaDepsDir\okhttp-4.12.0.jar"
    "okio" = "$jdaDepsDir\okio-jvm-3.6.0.jar"
    "kotlin-stdlib" = "$jdaDepsDir\kotlin-stdlib-1.9.10.jar"
    "kotlin-stdlib-jdk8" = "$jdaDepsDir\kotlin-stdlib-jdk8-1.9.10.jar"
    "kotlin-stdlib-jdk7" = "$jdaDepsDir\kotlin-stdlib-jdk7-1.9.10.jar"
    "commons-collections4" = "$jdaDepsDir\commons-collections4-4.4.jar"
    "trove4j" = "$jdaDepsDir\trove4j-3.0.3.jar"
    "jackson-core" = "$jdaDepsDir\jackson-core-2.16.0.jar"
    "jackson-databind" = "$jdaDepsDir\jackson-databind-2.16.0.jar"
    "jackson-annotations" = "$jdaDepsDir\jackson-annotations-2.16.0.jar"
}
$jdaDepUrls = @{
    "nv-websocket-client" = "https://repo1.maven.org/maven2/com/neovisionaries/nv-websocket-client/2.14/nv-websocket-client-2.14.jar"
    "okhttp" = "https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar"
    "okio" = "https://repo1.maven.org/maven2/com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar"
    "kotlin-stdlib" = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.10/kotlin-stdlib-1.9.10.jar"
    "kotlin-stdlib-jdk8" = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.9.10/kotlin-stdlib-jdk8-1.9.10.jar"
    "kotlin-stdlib-jdk7" = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.9.10/kotlin-stdlib-jdk7-1.9.10.jar"
    "commons-collections4" = "https://repo1.maven.org/maven2/org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar"
    "trove4j" = "https://repo1.maven.org/maven2/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar"
    "jackson-core" = "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.16.0/jackson-core-2.16.0.jar"
    "jackson-databind" = "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.16.0/jackson-databind-2.16.0.jar"
    "jackson-annotations" = "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.16.0/jackson-annotations-2.16.0.jar"
}

# Download missing JDA dependencies
foreach ($dep in $jdaDepUrls.Keys) {
    $depPath = $jdaDeps[$dep]
    if (-not (Test-Path $depPath)) {
        Write-Host "Descargando $dep..." -ForegroundColor Yellow
        try {
            Invoke-WebRequest -Uri $jdaDepUrls[$dep] -OutFile $depPath -TimeoutSec 60
        } catch {
            Write-Host "Error descargando ${dep}: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Extract all JDA jars into classesDir
$jarExtract = (Get-Command jar -ErrorAction SilentlyContinue).Source
if (-not $jarExtract) { $jarExtract = (Get-Command javac).Source.Replace("javac.exe", "jar.exe") }
Write-Host "Incluyendo JDA y dependencias en el JAR..." -ForegroundColor Yellow
Push-Location $classesDir
foreach ($dep in $jdaDeps.Keys) {
    $depPath = $jdaDeps[$dep]
    if (Test-Path $depPath) {
        & $jarExtract xf $depPath 2>$null
    }
}
Pop-Location
# Clean conflicting META-INF entries (keep only our plugin.yml)
$metaMaven = "$classesDir\META-INF\maven"
$metaServices = "$classesDir\META-INF\services"
$metaVersions = "$classesDir\META-INF\versions"
if (Test-Path $metaMaven) { Remove-Item $metaMaven -Recurse -Force }
if (Test-Path $metaVersions) { Remove-Item $metaVersions -Recurse -Force }
Write-Host "JDA y dependencias incluidas correctamente." -ForegroundColor Green

# Jar command
$jarTool = (Get-Command jar -ErrorAction SilentlyContinue).Source
if (-not $jarTool) {
    # Fallback si no esta en el PATH
    $jarTool = (Get-Command javac).Source.Replace("javac.exe", "jar.exe")
}

Write-Host "Creando JAR usando: $jarTool" -ForegroundColor Gray
Push-Location $classesDir
& $jarTool cf $outputJar *
Pop-Location

if (Test-Path $outputJar) {
    Write-Host "JAR creado correctamente: $outputJar" -ForegroundColor Green
    $size = (Get-Item $outputJar).Length / 1KB
    Write-Host "Tamano: $('{0:N2}' -f $size) KB" -ForegroundColor Gray
} else {
    Write-Host "Error critico: No se pudo crear el archivo JAR." -ForegroundColor Red
    exit 1
}
