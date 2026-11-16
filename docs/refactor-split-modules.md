# 변경 사항: 파일 분리와 실행 환경 수정

`main`의 프로토타입을 기능 변경 없이 역할별 파일로 나눴습니다. 코드는 대부분 원래 그대로 옮겼습니다.

## 파일 위치

| 기존 | 변경 후 |
|---|---|
| `CampusPlanner.java` | `App.java` |
| `PlannerModel.java`, `PlannerStore.java` | `model/` (내용 그대로) |
| `MapData.java` | `map/CampusMap` (OSM 읽기, 건물, 좌표 계산)<br>`map/WalkGraph` (보행 그래프, 출입구 연결, 압축)<br>`map/RouteService` (다익스트라, 경로 캐시) |
| `PlannerFrame.java` | `ui/MainFrame` (배치, 날짜·건물 선택, 저장)<br>`ui/WeekBar`, `ui/PlacesPanel`, `ui/SchedulePanel` |
| `CampusMapPanel.java` | `ui/MapPanel` (이동 구간 계산은 `service/DayPlanService`로) |
| `ScheduleSlot.java` | `service/ScheduleSlot` |
| `Theme.java`, `EventDialog.java`, `RouteDialog.java` | `ui/` |
| `data/chonnam-campus.osm`, `data/README.md` | `resources/map/` |

- 플로이드-워셜 코드는 지웠습니다. 노드가 900개 이하일 때만 쓰게 되어 있었는데 실제 그래프는 1,696개라 실행된 적이 없습니다.

## 실행 환경 수정

- **지도 파일**: 실행 위치 기준으로 찾던 것을 클래스 경로(`resources/`)에서 읽도록 바꿨습니다.
- **저장 위치(현재)**: 프로젝트의 `data/planner-state.xml`. 사용자 홈에서 가져오는 이전 처리는 제거했습니다.
- **폰트**: 맑은 고딕이 없으면 설치된 다른 한글 폰트를 씁니다.
- **run.ps1**
  - PATH에 없는 드라이브(예: `D:\`)가 있으면 멈추던 문제 수정
  - `JAVA_HOME` 없이 Oracle JDK만 설치된 PC에서 JDK를 못 찾던 문제 수정
  - 하위 폴더까지 컴파일하고 `resources`를 복사하도록 변경

## 확인한 것

원래 코드와 바꾼 코드를 같은 일정 파일로 실행해 비교했습니다.

- 건물 408쌍의 경로(거리, 좌표, 안내 문구): 모두 같음
- 메인 화면, 건물 선택·즐겨찾기·휴강 후 화면, 최단거리 검색 화면: 다른 픽셀 0개
- 저장 후 다시 불러오기: 같음
- `run.ps1` 빌드, 한글·공백이 있는 홈 경로에서 예전 일정 복사: 정상

실제 창을 띄워 마우스로 조작하는 테스트와 Mac·Linux 실행은 아직 하지 않았습니다.

## 기능 추가할 때

- 지름길: `map/WalkGraph` 생성자의 주석 위치에서 간선 추가
- 과제·수업 데이터: `model/PlannerModel`, `PlannerStore`
- 새 화면 영역: `ui/`에 패널을 만들고 `MainFrame`에서 배치
