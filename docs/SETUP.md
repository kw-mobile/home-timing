# 팀 공통 개발 환경

## 버전 기준

| 항목 | 기준 파일 | 규칙 |
| --- | --- | --- |
| JDK | [`.java-version`](../.java-version) | 17 major 고정. 보안 패치는 허용하며 CI는 Temurin을 사용 |
| Gradle | [`gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties) | Wrapper와 배포 ZIP SHA-256을 함께 고정 |
| AGP·SDK·Build Tools·라이브러리 | [`libs.versions.toml`](../gradle/libs.versions.toml) | 버전은 이 파일에서만 변경 |
| 전이 의존성 | [`app/gradle.lockfile`](../app/gradle.lockfile) | 생성된 해석 결과를 커밋하고 엄격한 잠금 적용 |

`settings-gradle.lockfile`도 카탈로그 해석 과정에서 생성되므로 함께 커밋합니다.

검증한 기준 조합은 AGP 8.11.1과 Gradle 8.13입니다. 공식 호환 표: [Android Gradle Plugin 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes). JDK 패치·OS·CPU와 Android Studio 버전까지 동일하게 만드는 구성은 아니며, 빌드 도구와 의존성 차이로 생기는 충돌을 줄이는 것이 목적입니다.

Android Studio는 AGP 8.11을 지원하는 Narwhal 2025.1.1 이상 안정 버전을 사용합니다. Studio 자체보다 **Gradle JDK와 Wrapper**가 빌드 기준입니다. 자동 AGP·Gradle 업그레이드는 수락하지 않습니다.

## 최초 설정

1. JDK 17을 설치합니다. 가능하면 팀 전체가 Eclipse Temurin 배포판을 사용합니다.
2. Android Studio에서 레포 루트를 엽니다. 새 프로젝트를 만들거나 중첩 폴더를 열지 않습니다.
3. Settings → Build, Execution, Deployment → Build Tools → Gradle에서 Gradle JDK를 JDK 17 경로로 지정합니다. JDK 21인 내장 JBR을 그대로 선택하지 않습니다.
4. SDK Manager에서 카탈로그의 `compileSdk`에 해당하는 Android SDK Platform, `buildTools` 버전, Android SDK Platform-Tools를 설치합니다.
5. SDK 경로는 Android Studio가 만든 `local.properties`의 `sdk.dir` 또는 개인 `ANDROID_HOME`으로 지정합니다. 레포에는 개인 경로를 커밋하지 않습니다.
6. 터미널의 `JAVA_HOME`도 JDK 17로 지정한 뒤 아래 확인 명령을 실행합니다.

macOS 예시:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew --version
./gradlew teamDoctor
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows PowerShell에서는 `$env:JAVA_HOME`을 자신의 JDK 17 설치 경로로 정하고 `./gradlew.bat`로 동일한 작업을 실행합니다. `local.properties`의 Windows 경로는 `C:/Users/.../Android/Sdk`처럼 슬래시를 사용하세요.

CLI SDK 설치 예시입니다. 버전 변경 시에는 카탈로그를 기준으로 패키지를 선택합니다.

```sh
sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
```

## 외부 서비스

### Firebase

공용 Firebase 프로젝트의 준비 여부와 담당자는 아직 확인되지 않았습니다. MVP 추적 이슈에서 팀이 담당자를 지정합니다. **신규 팀원은 담당자에게 설정 파일을 받아 배치하고**, 아래 프로젝트 생성·앱 등록·Auth 활성화·규칙 배포는 지정 담당자가 수행합니다.

- 팀이 함께 사용하는 Spark 프로젝트 **하나**에 Android 앱 `com.kwmobile.hometiming`을 등록합니다. 팀원마다 별도 프로젝트를 만들지 않습니다.
- 담당자가 Firebase Console에서 받은 `google-services.json`을 팀원에게 전달하고 각자 `app/google-services.json`에 둡니다. 파일은 Git에서 제외됩니다. 파일명을 `.example`에서 바꾸는 식의 가짜 설정은 제공하지 않습니다.
- 파일이 있으면 Google Services 플러그인이 적용됩니다. 없으면 기본 화면 빌드만 가능합니다. `BuildConfig.FIREBASE_CONFIGURED`로 기능 진입을 막고 설정 안내를 제공하는 것은 후속 기능 구현의 책임입니다.
- Firebase 기능을 검증할 때는 `./gradlew -PrequireFirebase=true :app:assembleDebug`를 사용합니다. 설정 파일이 없으면 즉시 실패합니다. 이 옵션은 설정 존재 확인이며 인증·규칙·연결 성공을 보장하지 않습니다.
- 이메일/비밀번호 로그인을 활성화하고 Firestore를 준비합니다. 공용 프로젝트의 허용 규칙을 임시로 열지 않습니다. 규칙과 거부 테스트를 구현한 뒤 공유 데이터를 사용합니다.
- 이번 레포 초기화에서는 Firebase 앱 등록·Auth 활성화·보안 규칙 배포·기기 검증을 수행하지 않았습니다. 결제 계정은 연결하지 않습니다.

### 기상청

`local.properties.example`을 참고해 자신의 `local.properties`에 `KMA_SERVICE_KEY`를 추가합니다. Android Studio가 만든 `sdk.dir`을 덮어쓰지 마세요. 공공데이터포털의 디코딩 키를 사용하며 URL 인코딩은 네트워크 계층에서 처리합니다. CI나 터미널에서는 같은 이름의 환경 변수로 주입할 수 있고 환경 변수가 우선합니다.

키는 `BuildConfig.KMA_SERVICE_KEY`로 제공됩니다. Git에서 제외해도 APK에 넣은 키는 비밀 저장소처럼 보호되지 않습니다. HTTP 로그에는 키가 포함된 URL을 남기지 않아야 합니다. 실제 API 호출·키 유효성 검증은 후속 날씨 기능 범위입니다.

## 확인 결과 읽기

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `app/build/reports/lint-results-debug.html`
- 단위 테스트: `app/build/reports/tests/testDebugUnitTest/index.html` (테스트가 있을 때 생성)
- 초기 프로젝트에는 제품 단위 테스트가 없어 `testDebugUnitTest`가 `NO-SOURCE`일 수 있습니다. 이를 테스트 통과 개수로 세지 않습니다.
- CI는 키 없는 빌드·단위 테스트·Lint와 Wrapper 검증을 수행합니다. 실기기 알림, 두 기기 공유, Firebase 권한, 실제 기상청 연동은 별도 검증합니다.

## 의존성·버전 변경

1. 버전 변경 전용 브랜치를 만들고 변경 이유를 이슈에 적습니다.
2. 라이브러리는 카탈로그만 수정하고 Firebase 라이브러리는 BoM에서 함께 관리합니다. `+`, `latest.release`, SNAPSHOT은 사용하지 않습니다.
3. 잠금 파일을 재생성합니다. `dependencies`로 모든 구성을 해석한 뒤 실제 빌드에서도 잠금을 갱신합니다.

```sh
./gradlew :app:dependencies --write-locks
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --write-locks
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

4. 카탈로그와 잠금 diff를 함께 검토합니다. 일반 기능 PR에서는 `--write-locks`를 사용하지 않습니다.
5. Gradle 변경은 공식 배포 SHA-256을 확인하고 Wrapper 파일 전체를 갱신합니다. AGP 호환 표에 맞춰 JDK·Gradle·SDK를 함께 검토합니다.
6. 개인 Gradle 캐시 삭제나 다른 팀원의 버전 변경으로 문제를 우회하지 말고, CI 로그와 `teamDoctor` 결과를 공유합니다. 키 값이나 개인 설정 파일은 공유하지 않습니다.
