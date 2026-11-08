# 때맞춤 · Home Timing

생활용품 교체일을 관리하고, 날씨에 맞춰 세탁일을 제안하는 Java + XML Android 앱입니다. 가족이 같은 집의 목록과 완료 이력을 공유합니다.

## 팀원이 처음 시작할 때

1. [환경 설정](docs/SETUP.md)에 따라 JDK와 Android SDK를 준비합니다.
2. Android Studio에서 레포 루트를 열고 Gradle JDK를 JDK 17로 선택합니다.
3. macOS/Linux는 아래 명령으로 확인합니다. Windows는 `./gradlew` 대신 `gradlew.bat`를 사용합니다.

```sh
./gradlew teamDoctor
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Firebase 설정과 기상청 키 없이도 기본 화면을 빌드할 수 있습니다. 로그인·공유·날씨 기능 검증은 [외부 서비스 설정](docs/SETUP.md#외부-서비스)을 마친 뒤 진행합니다. 현재 기본 화면만 제공하며 제품 기능과 Firestore 보안 규칙은 아직 구현되지 않았습니다.

## 작업 기준

- [제출 MVP 범위와 구현 순서](docs/MVP.md)
- [브랜치·PR·버전 변경 규칙](CONTRIBUTING.md)
- [자동화 도구 작업 지침](AGENTS.md)
- [기존 Notion 기획](https://app.notion.com/p/40f8bac3963f80298786d02fdf27da79)

합의한 범위 조정은 `docs/MVP.md`를 기준으로 구현합니다. 기존 Notion PRD·TRD와 충돌하면 `docs/MVP.md`의 결정을 따릅니다. Notion 원문은 아직 갱신하지 않았습니다.
