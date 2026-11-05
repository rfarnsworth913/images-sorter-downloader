param([switch]$Run, [switch]$Test)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Oracle's Windows PATH shims may expose javac/java without exposing jar.
    $ErrorActionPreference = 'Continue'
    $settings = & java -XshowSettings:properties -version 2>&1
    $ErrorActionPreference = 'Stop'
    $homeLine = $settings | ForEach-Object { "$_" } | Where-Object { $_ -match '^\s*java.home\s*=' } | Select-Object -First 1
    if (-not $homeLine) { throw 'Could not locate the Java installation. Install JDK 17 or newer.' }
    $jdkDirectory = ($homeLine -split '=', 2)[1].Trim()
    $jarTool = Join-Path $jdkDirectory 'bin/jar.exe'
    if (-not (Test-Path $jarTool)) { throw 'The Java installation does not include jar.exe. Install a full JDK.' }
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = @(Get-ChildItem src/main/java -Recurse -Filter *.java | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -d build/classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    & $jarTool --create --file build/images-sorter-downloader.jar --main-class com.imagesorter.App -C build/classes .
    if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
    if ($Test) {
        $tests = @(Get-ChildItem src/test/java -Recurse -Filter *.java | ForEach-Object FullName)
        & javac --release 17 -encoding UTF-8 --add-modules jdk.httpserver -cp build/classes -d build/test-classes @tests
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        & java --add-modules jdk.httpserver -cp 'build/classes;build/test-classes' com.imagesorter.ServiceSmokeTest
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
    }
    if ($Run) { & java -jar build/images-sorter-downloader.jar }
} finally {
    Pop-Location
}
