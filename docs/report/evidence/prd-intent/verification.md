# PRD 의도 보완 검증

2026-11-10 · 최종 앱 코드 `faebf0f2e37d9b08674c8333ec4c36f27682debc`

> 이 기록은 PDF 제공 전 실행한 검증입니다. 이후 제공된 7쪽 PRD 원문 대조와 발견한 디자인 부족분은 [현재 점검표](../../PRD_DESIGN_AUDIT.md)를 따릅니다. 아래 원문 접근 제한은 검사 당시의 상태이며 PDF 자체는 이후 읽었습니다.

[점검 결과·화면별 대응·업그레이드 기준](../../PRD_DESIGN_AUDIT.md) · [라이트 8화면](overview-light.png) · [다크 8화면](overview-dark.png) · [핵심 흐름](intent-overview.png)

현재 결과는 Android XML **디자인과 로컬 예시 흐름**의 정합성입니다. 최신 Notion PRD/TRD는 인증/접근 문제로 읽지 못했습니다. 사용자 제공 S1~S8·승인한 디자인 변경·저장소 MVP를 대조했습니다. ‘이번 주’의 달력 주간/향후 7일 정의는 사용자에게 선택을 요청했고, 답변 전 기존 계산(오늘 제외, 내일~7일 뒤)을 유지합니다.

## 최종 빌드

macOS arm64 · JDK 17 · Android Emulator `emulator-5554`, API 36·arm64-v8a · 1080×2400·420dpi·fontScale 1.0. 작은 화면은 461dpi(약 375dp)입니다.

명령: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

- 빌드 성공. JUnit 결과 **17개**, 실패·오류·건너뜀 0. Lint 오류 0·고정한 도구/의존성 새 버전 안내 경고 15.
- 검사에는 용품을 날씨로 이동하지 않음, 민감하지 않은 집안일/과거일/같은 날짜의 제안 거부, 이번 일정 유지·다음 회차 재평가, 조기·지연 완료, 일정 변경/완료 뒤 이름 수정과 폼 예정일 보존이 포함됩니다.
- `./scripts/design/preview.sh --serial emulator-5554 --no-build`로 실제 설치·실행했습니다.
- APK: `app/build/outputs/apk/debug/app-debug.apk` · SHA-256 `aa11b2d668fb4d64c881e335183e4364c08ddb1c215d67dddc4250eaf76f2716`.

## 실제 화면 확인

| 시나리오 | 확인 결과·근거 |
| --- | --- |
| 홈에서 관리 목적 노출 | 오늘 1·지남 1·7일 이내 2, 요약 탭으로 패널 펼침, 작업 제안 행으로 S7 진입. [기본 홈](ready-home.png) |
| 기존 패널 조작 | 펼침·Back 닫힘·다시 보기, 약 절반의 기본 높이를 유지. [펼침](home-expanded.png)·[닫힘](home-hidden.png) |
| 날씨 제안 선택 | 맑음은 일정 유지, 비 예시는 이유와 유지/변경을 구분. [범위 내 제안](proposal-within-range.png)·[범위 밖](proposal-outside-range.png)·[맑음](proposal-clear-final.png) |
| 예보가 없는 상태 | 오프라인에서 적합도를 판단하지 않고 변경 행동을 제공하지 않음. [예보 불가](weather-offline.png) |
| 가족 참여 | 기본 2인/항목 있는 집 차단 → 집장 내보내기·이력 유지 → 항목 있는 1인 집도 차단 → 명시적 나가기 뒤 빈 1인 집에서 예시 코드 참여 확인. [차단](join-blocked.png)·[조건 충족 참여](join-eligible-success.png) |
| 내보내기·삭제 역할 | 집장만 구성원 관리 진입. 다른 가족 항목의 삭제는 일반 구성원에게 비활성, 자신의 항목은 가능. [내보내기 확인](remove-confirm.png)·[삭제 제한](member-delete-blocked.png) |
| 알림 상태 분리 | 알림 선호 켬/권한 예시 거부에서 권한 배너 유지, 선호 끔/권한 예시 허용에서 권한 표시는 유지. [선호 켬/권한 거부](notifications-without-permission.png)·[선호 끔/권한 허용](permission-without-notifications.png) |
| 조기 완료의 원래 회차 | 11/12 목요일 회차를 11/11로 옮겨 11/10 완료 → 다음은 11/19 목요일. [원래 예정일](weekly-moved-original.png)·[조기 완료](weekly-early-completion.png) |
| 메타데이터만 수정 | 예정일 미리보기·저장 후 상세는 11/19 유지, 캘린더 같은 항목과 홈 임박 개수도 반영. [폼 미리보기](weekly-edit-preview.png)·[수정 뒤](weekly-after-name-edit.png)·[캘린더](weekly-calendar.png) |
| 테마·작은 화면 | 라이트/다크와 375dp에서 홈 작업 제안 행이 실제 탭으로 S7 진입. [작은 홈](home-small.png)·[다크 홈](home-dark.png) |

