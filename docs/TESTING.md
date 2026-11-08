# 변경별 검증과 결과 기록

## 필요한 검증 선택

| 변경 | 최소 확인 |
| --- | --- |
| 문서·이슈/PR 템플릿·CLAUDE/AGENTS 지침만 변경 | 링크·import·문서 간 지침 정합성, `git diff --check` |
| 순수 Java 제품 규칙 | 관련 단위 테스트와 아래 기본 빌드·Lint |
| UI·리소스 | 기본 빌드·Lint, 변경 화면의 기기 동작·글자 크기·상태 확인 |
| Repository·저장 구조·권한 | 기본 검증, 실패·동시 변경·권한 거부 등 관련 계약 테스트 |
| 알림·권한·공유 | 기본 검증과 해당 실기기/두 기기 시나리오 |
| 도구·SDK·의존성 | SETUP의 잠금 갱신 절차 후 일반 기본 검증 |
| Notion 자동화 | `python -m unittest discover -s scripts/notion/tests -v`, 실제 API 검증은 [연결 안내](report/NOTION_SYNC.md) |
| GitHub workflow | `actionlint`로 구문 확인, 변경한 동작은 해당 CI 결과 확인 |

순수 문서 변경에 Android 빌드를 요구하지 않습니다. 구현을 그대로 반복하는 테스트보다 날짜 경계·동시 완료·권한 거부·네트워크 실패처럼 결과가 달라지는 조건을 확인합니다.

## 기본 명령

JDK·SDK 준비는 [SETUP](SETUP.md)을 따릅니다. Windows에서는 `./gradlew.bat`를 사용합니다.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

JUnit 클래스만 빠르게 확인할 때는 `./gradlew :app:testDebugUnitTest --tests '전체.패키지.TestClass'`로 범위를 좁힙니다. 관련 테스트 성공과 전체 기본 검증은 구분해 기록합니다.

## 결과를 남기는 방식

PR 본문의 검증 표를 최종 결과로 유지합니다. 검증 후 코드가 바뀌면 영향을 받는 검증을 다시 실행하거나, 이전 커밋에서만 확인했다는 점을 적습니다. 새 실기기 결과가 생기면 [PR 댓글 기준](report/README.md#pr-댓글)을 따라 근거를 남기고 본문에 연결합니다.

| 항목 | 기록할 것 |
| --- | --- |
| 기준 | 검증한 앱 커밋 SHA |
| 환경 | 로컬/CI, JDK·OS 또는 기기·Android 버전 |
| 확인 | 실행 명령 또는 재현 가능한 사용자 시나리오 |
| 기대와 실제 | 예상 동작, 관찰한 결과, 성공·실패·미실행 |
| 근거 | CI URL·로그·스크린샷·영상·테스트 보고서 링크 |
| 한계 | 확인하지 못한 상태와 알려진 문제 |

`NO-SOURCE`는 테스트 없음, SKIPPED는 미실행입니다. Lint 성공에 경고가 있다면 경고 유형을 적고, 근거 없이 없었다고 쓰지 않습니다. 빌드 성공은 실기기 동작 증명이 아니고, 테스트 날씨는 실제 API 연결 증명이 아닙니다.

## 산출물과 CI

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `app/build/reports/lint-results-debug.html`
- JUnit: `app/build/reports/tests/testDebugUnitTest/index.html` (테스트가 있을 때 생성)
- CI는 Wrapper·도구 설정 후 키 없는 빌드·단위 테스트 작업·Lint를 단일 작업에서 실행합니다. 자동 배포·에뮬레이터 행렬은 구성하지 않습니다.
- Android 워크플로는 앱·Gradle·JDK 설정·해당 워크플로 변경에 실행합니다. 문서와 독립된 Notion 스크립트 변경에는 Android 빌드를 실행하지 않고, Notion 전용 검증 작업에서 Python 계약 테스트를 확인합니다. `workflow_dispatch`로 수동 실행할 수 있습니다. 혼합 PR은 PR 전체 변경에 코드가 포함되므로 문서만 추가 push해도 다시 실행될 수 있습니다.
- 이 워크플로를 추후 필수 검사로 지정한다면 경로 필터로 건너뛴 PR도 병합 가능한지 함께 검토합니다. 저장소 보호 설정은 이 문서로 변경되지 않습니다.

실제 Firebase·기상청·알림·두 기기 검증은 관련 기능이 구현된 뒤 별도 기록합니다. 기능별 필수 시나리오는 [MVP 완료 기준](MVP.md#구현-순서와-완료-기준)을 따릅니다.
