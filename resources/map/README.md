# 예제 학교 OSM 데이터

지도 교체창에서 아래 원본 파일을 선택합니다. 앱은 원본을 변경하지 않고 `data/selected-map.osm`으로 복사해 사용합니다.

- 전남대: `chonnam-campus.osm` (첫 실행 기본 지도)
- 조선대: `chosun-campus.osm`
- 광주과학기술원(GIST): `gist-campus.osm`
- 광주교육대학교: `gnue-campus.osm`
- 광주대학교: `gwangju-campus.osm`
- 호남대학교: `honam-campus.osm`

## 추가 학교 원본 (2026-11-27 다운로드)

각 학교의 OSM 캠퍼스 경계 전체와 주변 약 60~80m 영역을 포함해 내려받았습니다. 아래 파일은 OpenStreetMap API의 XML 원본 응답입니다.

| 학교 | 파일 | 캠퍼스 경계 | 원본 추출 |
|---|---|---|---|
| 광주과학기술원 | `gist-campus.osm` | [way 475431872](https://www.openstreetmap.org/way/475431872) | [OSM API](https://api.openstreetmap.org/api/0.6/map?bbox=126.835678,35.223373,126.849568,35.233559) |
| 광주교육대학교 | `gnue-campus.osm` | [way 105568793](https://www.openstreetmap.org/way/105568793) | [OSM API](https://api.openstreetmap.org/api/0.6/map?bbox=126.922472,35.162389,126.931070,35.167303) |
| 광주대학교 | `gwangju-campus.osm` | [way 485774210](https://www.openstreetmap.org/way/485774210) | [OSM API](https://api.openstreetmap.org/api/0.6/map?bbox=126.891612,35.101766,126.897765,35.109749) |
| 호남대학교 | `honam-campus.osm` | [way 539149487](https://www.openstreetmap.org/way/539149487) | [OSM API](https://api.openstreetmap.org/api/0.6/map?bbox=126.755728,35.148680,126.771612,35.154428) |

저작자 © OpenStreetMap contributors · [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/) · [저작권 안내](https://www.openstreetmap.org/copyright)

네 파일 모두 앱의 기존 OSM 로더로 올바른 캠퍼스 경계 선택, 건물 좌표 완전성, 보행 그래프 생성과 건물 간 경로 계산을 확인했습니다. 창을 띄우는 검사는 하지 않았습니다.

## 조선대학교

- 출처 [OpenStreetMap API 원본 지도 추출](https://api.openstreetmap.org/api/0.6/map?bbox=126.922166,35.134535,126.937353,35.147812)
- 요청 범위: 서쪽 경도 126.922166, 남쪽 위도 35.134535, 동쪽 경도 126.937353, 북쪽 위도 35.147812
- 캠퍼스 경계 [OSM way 468689587](https://www.openstreetmap.org/way/468689587)
- 저작자 © OpenStreetMap contributors · [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- 원본 추출에 연결된 외부 교통 정보가 포함될 수 있으며, 앱은 대학 경계와 선택 영역을 기준으로 필요한 지도를 구성합니다.

## 전남대학교

- 파일 `chonnam-campus.osm`
- 출처 [OpenStreetMap API 원본 지도 추출](https://api.openstreetmap.org/api/0.6/map?bbox=126.897,35.171,126.915,35.184)
- 다운로드 날짜 2026-11-15
- 요청 범위 서쪽 경도 126.897, 남쪽 위도 35.171, 동쪽 경도 126.915, 북쪽 위도 35.184
- 캠퍼스 경계 [OSM way 105125653](https://www.openstreetmap.org/way/105125653)
- 경계 범위 위도 35.1716893–35.1821349, 경도 126.8971485–126.9124815
- 저작자 © OpenStreetMap contributors
- 라이선스 [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- 저작권 및 기여자 안내 [OpenStreetMap copyright](https://www.openstreetmap.org/copyright)

이 파일은 위 API 응답을 그대로 저장한 OSM XML 스냅샷입니다. 앱은 인터넷 연결 없이 이 파일에서 지도를 그립니다. 이전에 제작한 지도 PNG, 학교 공식 안내도, 수작업으로 만든 건물이나 도로 데이터는 지도와 길찾기에 사용하지 않습니다.

## 건물과 지도 표시

`amenity=university`인 닫힌 캠퍼스 경계 중 면적이 가장 큰 곳을 기준으로 내부의 건물 폴리곤을 읽습니다. `multipolygon` 건물도 외곽선을 연결해서 읽습니다. 전남대 스냅샷에는 캠퍼스 건물 211개가 있으며 만들마루 `w1125468849`, G&R허브 `w1125544293`가 포함됩니다. 이름이 없는 건물도 지도에서 선택할 수 있으며 목록에서는 숨깁니다. 건물명은 OSM의 한국어 이름을 우선 사용하고 사용자 수정 이름은 개인 데이터에 저장합니다.

## 보행 경로 계산

보행 가능한 도로의 OSM 노드를 연결합니다. 계단을 포함하며, `foot=no`, `access=private` 등의 명시적인 통행 제한을 제외합니다. `foot=yes` 등 보행 허용 태그는 일반 접근 제한보다 우선합니다. 차량의 일방통행은 보행에 적용하지 않으며 `oneway:foot`은 적용합니다. 교차해 보여도 동일한 OSM 노드로 연결되지 않은 길은 연결하지 않습니다.

캠퍼스 내부와 경계 바깥 약 42m 이내의 실제 보행 도로를 사용합니다. 연속된 길에서 갈림길이 아닌 중간 노드를 압축하고, 원래 길의 굴곡과 거리는 유지합니다. 현재 데이터에서는 원본 도로 노드 4,311개가 약 1,696개의 교차점 및 건물 연결 노드로 압축됩니다. 압축 그래프에서 Dijkstra로 최단 경로를 계산하고 건물 쌍별 결과를 캐시합니다.

OSM에 연결된 출입구가 있으면 해당 출입구를 사용합니다. 출입구와 길 사이 연결이 누락된 경우에는 실제 건물 외곽과 가까운 보행 도로 사이의 짧은 연결을 추정합니다. 출입구가 표시된 경우 최대 55m, 출입구가 없는 경우 최대 75m 이내에서 다른 건물과 물을 가로지르지 않는 연결을 선택합니다. 이 경우 경로 안내에 추정된 연결 구간이 있다는 설명을 표시합니다. 이것은 교내의 확인된 출입구나 통로를 보증하는 정보가 아닙니다. 실내 통과 지름길, 실시간 공사, 건물 운영 시간, 18시 이후 문 개폐 여부는 추가하지 않았습니다. 그래프상 경로가 없으면 경로 없음으로 표시합니다.

거리는 위경도 좌표의 구면 거리로 계산합니다. 도보 시간에는 앱의 가정인 초속 1.3m를 적용하며, 실내 이동, 신호 대기, 경사 등에 따른 추가 시간은 포함하지 않습니다.
