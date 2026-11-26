param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $projectRoot ('build/whitebox-qa/' + $stamp)
[void][IO.Directory]::CreateDirectory($outputDir)
$compiler = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac.exe -ErrorAction Stop).Source }
$runtime = Join-Path (Split-Path -Parent $compiler) 'java.exe'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src'), $PSScriptRoot -Recurse -Filter '*.java' -File | ForEach-Object FullName)
& $compiler --release 17 -encoding UTF-8 -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'resources') | Copy-Item -Destination $outputDir -Recurse -Force
$map = Join-Path $projectRoot 'resources/map/chonnam-campus.osm'
$failures = 0
foreach ($suite in @('campus.timetable.CalendarDragTest','campus.ui.CalendarIntegrationTest','campus.ui.MapViewportTest','campus.timetable.TimetableEditorTest','campus.ui.ScheduleEntryWhiteBoxTest','campus.ui.CalendarDeadlineTest','campus.ui.WorkspaceMaterialsTest','campus.ui.WorkspaceLayoutTest','campus.ui.PlaceLinkingTest','campus.ui.TimeReminderTest','campus.ui.WeekBarRegressionTest','campus.ui.RouteTimingRegressionTest','campus.AppPathsTest')) {
    $headless = if ($suite -in @('campus.timetable.TimetableEditorTest','campus.ui.ScheduleEntryWhiteBoxTest','campus.ui.WorkspaceMaterialsTest','campus.ui.WorkspaceLayoutTest','campus.ui.PlaceLinkingTest')) { 'false' } else { 'true' }
    & $runtime ('-Djava.awt.headless=' + $headless) -cp $outputDir $suite $map (Join-Path $outputDir 'test-state.xml') 2>&1 | Tee-Object -FilePath (Join-Path $outputDir ($suite + '.log'))
    if ($LASTEXITCODE -ne 0) { $failures++ }
}
Write-Host ('QA logs: ' + $outputDir)
if ($failures -gt 0) { Write-Error ($failures.ToString() + ' test suites failed'); exit 1 }
Write-Host 'All white-box QA suites passed.'
