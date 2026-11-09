# Android XML 디자인 미리보기 검증

2026-11-09. 이 기록과 같은 커밋의 `app/` 소스로 만든 디버그 APK를 대상으로 합니다. 예시 데이터이며 외부 서비스·계정·일정 변경은 실행하지 않았습니다. 이전 HTML과 별도의 네이티브 화면입니다.

## 환경과 빌드

- macOS / arm64, Zulu JDK 17, 저장소 Gradle Wrapper.
- 전용 읽기 전용 AVD: Android API 36.1 / arm64, 1080 × 2400, 420dpi, 약 411dp 폭.
- 작은 화면: `wm size 945x2100`, 420dpi, 360dp 폭. 글자 확대: `font_scale=2.0`.
- 에뮬레이터는 별도 포트에서 headless·read-only·no-snapshot으로 실행했습니다. 기존 기기·AVD 데이터는 수정하지 않았습니다.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

결과: 빌드·Lint 성공. Lint 0 errors / 15 warnings이며 경고는 기존 Gradle·플러그인·라이브러리의 새 버전 알림입니다. 이번 작업에서 의존성 버전은 변경하지 않았습니다. `testDebugUnitTest`는 **NO-SOURCE**이며 제품 단위 테스트 통과를 뜻하지 않습니다.

로컬 상세 로그: `/tmp/home-timing-xml-build.log`, `/tmp/home-timing-native-qa.log`. Lint 보고서: `app/build/reports/lint-results-debug.html`.

## 에뮬레이터 시나리오

ADB로 설치·실행하고 UI Automator의 실제 뷰 계층에서 선택된 탭과 화면 문구를 확인했습니다. 화면은 `screencap`으로 저장한 뒤 직접 검토했습니다.

| 시나리오 | 실제 확인 |
| --- | --- |
| 앱 시작 | 홈 화면과 선택된 홈 탭 표시 |
| 홈 → 용품 → 설정 | 각 화면의 제목과 선택 탭 변경 |
| 가족 관리 행 선택 | 미구현 기능임을 알리는 다이얼로그 표시, 뒤로 닫기 |
| 설정 탭에서 가로 회전 | Activity 재생성 후 설정 탭 유지 |
| 360dp 폭·200% 글자 | 홈·용품 화면의 줄바꿈과 하단 탭 접근, 일정 상태가 메타 정보 아래로 이동 |
| 큰 글자에서 목록 스크롤 | 마지막 예시 항목 ‘칫솔’까지 접근 |
| 기본 크기로 복원 | 홈 화면 재표시 |

| 이미지 | 화면·조건 |
| --- | --- |
| [home.png](home.png) | 홈, 약 411dp, 글자 100% |
| [items.png](items.png) | 용품, 약 411dp, 글자 100% |
| [settings.png](settings.png) | 설정, 약 411dp, 글자 100% |
| [items-360-font200.png](items-360-font200.png) | 용품, 360dp, 글자 200%, 상단 |
| [items-360-font200-scrolled.png](items-360-font200-scrolled.png) | 용품, 360dp, 글자 200%, 스크롤 후 |
| [settings-landscape.png](settings-landscape.png) | 설정, 가로 화면 |

## 한계와 인계

물리 기기, API 26, 태블릿, 제스처 내비게이션, TalkBack의 실제 읽기 순서, 키보드가 있는 입력 화면은 검증하지 않았습니다. 실제 긴 사용자 항목명과 제품 오류·로딩·오프라인 상태도 후속 기능 작업에서 검증해야 합니다. 화면 재생성 시 선택 탭은 복원하지만 모든 탭의 스크롤 위치를 프로세스 종료 이후까지 보존하는 기능은 구현하지 않았습니다.

사용자는 레퍼런스와 단색·평면 디자인 방향을 지정했습니다. 성명은 확인하지 않았습니다. Codex 도구가 XML·Java·규격·검증 기록을 작성하고 로컬 빌드와 에뮬레이터 확인을 수행했습니다. 팀원의 리뷰·승인·실기기 확인은 수행하지 않았습니다.

GitHub fetch와 이슈 조회는 `Repository not found`로 실패했습니다. 원격 최신 main 일치 여부를 확인하지 못했고 이슈·PR 생성, push, 병합은 하지 않았습니다. 로컬 작업 브랜치는 `feature/design-system-foundations`, 구현 시작 기준은 `ce735dd`입니다.
