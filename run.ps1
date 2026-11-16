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
        $candidateBins.Add([IO.Path]::Combine($env:JAVA_HOME.Trim().Trim('"'), 'bin'))
    }
    $oracleShims = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in ($env:PATH -split [IO.Path]::PathSeparator)) {
        $bin = $entry.Trim().Trim('"')
        if (!$bin) { continue }
        if ($bin -match '(?i)[\\/]Oracle[\\/]Java[\\/]javapath(?:[\\/]|$)') {
            $oracleShims.Add($bin)
        } else {
            $candidateBins.Add($bin)
        }
    }
    # JAVA_HOME이나 PATH에 JDK가 없으면 흔한 JDK 설치 폴더를 최신 버전부터 찾아봅니다.
    if ($env:ProgramFiles) {
        foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Zulu', 'Amazon Corretto', 'BellSoft')) {
            $root = [IO.Path]::Combine($env:ProgramFiles, $vendor)
            if ([IO.Directory]::Exists($root)) {
                Get-ChildItem -LiteralPath $root -Directory | Sort-Object Name -Descending |
                    ForEach-Object { $candidateBins.Add([IO.Path]::Combine($_.FullName, 'bin')) }
            }
        }
    }
    # Oracle 설치 프로그램이 PATH에 넣는 javapath 실행 도우미는 마지막 수단으로만 사용합니다.
    foreach ($shim in $oracleShims) { $candidateBins.Add($shim) }

    $java = $null
    $javac = $null
    foreach ($bin in ($candidateBins | Select-Object -Unique)) {
        # PATH에는 없는 드라이브나 잘못된 경로가 섞여 있을 수 있습니다.
        # Join-Path는 없는 드라이브에서 오류를 내므로 .NET 메서드로 확인하고, 문제가 있는 항목은 건너뜁니다.
        try {
            $runtime = [IO.Path]::Combine($bin, 'java.exe')
            $compiler = [IO.Path]::Combine($bin, 'javac.exe')
            if (![IO.File]::Exists($runtime) -or ![IO.File]::Exists($compiler)) {
                continue
            }
        } catch {
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
    # 이전 빌드의 클래스 파일(옮기거나 지운 클래스)이 남지 않도록 매번 새로 만듭니다.
    if (Test-Path -LiteralPath $classDir) {
        Remove-Item -LiteralPath $classDir -Recurse -Force
    }
    [void] [IO.Directory]::CreateDirectory($classDir)
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -File -Recurse |
        ForEach-Object { $_.FullName })
    if ($sources.Count -eq 0) {
        throw 'src 폴더에서 Java 소스 파일을 찾을 수 없습니다.'
    }
    Write-Host '전남대 캠퍼스 플래너를 준비하고 있습니다...'
    & $javac '--release' '17' '-encoding' 'UTF-8' '-d' $classDir @sources
    if ($LASTEXITCODE -ne 0) {
        throw 'Java 컴파일에 실패했습니다. 위 오류를 확인해 주세요.'
    }
    # 지도 데이터 등 resources 폴더의 파일을 클래스 경로에 함께 둡니다.
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'resources') |
        Copy-Item -Destination $classDir -Recurse -Force

    & $java "-Dcampus.home=$projectRoot" '-cp' $classDir 'campus.App' @AppArguments
    exit $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
