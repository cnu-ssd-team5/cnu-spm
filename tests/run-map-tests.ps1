param([string]$JdkBin = '')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$JdkBin -and $env:JAVA_HOME) {
    $JdkBin = Join-Path $env:JAVA_HOME 'bin'
}
$javac = if ($JdkBin) { Join-Path $JdkBin 'javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$java = if ($JdkBin) { Join-Path $JdkBin 'java.exe' } else { (Get-Command java -ErrorAction Stop).Source }

$workRoot = Join-Path $projectRoot 'build/map-refactor'
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
$classes = Join-Path $workRoot ('classes-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
$sources += Join-Path $PSScriptRoot 'campus/map/MapRegressionTest.java'
& $javac --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw '지도 검사 컴파일 실패' }

$classPath = $classes + [IO.Path]::PathSeparator + (Join-Path $projectRoot 'resources')
& $java -ea '-Djava.awt.headless=true' -cp $classPath campus.map.MapRegressionTest $projectRoot (Join-Path $workRoot 'after.txt')
if ($LASTEXITCODE -ne 0) { throw '지도 회귀 검사 실패' }
