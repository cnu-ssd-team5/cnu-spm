Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    $projectRoot = [IO.Path]::GetFullPath($PSScriptRoot)
    $candidateBins = [Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidateBins.Add((Join-Path $env:JAVA_HOME 'bin'))
    }
    foreach ($entry in ($env:PATH -split [IO.Path]::PathSeparator)) {
        $bin = $entry.Trim().Trim('"')
        if ($bin -and $bin -notmatch '(?i)[\\/]Oracle[\\/]Java[\\/]javapath(?:[\\/]|$)') {
            $candidateBins.Add($bin)
        }
    }

    $jdkBin = $null
    foreach ($bin in ($candidateBins | Select-Object -Unique)) {
        $complete = $true
        foreach ($tool in @('java.exe', 'javac.exe', 'jar.exe', 'jlink.exe', 'jpackage.exe')) {
            if (![IO.File]::Exists([IO.Path]::Combine($bin, $tool))) {
                $complete = $false
                break
            }
        }
        if (!$complete) {
            continue
        }
        $compiler = Join-Path $bin 'javac.exe'
        $compilerVersion = (& $compiler -version 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -eq 0 -and $compilerVersion -match 'javac\s+(?:1\.)?(\d+)' -and
            [int] $Matches[1] -ge 17) {
            $jdkBin = $bin
            break
        }
    }
    if (!$jdkBin) {
        throw '패키지를 만들 때는 jpackage를 포함한 JDK 17 이상이 필요합니다.'
    }

    $stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6)
    $work = Join-Path $projectRoot ('build/package-' + $stamp + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    $classes = Join-Path $work 'classes'
    $input = Join-Path $work 'input'
    $runtime = Join-Path $work 'runtime'
    $dist = Join-Path $projectRoot 'dist'
    $releaseRoot = $dist
    if (Test-Path -LiteralPath (Join-Path $releaseRoot 'CNU-Schedule')) {
        $releaseRoot = Join-Path $dist ('export-' + $stamp)
    }
    [void] [IO.Directory]::CreateDirectory($classes)
    [void] [IO.Directory]::CreateDirectory($input)
    [void] [IO.Directory]::CreateDirectory($releaseRoot)

    $sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -File -Recurse |
        ForEach-Object { $_.FullName })
    if ($sources.Count -eq 0) {
        throw 'Java 소스 파일을 찾을 수 없습니다.'
    }
    Write-Host '1/4 Java 소스를 컴파일합니다.'
    & (Join-Path $jdkBin 'javac.exe') '--release' '17' '-encoding' 'UTF-8' '-d' $classes @sources
    if ($LASTEXITCODE -ne 0) {
        throw '컴파일에 실패했습니다.'
    }

    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'resources') | Copy-Item -Destination $classes -Recurse -Force
    $jar = Join-Path $input 'campus-schedule.jar'
    & (Join-Path $jdkBin 'jar.exe') '--create' '--file' $jar '--main-class' 'campus.App' '-C' $classes '.'
    if ($LASTEXITCODE -ne 0) {
        throw '실행 JAR 파일을 만들지 못했습니다.'
    }
    Write-Host '2/4 실행에 필요한 Java 런타임을 포함합니다.'
    $linkArguments = @('--add-modules', 'java.desktop,java.xml,jdk.localedata', '--include-locales=ko,en',
        '--strip-debug', '--no-header-files', '--no-man-pages', '--output', $runtime)
    & (Join-Path $jdkBin 'jlink.exe') @linkArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Java 런타임을 만들지 못했습니다.'
    }

    Write-Host '3/4 Windows 실행 파일을 만듭니다.'
    # Build in the OS temporary directory, then publish a completed app image.
    # Some managed Windows workspaces fail jpackage's Files.isWritable check even when actual writes work.
    $packageStage = [IO.Path]::Combine([IO.Path]::GetTempPath(), 'cnu-package-' + [Guid]::NewGuid().ToString('N'))
    [void] [IO.Directory]::CreateDirectory($packageStage)
    $packageArguments = @('--type', 'app-image', '--name', 'CNU-Schedule', '--app-version', '1.4',
        '--input', $input, '--main-jar', 'campus-schedule.jar', '--main-class', 'campus.App',
        '--runtime-image', $runtime, '--dest', $packageStage)
    & (Join-Path $jdkBin 'jpackage.exe') @packageArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Windows 앱을 만들지 못했습니다.'
    }

    $application = Join-Path $releaseRoot 'CNU-Schedule'
    if (Test-Path -LiteralPath $application) { throw ('출력 폴더가 이미 있습니다: ' + $application) }
    Copy-Item -LiteralPath (Join-Path $packageStage 'CNU-Schedule') -Destination $releaseRoot -Recurse
    $launcher = Join-Path $application 'CNU-Schedule.exe'
    if (!(Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw '만들어진 Windows 실행 파일을 찾지 못했습니다.'
    }
    foreach ($privatePath in @('data/planner-state.xml', 'app/data/planner-state.xml', 'materials')) {
        if (Test-Path -LiteralPath (Join-Path $application $privatePath)) {
            throw ('배포 폴더에 개인 데이터가 포함되어 있습니다: ' + $privatePath)
        }
    }

    Copy-Item -LiteralPath (Join-Path $projectRoot 'resources/map/README.md') -Destination (Join-Path $application 'OSM-SOURCES.md')
    [void] [IO.Directory]::CreateDirectory((Join-Path $application 'resources'))
    Copy-Item -LiteralPath (Join-Path $projectRoot 'resources/map') -Destination (Join-Path $application 'resources') -Recurse
    $instructions = @'
전남대학교 일정 — Windows 실행본

1. ZIP 파일을 우클릭하고 "모두 압축 풀기"를 선택합니다.
2. 압축을 푼 CNU-Schedule 폴더 안에서 CNU-Schedule.exe를 실행합니다.
3. Java나 JDK를 따로 설치할 필요가 없습니다.

CNU-Schedule 폴더 전체를 함께 보관하거나 전달해 주세요.
실행 파일 옆의 app, runtime 폴더도 필요합니다.

새 실행본에는 개인 일정이 들어 있지 않습니다.
기존 앱에서 "공유하기"로 저장한 XML 파일을 새 앱의 "가져오기"로 불러올 수 있습니다.
개인 일정과 자료는 실행 파일 옆 data 폴더에 저장됩니다.
과목 자료: data/buildings/건물명/강의명
자료 백업 시 data 폴더 전체를 복사하세요. 일정 공유 XML에는 실제 자료 파일이 들어 있지 않습니다.
쓰기 가능한 폴더(문서, 바탕화면 등)에 압축을 풀어 사용해 주세요.

OSM 지도 출처와 라이선스는 OSM-SOURCES.md에 있습니다.
'@
    [IO.File]::WriteAllText((Join-Path $application '사용방법.txt'), $instructions, [Text.UTF8Encoding]::new($true))

    Write-Host '4/4 공유할 ZIP 파일을 만듭니다.'
    $archive = Join-Path $dist 'CNU-Schedule-Windows.zip'
    if (Test-Path -LiteralPath $archive) {
        $archive = Join-Path $dist ('CNU-Schedule-Windows-' + $stamp + '.zip')
    }
    Compress-Archive -LiteralPath $application -DestinationPath $archive -CompressionLevel Optimal
    Write-Host ''
    Write-Host ('실행 파일: ' + $launcher)
    Write-Host ('배포 ZIP: ' + $archive)
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
