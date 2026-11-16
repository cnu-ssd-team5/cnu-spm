# 변경 사항: 패키지 분리와 실행 환경 수정

`prototype`(김정현 님 기능 추가 버전)을 기능 변경 없이 패키지로 나눴습니다. 클래스 이름과 코드는 대부분 그대로입니다.

## 파일 위치

| 기존 (`src/campus/`) | 변경 후 |
|---|---|
| `CampusPlanner.java` | 그대로 (`campus` 패키지) |
| `PlannerModel.java`, `PlannerStore.java` | `model/` |
| `MapData.java` | `map/MapData` (OSM 읽기, 건물, 좌표 계산)<br>`map/WalkGraph` (보행 그래프, 출입구·핀 연결, 압축)<br>`map/RouteService` (`Route`, 다익스트라, 경로 캐시) |
| `Place.java`, `PlaceCatalog.java`, `ScheduleSlot.java` | `service/` |
| `CampusMapPanel`의 이동 구간 계산(`Leg`) | `service/DayPlanService` |
| 나머지 화면·대화상자, `Theme` | `ui/` |
| `test/campus/*Check.java` | `test/campus/model/`, `test/campus/ui/` |
| `data/chonnam-campus.osm`, `data/README.md` | `resources/map/` |

- `PlannerFrame`은 이미 패널별로 나뉘어 있어서 더 나누지 않았습니다.
- 플로이드-워셜 코드는 지웠습니다. 노드가 900개 이하일 때만 쓰게 되어 있었는데 실제 그래프는 1,696개라 실행된 적이 없습니다.
- 패키지가 달라져서 다른 패키지에서 쓰는 클래스(`Place`, `PlaceCatalog`, `ScheduleSlot`, `PlannerFrame`, `Theme`)는 `public`으로 바꿨습니다.

## 실행 환경 수정

- **지도 파일**: 실행 위치 기준으로 찾던 것을 클래스 경로(`resources/`)에서 읽습니다.
- **저장 위치**: `data/planner-state.xml` → `~/.cnu-planner/planner-state.xml`, 과목 자료 `materials/` → `~/.cnu-planner/materials/`. 예전 위치에 파일이 있으면 처음 실행할 때 한 번 복사합니다.
- **폰트**: 맑은 고딕이 없으면 설치된 다른 한글 폰트를 씁니다.
- **run.ps1**
  - PATH에 없는 드라이브(예: `D:\`)가 있으면 멈추던 문제 수정
  - `JAVA_HOME` 없이 Oracle JDK만 설치된 PC에서 JDK를 못 찾던 문제 수정
  - 하위 폴더까지 컴파일하고 `resources`를 복사하도록 변경

## 테스트 수정

검사 내용은 바꾸지 않았고, 새 구조에 맞춰 다음만 고쳤습니다.

- 패키지 선언과 import
- `new PlaceCatalog(data, model)` → `new PlaceCatalog(routes, model)` (`RouteService routes = new RouteService(data)` 추가)
- `new PlannerFrame(data, model, ...)` → `new PlannerFrame(data, routes, model, ...)`
- `MapData.Route` → `RouteService.Route`, `CampusMapPanel.Leg` → `DayPlanService.Leg`
- `CampusPlanner.configureLookAndFeel()` → `Theme.configureLookAndFeel()`
- 지도 경로 `data/chonnam-campus.osm` → `resources/map/chonnam-campus.osm`
- `PlacesCheck`가 리플렉션으로 읽던 `MapData.geometry` → `RouteService.walk.geometry`

## 확인한 것

- 팀원 테스트 4개: 모두 통과 (모델·저장 66, 장소·경로 37, 보조 화면 24, 통합 166)
- `prototype`과 비교 (같은 일정 파일, 핀 3개, 과제 1개)
  - 경로 501개(건물↔건물, 핀↔건물, 핀↔핀)의 거리·좌표·안내 문구: 모두 같음
  - 지도·달력·하루 시간표·과목 자료·최단거리 검색 화면: 다른 픽셀 0개
- `run.ps1` 빌드, 한글·공백이 있는 홈 경로에서 예전 일정·과목 자료 복사: 정상

실제 창을 띄워 마우스로 조작하는 테스트와 Mac·Linux 실행은 하지 않았습니다.
