param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $AppArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    $projectRoot = $PSScriptRoot
    $candidateBins = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidateBins.Add((Join-Path $env:JAVA_HOME 'bin'))
    }
    foreach ($entry in ($env:PATH -split [IO.Path]::PathSeparator)) {
        $bin = $entry.Trim().Trim('"')
        if ($bin -and $bin -notmatch '(?i)[\\/]Oracle[\\/]Java[\\/]javapath(?:[\\/]|$)') {
            $candidateBins.Add($bin)
        }
    }

    $java = $null
    $javac = $null
    foreach ($bin in ($candidateBins | Select-Object -Unique)) {
        $runtime = Join-Path $bin 'java.exe'
        $compiler = Join-Path $bin 'javac.exe'
        if (!(Test-Path -LiteralPath $runtime -PathType Leaf) -or
            !(Test-Path -LiteralPath $compiler -PathType Leaf)) {
            continue
        }
        $compilerVersion = (& $compiler -version 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $compilerVersion -match 'javac\s+(?:1\.)?(\d+)' -and
            [int] $Matches[1] -ge 17) {
            $java = $runtime
            $javac = $compiler
            break
        }
    }
    if (!$java) {
        throw 'JDK 17 이상이 필요합니다. JDK를 설치하고 JAVA_HOME을 JDK 폴더로 지정하거나 해당 bin 폴더를 PATH에 추가해 주세요.'
    }

    $classDir = Join-Path $projectRoot 'build/classes'
    [void] [IO.Directory]::CreateDirectory($classDir)
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/campus') -Filter '*.java' -File |
        ForEach-Object { $_.FullName })
    if ($sources.Count -eq 0) {
        throw 'src/campus 폴더에서 Java 소스 파일을 찾을 수 없습니다.'
    }
    Write-Host '전남대 캠퍼스 플래너를 준비하고 있습니다...'
    & $javac '--release' '17' '-encoding' 'UTF-8' '-d' $classDir @sources
    if ($LASTEXITCODE -ne 0) {
        throw 'Java 컴파일에 실패했습니다. 위 오류를 확인해 주세요.'
    }

    & $java "-Dcampus.home=$projectRoot" '-cp' $classDir 'campus.CampusPlanner' @AppArguments
    exit $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
