# 때맞춤 · Home Timing

생활용품 교체일과 날씨에 맞춘 집안일을 관리하는 Android 앱입니다. Java + XML, Firebase, 기상청 API를 사용하며 가족이 목록과 완료 이력을 공유합니다.

> 이메일 계정·용품·집안일·가족·공용 날씨 지역을 기존 Firebase Repository에 연결합니다. 로컬 알림은 로그인/집 이동/로그아웃과 개인 설정을 따릅니다. 실제 기상청 조회에는 API허브 키와 활용신청이 필요하고, 오류·캐시·테스트 예보는 구분합니다. 구현·빌드 성공과 실제 서비스·실기기 검증 결과는 [검증 기준](docs/TESTING.md)과 연결된 이슈/PR을 확인하세요.

## 시작하기

clone한 뒤 Codex나 Claude Code에 **“이 프로젝트 개발 환경 설정해줘”** 라고 요청하면 [team-setup](.agents/skills/team-setup/SKILL.md)을 따릅니다. JDK·SDK 확인, 본인 Firebase 연결·설정 파일 배치, 기본 빌드를 진행하며 Google 로그인과 기상청 키는 본인이 준비합니다. 명시적으로 호출하려면 Codex는 `$team-setup`, Claude Code는 `/team-setup`을 사용합니다.

1. [환경 설정](docs/SETUP.md)에 따라 JDK 17과 Android SDK를 준비합니다.
2. Android Studio에서 레포 루트를 열고 Gradle JDK를 JDK 17로 선택합니다.
3. 아래 명령으로 환경과 기본 빌드를 확인합니다.

```sh
./gradlew teamDoctor
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows에서는 `./gradlew.bat`를 사용합니다. Firebase 설정과 기상청 키 없이도 기본 빌드가 가능하며, 실제 연동 준비는 [외부 서비스 설정](docs/SETUP.md#외부-서비스)을 따릅니다.

현재 화면 구조와 공통 규격은 [디자인 시스템](docs/DESIGN_SYSTEM.md)을 따릅니다. 일반 실행은 로그인 또는 저장된 계정의 집을 엽니다. Firebase 설정이 없으면 설정 안내를 표시하며 예시 데이터로 성공을 대신하지 않습니다. 기존 디자인 갤러리는 debug APK의 명시적 `preview_screen` Intent에서만 열 수 있습니다([미리보기 실행 안내](docs/DESIGN_SYSTEM.md#미리보기-실행)). 서명 APK는 [Release 절차](docs/SETUP.md#release-apk)를 따릅니다.

[PRD 의도 점검과 업그레이드 기준](docs/report/PRD_DESIGN_AUDIT.md)에서 화면별 대응, 예시 동작과 실제 연동의 경계, 후속 연결 순서를 확인합니다.

공유용 [전체 화면 HTML](docs/design-review.html)은 파일 하나로 전달하고 인터넷 없이 열 수 있습니다. 라이트/다크·S1~S8·가입/설정 하위 화면·컴포넌트와 상태 예시를 포함합니다. 팀원이 구현에 적용할 입력·상태·이벤트 계약은 [디자인 시스템](docs/DESIGN_SYSTEM.md#팀원-적용용-컴포넌트-계약)을 참고하세요.

확정된 화면의 JPG는 [라이트 요약](docs/design-jpg/overview-light.jpg)·[다크 요약](docs/design-jpg/overview-dark.jpg)과 [전체 페이지 보드](docs/design-jpg)에서 확인합니다. 저장소에는 현재 승인된 시안만 두고 중간 디자인은 Git 이력으로 보존합니다.

## 팀 작업 안내

| 주제 | 문서 |
| --- | --- |
| 제출 범위·구현 순서 | [MVP](docs/MVP.md) |
| 코드 구조·검증 | [아키텍처](docs/ARCHITECTURE.md) · [검증 기준](docs/TESTING.md) |
| 브랜치·PR·버전 변경 | [협업 규칙](CONTRIBUTING.md) |
| 참여자별 기여·최종 보고서 | [기록 기준](docs/report/README.md) · [개발 기록](docs/report/WORKLOG.md) |
| AI 도구 공통 지침 | [AGENTS.md](AGENTS.md) · [CLAUDE.md](CLAUDE.md) |

전체 구조는 [문서 안내](docs/README.md)에서 확인합니다. 작업 완료 시 PR에 **누가 무엇을 구현·검증했는지** 남기고 개발 기록에 연결합니다.

[기존 Notion 기획](https://app.notion.com/p/40f8bac3963f80298786d02fdf27da79)과 제출 범위가 다르면 **MVP 문서**를 기준으로 작업합니다.
