# 태양 아이콘·하단 패널 검증

2026-11-09. 디자인 미리보기의 후속 UI 검증입니다. 기준 커밋은 `c007133`, 검증한 app/ Git tree는 `10574472469ae4a50ebed0caa388d6cda05f7ef4`입니다. 최종 커밋에서도 같은 app/ 트리를 유지합니다. APK SHA-256은 `fa51fe46bb73b8dc6e4aab42dad89060e059c556f823ed3a3123525e2743becb`입니다. 제품 데이터·날씨 API·계정 연동 검증은 아닙니다.

## 변경과 기준

태양은 채운 원과 같은 길이의 광선 8개로 바꿨습니다. 광선 길이 차이는 24 viewBox 기준 0.001 미만이며 단색 벡터입니다. 핸들은 실제 `BottomSheetBehavior`에 연결했고 홈·용품·설정에서 드래그와 클릭을 지원합니다. 중앙 확인 다이얼로그에는 드래그 기능이 없어 핸들을 붙이지 않습니다.

## 환경과 결과

macOS arm64, Zulu JDK 17, Gradle Wrapper. 읽기 전용 전용 AVD의 Android API 36.1 / arm64에서 확인했습니다. 기본 1080 × 2400 / 420dpi, 작은 화면 945 × 2100 / 420dpi인 360dp 폭, 글자 배율 200%, 가로 회전을 사용했습니다.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

빌드·Lint 성공. Lint 0 errors / 15 warnings는 기존 도구·라이브러리 새 버전 알림입니다. `testDebugUnitTest`는 **NO-SOURCE**로 제품 단위 테스트가 없는 상태입니다. XML 파싱·상대 링크·`git diff --check`도 확인했습니다.

ADB 설치·실행과 UI Automator의 실제 버튼 설명·패널 위치·선택 탭·화면 문구를 확인하고 screencap으로 화면을 검토했습니다.

| 시나리오 | 실제 결과 |
| --- | --- |
| 핸들 클릭 | 패널 위치와 ‘목록 펼치기/접기’ 설명이 함께 전환 |
| 위로 드래그 | 접힌 패널이 펼쳐짐 |
| 접힌 목록에서 위로 스크롤 | 목록 스크롤에 앞서 패널이 펼쳐짐 |
| 펼친 상태의 뒤로가기 | 패널을 접고 홈 화면 유지 |
| 용품·설정 탭 | 각 화면에서 핸들 펼치기·접기 동작 |
| 탭 이동 후 복귀 | 해당 패널의 펼침 상태 유지 |
| 가로 회전 | 펼친 상태와 핸들 설명을 함께 복원 |
| 설정 행 선택 | 미리보기 안내가 열리고 뒤로가기로 안내만 닫힘 |
| 접힌 설정에서 뒤로가기 | 홈 탭으로 이동 |
| 360dp·200% | 펼친 홈 목록의 마지막 ‘침구 세탁’까지 스크롤로 접근 |
| 목록 스크롤 뒤 핸들을 아래로 드래그 | 패널이 접힘 |
| 기본 크기 복원 | 홈 접힌 상태 재표시 |

회전 테스트에서 패널 상태 복원 후 핸들 설명만 초기 상태로 남는 오류를 발견했습니다. Behavior 복원 시 상태 콜백이 호출되지 않는 경우에도 레이아웃 단계에서 실제 상태와 설명을 동기화하도록 수정하고 재검증했습니다.

## 화면

| 이미지 | 조건 |
| --- | --- |
| [home-collapsed.png](home-collapsed.png) | 홈 접힘, 새 태양과 핸들 |
| [home-expanded.png](home-expanded.png) | 홈 펼침 |
| [items-expanded.png](items-expanded.png) | 용품 펼침 |
| [home-landscape-expanded.png](home-landscape-expanded.png) | 가로 회전 후 펼친 상태 |
| [home-360-font200-expanded.png](home-360-font200-expanded.png) | 360dp·200%, 목록 스크롤 후 |

## 인계와 한계

실기기·API 26·태블릿·제스처 내비게이션·TalkBack의 실제 음성 읽기는 미검증입니다. 접근성 버튼 이름·상태와 클릭 가능 여부는 에뮬레이터 뷰 계층에서 확인했으며 실제 TalkBack 사용 검증을 대신하지 않습니다. 현재 미리보기 데이터 이외의 긴 목록·동적 데이터 갱신은 후속 제품 작업에서 확인합니다.

사용자(성명 확인 필요)는 태양 아이콘 개선과 실제 행동에 맞는 핸들·UX를 요청했습니다. Codex 도구가 XML·Java·규격 작성, 빌드·에뮬레이터 검증을 수행했습니다. 사람의 리뷰·승인·실기기 확인은 수행하지 않았습니다. 로컬 로그는 `/tmp/home-timing-sheet-build.log`, `/tmp/home-timing-sheet-qa.log`입니다.

기존 작업 브랜치 `feature/design-system-foundations`에서 진행했습니다. 원격은 `Repository not found`로 이슈·PR과 최신 main을 확인하지 못했으며 push·병합하지 않았습니다.
