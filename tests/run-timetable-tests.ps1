param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot 'build/timetable-tests'
[void][IO.Directory]::CreateDirectory($outputDir)
$compiler = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac.exe -ErrorAction Stop).Source }
$runtime = Join-Path (Split-Path -Parent $compiler) 'java.exe'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' -File | ForEach-Object FullName)
& $compiler --release 17 -encoding UTF-8 -d $outputDir @sources (Join-Path $PSScriptRoot 'campus/timetable/CalendarDragTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $runtime '-Djava.awt.headless=true' -cp $outputDir campus.timetable.CalendarDragTest (Join-Path $projectRoot 'resources/map/chonnam-campus.osm') (Join-Path $outputDir 'test-state.xml')
exit $LASTEXITCODE
