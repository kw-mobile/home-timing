# 때맞춤 · Home Timing

생활용품 교체일과 날씨에 맞춘 집안일을 관리하는 Android 앱입니다. Java + XML, Firebase, 기상청 API를 사용하며 가족이 목록과 완료 이력을 공유합니다.

> 현재 공통 개발 환경과 Android XML 기반 홈·용품·설정 디자인 미리보기를 준비한 단계입니다. 화면은 예시 데이터이며 제품 기능과 Firestore 보안 규칙은 후속 작업으로 구현합니다.

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

현재 화면 구조와 공통 규격은 [디자인 시스템](docs/DESIGN_SYSTEM.md)을 따릅니다. Android Studio에서 앱을 실행하면 네이티브 미리보기를 확인할 수 있습니다. [HTML 프로토타입](docs/prototype/README.md)은 이전 구조의 참고 시안입니다.

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
