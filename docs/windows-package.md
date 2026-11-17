# Windows 실행본

최신 **1.3 실행본**은 [CNU-Schedule-Windows-20261117-102216.zip](../dist/CNU-Schedule-Windows-20261117-102216.zip)입니다. ZIP 전체를 압축 해제한 다음 `CNU-Schedule/CNU-Schedule.exe`를 실행합니다. Java 런타임은 실행본에 포함되어 있어 따로 설치할 필요가 없습니다.

이전 `dist/CNU-Schedule-Windows.zip`과 기존 실행 폴더는 보존했습니다. 새 기능은 위 1.3 실행본에서 사용할 수 있습니다. 압축을 풀기 전 실행 파일은 `dist/export-20261117-102216/CNU-Schedule/CNU-Schedule.exe`에 있습니다.

`CNU-Schedule.exe`와 `app`, `runtime` 폴더를 함께 보관합니다. 개인 일정은 실행본 옆의 `data/planner-state.xml`에, 과목 자료는 `materials`에 저장됩니다. 문서나 바탕화면처럼 쓰기 가능한 위치에서 사용합니다.

새 실행본에는 개인 일정과 과목 자료가 포함되지 않습니다. 기존 앱에서 공유 XML을 저장한 다음 새 앱의 가져오기로 불러올 수 있습니다.

개발자가 실행본을 다시 만들 때는 JDK 17 이상에서 다음 명령을 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\package.ps1
```

패키징은 원본 OSM 지도와 컴파일한 앱만 포함합니다. 기존 배포 폴더가 있으면 날짜가 붙은 새 출력 위치를 사용합니다. 이전 실행본과 사용자 자료를 삭제하지 않습니다.

