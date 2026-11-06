param(
    [ValidateSet('demo', 'test', 'benchmark', 'all', 'compile')]
    [string]$Command = 'demo',
    [string]$OutputDirectory = 'results'
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Prefer the explicitly selected JDK, then PATH, then a locally installed JDK.
$compiler = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    if (Test-Path -LiteralPath $candidate) { $compiler = $candidate }
}
if (-not $compiler) {
    $available = Get-Command javac -ErrorAction SilentlyContinue
    if ($available) { $compiler = $available.Source }
}
if (-not $compiler) {
    $jdkFolder = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkFolder) {
        $compiler = Get-ChildItem -LiteralPath $jdkFolder -Directory |
            Sort-Object LastWriteTime -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\javac.exe' } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
    }
}
if (-not $compiler) { throw 'JDK 17 or newer is required. Set JAVA_HOME to its installation folder.' }
$java = Join-Path (Split-Path -Parent $compiler) 'java.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'java.exe is missing beside javac.exe.' }

New-Item -ItemType Directory -Force -Path 'build\classes' | Out-Null
$sources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' -Recurse |
    Sort-Object FullName | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
$sourceList = Join-Path $PSScriptRoot 'build\sources.txt'
[System.IO.File]::WriteAllLines($sourceList, $sources, (New-Object System.Text.UTF8Encoding($false)))
& $compiler --release 17 -encoding UTF-8 -Xlint:all -d 'build/classes' ('@' + $sourceList)
if ($LASTEXITCODE -ne 0) { throw "Compilation failed (exit $LASTEXITCODE)." }

function Invoke-JavaClass([string]$ClassName, [string[]]$ClassArguments = @()) {
    & $java '-Djava.awt.headless=true' -cp 'build/classes' $ClassName @ClassArguments
    if ($LASTEXITCODE -ne 0) { throw "$ClassName failed (exit $LASTEXITCODE)." }
}

switch ($Command) {
    'demo'      { Invoke-JavaClass 'textanalysis.Main' }
    'test'      { Invoke-JavaClass 'textanalysis.TestSuite' }
    'benchmark' { Invoke-JavaClass 'textanalysis.experiments.Benchmark' @($OutputDirectory) }
    'all' {
        Invoke-JavaClass 'textanalysis.TestSuite'
        Invoke-JavaClass 'textanalysis.Main'
        Invoke-JavaClass 'textanalysis.experiments.Benchmark' @($OutputDirectory)
    }
    'compile' { Write-Output 'Compilation successful (Java 17 target).' }
}
