# 전남대 캠퍼스 플래너

전남대학교 광주캠퍼스의 OpenStreetMap 데이터를 사용하는 Java Swing 데스크톱 앱입니다. 주간 고정 일정에 날짜별 일정을 더하고, 선택한 하루의 장소와 이동 경로를 지도에서 확인할 수 있습니다.

## 실행

JDK 17 이상이 필요합니다. `JAVA_HOME`을 JDK 설치 폴더로 지정하거나 JDK의 `bin` 폴더를 `PATH`에 추가합니다. 외부 Java 라이브러리와 별도 빌드 도구는 필요하지 않습니다.

Windows에서 `run.cmd`를 더블클릭합니다. PowerShell에서는 다음 명령으로 실행합니다.

```powershell
.\run.ps1
```

실행 스크립트는 `src` 아래의 모든 `.java` 파일을 UTF-8, Java 17 대상으로 컴파일하고 `resources` 폴더(지도 데이터)를 함께 복사한 뒤 앱을 엽니다. JDK는 `JAVA_HOME`, `PATH`, 흔한 JDK 설치 폴더(`Program Files\Java` 등) 순서로 찾고, Oracle `javapath` 실행 도우미는 마지막 수단으로만 사용합니다. 시작 클래스는 `campus.App`입니다.

다른 OSM 파일이나 별도 개인 일정 파일로 실행하려면 경로를 지정할 수 있습니다.

```powershell
.\run.ps1 --osm "C:\maps\campus.osm" --state "C:\plans\my-planner.xml"
```

- 기본 지도: 프로그램에 포함된 `resources/map/chonnam-campus.osm` (실행 위치와 상관없이 읽습니다)
- 개인 일정 저장 파일: 사용자 홈 폴더의 `.cnu-planner/planner-state.xml` (예: `C:\Users\이름\.cnu-planner\`)
- 예전 버전이 프로젝트의 `data/planner-state.xml`에 저장한 일정은 처음 실행할 때 새 위치로 한 번 복사합니다.
- `--osm`, `--state`의 상대 경로는 프로젝트 폴더를 기준으로 해석합니다.

## 코드 구조

```
src/campus/
 ├─ App.java       시작점 (지도·일정 불러오기, 메인 창 열기)
 ├─ model/         PlannerModel (일정, 즐겨찾기, 휴강, 메모), PlannerStore (XML 저장)
 ├─ map/           CampusMap (OSM 읽기, 건물), WalkGraph (보행 그래프), RouteService (다익스트라)
 ├─ service/       DayPlanService (일정 사이 이동 구간), ScheduleSlot (빈 시간대)
 └─ ui/            MainFrame, WeekBar, PlacesPanel, MapPanel, SchedulePanel,
                   EventDialog, RouteDialog, Theme
resources/map/     전남대 OSM 지도 데이터와 출처
```

`model`, `map`, `service`에는 Swing 코드를 넣지 않습니다. 화면 패널은 `MainFrame`의 메서드를 호출해 일정을 바꾸고, 저장과 화면 갱신은 `MainFrame`이 담당합니다.

## 사용

- 주간 날짜를 선택하면 그날의 일정과 이동 경로를 보여줍니다.
- 고정 일정은 요일, 시작·종료 시각, 건물, 분류와 메모를 지정합니다. 지정한 요일마다 반복됩니다.
- 일일 일정은 날짜를 지정해 동아리, 조별과제, 약속, 특강 등을 추가합니다.
- 지도와 건물 목록에서 장소를 고르고 즐겨찾기에 저장합니다. 건물은 OSM 데이터에서 불러옵니다.
- 일정의 휴강 또는 제외 버튼은 선택한 날짜의 해당 일정만 이동 경로에서 뺍니다. 다시 누르면 복원되며, 다른 주의 고정 일정은 유지됩니다.
- 일정 편집에서 장소, 시각, 분류, 메모를 바꿀 수 있습니다. 고정 일정을 삭제하면 반복 일정 전체를 삭제합니다.
- 일정별 메모와 날짜별 메모를 따로 적을 수 있습니다. 일정, 즐겨찾기, 휴강 예외와 메모는 로컬 파일에 저장되어 앱을 다시 열어도 유지됩니다.
- 시간표 순서대로 유효한 일정 사이의 경로를 보여줍니다. 겹치는 시간이나 수업 사이 이동 시간이 부족한 경우를 확인할 수 있습니다.

## 지도와 이동 시간

지도는 OSM의 건물, 도로, 보행로와 지형 도형을 직접 그립니다. 기존에 만든 캠퍼스 PNG는 사용하지 않습니다. 이름이 등록되지 않은 건물은 OSM ID를 포함한 이름으로 구분합니다.

보행 그래프는 길의 굴곡 좌표를 보존하면서 차수가 2인 중간 정점을 압축하고, 다익스트라로 최단 경로를 구합니다. OSM에 출입구가 있으면 연결하고, 없으면 건물 외곽과 가까운 보행로 사이의 짧은 연결을 추정합니다. 연결을 만들 수 없는 건물은 경로를 표시할 수 없다는 안내를 보여줍니다.

예상 보행 시간은 계산한 거리를 초당 1.3m의 보행 속도로 환산합니다. 계단, 경사, 신호 대기, 실내 이동 시간은 별도로 계산하지 않습니다. OSM에 없는 강의실·층 정보나 출입구 운영 시간은 사용자 메모로 보완할 수 있습니다. 18시 일괄 폐쇄처럼 실제 데이터에 없는 통행 제한을 자동으로 적용하지 않습니다.

지도 데이터의 출처, 라이선스와 추출 범위는 `resources/map/README.md`에 기록합니다. 지도 데이터의 저작자는 OpenStreetMap 기여자이며, ODbL 라이선스가 적용됩니다.

- [OpenStreetMap 저작권 및 이용 안내](https://www.openstreetmap.org/copyright)
- [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/)

## 저장 파일

개인 일정은 사용자 홈 폴더의 `.cnu-planner/planner-state.xml`에 UTF-8 XML로 저장합니다. 저장할 때 같은 폴더에 임시 파일을 쓴 뒤 원래 파일을 교체합니다. 손상되었거나 지원하지 않는 버전의 파일을 읽으면 오류를 알립니다. 같은 저장 파일로 앱을 동시에 열면 덮어쓰기를 막기 위해 두 번째 실행에 안내를 표시합니다. 개인 일정 파일과 실행 잠금 파일은 `.gitignore`에서 제외합니다.