홈/제안/가족/권한의 묶음 실행은 `b71aa5a`에서 통과했고 이후 예보 범위와 카피를 보완했습니다. 최종 코드에서는 예보 범위·삭제 상태를 다시 확인하고, 원래 회차·이름 수정·캘린더/홈 반영을 확인했습니다. 정상 경로 10개 × 라이트/다크 전체 캡처도 최종 코드로 갱신했습니다. 예시 초기화 후 시작하며 마지막에 기본 예시·라이트·420dpi·기기 시각 자동·압축 홈으로 복원했습니다.

주간 UI 검사의 첫 실행은 수정 후 상세의 이전 스크롤 위치가 복원돼 화면 밖 제목을 찾는 데 실패했습니다. 검사에서 제목까지 스크롤한 뒤 다시 확인해 통과했고, 최초 로그와 재실행을 분리해 보존했습니다. 제품 실패로 단정하거나 첫 실행 전체가 통과했다고 기록하지 않습니다.

## 근거와 한계

로그는 `app/build/reports/design/`의 `prd-intent-build.log`, `prd-intent-ui.log`, `prd-forecast-range.log`, `prd-weekly-first-run.log`, `prd-weekly-ui.log`, `prd-final-states.log`, `prd-final-captures.log`에 보존합니다. APK·전체 로그·초기 단계 화면은 Git에 넣지 않고 최종 및 핵심 상태 이미지만 포함합니다.

UI/UX Pro Max의 검색 결과는 이번 모바일 핵심 행동 노출 문제에 직접 맞지 않아, 기존 네이티브 UI 체크리스트·승인된 컴포넌트 규격을 기준으로 적용했습니다. 별도 문맥의 문서/홈 시각 검토는 ‘경미한 공백’ 판정이었으며, 최종 검증 SHA·오늘 제외 집계·다음 연결 단위의 수용 기준을 문서에 보완했습니다. 압축 패널의 첫 행이 유리 바 뒤로 일부 보이는 한계를 남겼고, 요약용 압축 상태와 목록 처리용 펼침 상태를 구분했습니다. 가족 흐름은 홈 사진만으로 검증하지 않고 별도 시나리오 근거를 사용합니다. 문서/홈 시각 검토와 실제 입력 검증을 구분합니다.

실제 Auth·Firestore·기상청·알림·공유·초대·보안 규칙·사진 OCR은 연결하지 않았습니다. 표시용 예시 등록자와 권한 상태를 실제 UID/OS 권한으로 간주하지 않습니다. 실제 회차 ID·revision·동시성·기존 서버 데이터 마이그레이션, 두 기기·실기기·TalkBack·성능 계측은 미검증입니다. 실제 캘린더·사진의 P1 우선순위는 바꾸지 않았습니다.

사용자(성명 확인 필요)는 디자인 담당으로 PRD 의도 점검을 요청했고, Codex 도구가 대조·UI/예시 규칙·검증·기록을 수행했습니다. 팀원의 승인/구현으로 대체 기록하지 않습니다. 로컬 `feature/design-system-foundations`에서 커밋했고 원격 `Repository not found`로 이슈·PR·push는 수행하지 못했습니다. main 병합·보호 설정 변경·브랜치/worktree 삭제는 하지 않았습니다.
