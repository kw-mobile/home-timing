# 팀 작업 규칙

## 작업 시작

[환경 설정](docs/SETUP.md)과 [MVP 기준](docs/MVP.md)을 먼저 읽습니다. 최신 main에서 한 작업당 한 브랜치를 만듭니다.

```sh
git switch main
git pull --ff-only origin main
git switch -c feature/issue-number-short-name
```

`feature/`, `fix/`, `docs/`, `chore/` 접두어를 사용합니다. 현재 공용 기준 브랜치는 main입니다. 별도 develop 브랜치는 만들지 않습니다. 공용 브랜치에 강제 push하지 않습니다.

## 충돌 줄이기

- 작업 시작 전에 이슈에 담당자·수정할 화면/계층을 적고 공유 파일 담당을 조율합니다.
- UI는 `ui/<기능>`, 데이터는 `data/{auth,firestore,weather,local}`, 순수 Java 규칙은 `domain`, 알림은 `notify`로 나눕니다. 기본 패키지는 `com.kwmobile.hometiming`입니다.
- Manifest·화면 이동·버전 카탈로그·잠금 파일·Gradle 설정은 여러 작업이 함께 바꾸기 쉬우므로 PR을 작게 나눕니다.
- 라이브러리를 추가할 때 Gradle 파일에 버전을 직접 쓰지 않습니다. [버전 변경 절차](docs/SETUP.md#의존성버전-변경)를 따릅니다.
- Firebase는 공용 프로젝트를 사용하되 공용 테스트 계정·데이터를 무단 초기화하지 않습니다. 개발 데이터 이름에는 담당자 식별값을 넣습니다.
- `local.properties`, `google-services.json`, `.idea`, 서명 파일, API 키를 커밋하지 않습니다. 설정을 공유할 때는 값 대신 필요한 키 이름과 발급 경로를 문서화합니다.

## PR

1. 관련 이슈와 사용자가 확인할 변경 동작을 적습니다.
2. 최신 main을 작업 브랜치에 반영하고 충돌을 해결합니다. 진행 중 다른 사람의 작업을 덮어쓰지 않습니다.
3. `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`를 실행합니다.
4. Firebase 기능은 실제 팀 설정으로 빌드하고 권한 테스트를 추가합니다. 알림·공유 변경에는 실기기/두 기기 결과도 남깁니다.
5. PR을 만들고 팀원 1명 이상의 리뷰를 받습니다. 이 규칙은 팀 작업 기준이며 저장소 보호 설정은 자동으로 변경하지 않습니다.
6. main 병합은 팀의 권한 있는 담당자가 수행합니다. 기능 작업 중에 도구 버전 업데이트를 섞지 않습니다.

커밋 메시지는 `feat:`, `fix:`, `docs:`, `chore:` 등 변경 목적을 드러내는 접두어를 사용합니다. 버전 업그레이드는 카탈로그·Wrapper·잠금 파일과 검증 결과를 묶은 별도 PR로 진행합니다.
