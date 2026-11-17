param([string] $JdkBin = '')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try {
    if (!$JdkBin) {
        $candidates = @()
        if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin') }
        $candidates += $env:PATH -split [IO.Path]::PathSeparator
        $JdkBin = $candidates | Where-Object {
            $_ -and $_ -notmatch '(?i)Oracle[\\/]Java[\\/]javapath' -and
            (Test-Path -LiteralPath (Join-Path $_ 'javac.exe')) -and
            (Test-Path -LiteralPath (Join-Path $_ 'java.exe'))
        } | Select-Object -First 1
    }
    if (!$JdkBin) { throw 'JDK 17 이상이 필요합니다. -JdkBin으로 JDK의 bin 폴더를 지정할 수 있습니다.' }
    $output = Join-Path $PSScriptRoot 'build/checks'
    [void] [IO.Directory]::CreateDirectory($output)
    $sources = @(Get-ChildItem -Path (Join-Path $PSScriptRoot 'src/campus/*.java'),
        (Join-Path $PSScriptRoot 'test/campus/*.java') -File | ForEach-Object FullName)
    & (Join-Path $JdkBin 'javac.exe') --release 17 -encoding UTF-8 -d $output @sources
    if ($LASTEXITCODE -ne 0) { throw '검사 소스 컴파일에 실패했습니다.' }
    foreach ($name in @('PlannerModelCheck', 'SemesterCheck', 'PlannerTimeCheck', 'TimePickerCheck',
            'SecondaryViewsCheck', 'PlacesCheck', 'MaterialsDropCheck', 'DemoIntegrationCheck', 'DemoGroupCheck', 'PlannerIntegrationCheck')) {
        & (Join-Path $JdkBin 'java.exe') -cp $output ('campus.' + $name) $PSScriptRoot
        if ($LASTEXITCODE -ne 0) { throw ($name + ' 검사에 실패했습니다.') }
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
