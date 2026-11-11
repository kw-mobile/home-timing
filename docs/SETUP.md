# 팀 공통 개발 환경

## 버전 기준

| 항목 | 기준 파일 | 규칙 |
| --- | --- | --- |
| JDK | [`.java-version`](../.java-version) | 17 major 고정. 보안 패치는 허용하며 CI는 Temurin을 사용 |
| Gradle | [`gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties) | Wrapper와 배포 ZIP SHA-256을 함께 고정 |
| AGP·SDK·Build Tools·CI 설치 도구·라이브러리 | [`libs.versions.toml`](../gradle/libs.versions.toml) | 버전은 이 파일에서만 변경 |
| 전이 의존성 | [`app/gradle.lockfile`](../app/gradle.lockfile) | 생성된 해석 결과를 커밋하고 엄격한 잠금 적용 |

`settings-gradle.lockfile`도 카탈로그 해석 과정에서 생성되므로 함께 커밋합니다.

검증한 기준 조합은 AGP 8.11.1과 Gradle 8.13입니다. 공식 호환 표: [Android Gradle Plugin 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes). JDK 패치·OS·CPU와 Android Studio 버전까지 동일하게 만드는 구성은 아니며, 빌드 도구와 의존성 차이로 생기는 충돌을 줄이는 것이 목적입니다.

Android Studio는 AGP 8.11을 지원하는 Narwhal 2025.1.1 이상 안정 버전을 사용합니다. Studio 자체보다 **Gradle JDK와 Wrapper**가 빌드 기준입니다. 자동 AGP·Gradle 업그레이드는 수락하지 않습니다.

## 최초 설정

에이전트에게 “이 프로젝트 개발 환경 설정해줘”라고 요청하면 [team-setup](../.agents/skills/team-setup/SKILL.md)이 아래 절차를 수행합니다. 기존 로컬 파일·계정을 보존하며 인증·키 입력과 실제 미완료 항목을 구분합니다. 직접 설정하려면 아래 순서로 진행합니다.

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

공용 프로젝트는 **[Home Timing (`kw-mobile-home-timing`)](https://console.firebase.google.com/project/kw-mobile-home-timing/overview)**입니다. Spark 요금제이며 결제 계정은 연결하지 않았습니다. 팀원은 이 프로젝트를 함께 사용합니다.

| 설정 | 현재 상태 |
| --- | --- |
| Android 앱 | `com.kwmobile.hometiming` (`1:254704069106:android:b118b991eced1fbeb3236b`) |
| Authentication | 이메일/비밀번호 로그인 활성화 |
| Firestore | `(default)` / Standard Native / 서울 `asia-northeast3`, 무료 DB |
| 앱 데이터 접근 | 저장소는 `firestore.rules` v2([허용 범위](DATA_MODEL.md#보안-규칙-범위-v2)). 실제 프로젝트에는 v1(`335fb10`, 2026-11-10 콘솔 게시)이 적용되어 있고, v2 배포 결과는 [#38](https://github.com/kw-mobile/home-timing/issues/38)에 기록 |

#### 팀원 관리 권한

지정된 두 팀원은 프로젝트 전용 `homeTimingTeamAdmin`과 `roles/firebase.viewer`를 함께 사용합니다. 전용 역할은 Firebase Admin 기반에서 결제 변경·IAM 정책 변경과 커스텀 역할에 사용할 수 없는 권한을 제외한 것입니다. Firebase Viewer는 아래 CLI 명령이 사용하는 프로젝트·API 작업 조회 권한을 제공합니다. Owner 초대는 취소했습니다.

| 작업 | 권한 목록으로 확인한 범위 |
| --- | --- |
| DB | DB 설정·문서 읽기/쓰기·삭제·인덱스 관리 |
| 보안 규칙 | 규칙 조회·작성·릴리스 생성/수정 |
| Authentication | 인증 설정·사용자 관리 |
| CLI | 프로젝트·앱·API 키·서비스·API 작업 조회, 규칙 배포용 `serviceusage.services.use`(2026-11-11 추가, 아래) |
| 제외 | 결제 연결·요금제 변경·IAM 역할 변경·서비스 계정으로 권한 확대 |

2026-11-08 원격 정책에서 두 계정의 직접 역할 할당을 읽고, 두 역할의 합집합에 대상 작업의 권한 41개가 포함됨을 확인했습니다. 인덱스 권한의 현재 이름은 `datastore.schemas.*`이며 `datastore.indexes.*`는 이전 이름/별칭입니다. [Firestore 권한 기준](https://docs.cloud.google.com/firestore/native/docs/security/iam), [Firebase CLI 필수 권한](https://firebase.google.com/docs/projects/iam/permissions)을 참고합니다. 팀원 계정으로 직접 로그인한 CLI/콘솔 동작은 아래 순서로 각자 확인합니다. 권한 목록 확인을 실제 팀원 작업 성공으로 기록하지 않습니다.

`firebase deploy --only firestore:rules`는 배포 전에 Firestore API 사용 여부를 확인하며 `serviceusage.services.use`가 필요합니다. 처음에는 팀원 역할에 이 권한이 없어 2026-11-10 배포가 403으로 실패했고([#28](https://github.com/kw-mobile/home-timing/issues/28)), 2026-11-11 프로젝트 소유자가 추가한 뒤 전주용 계정의 `--dry-run`에서 API 확인과 규칙 컴파일이 통과했습니다([#32](https://github.com/kw-mobile/home-timing/issues/32)). 다른 팀원은 아래 `--dry-run`으로 자기 계정을 확인합니다. 이 권한은 API 사용만 허용하며 결제·IAM 변경은 포함하지 않습니다. 다시 403이 나면 팀원 역할로는 IAM을 바꿀 수 없으므로 프로젝트 소유자에게 `homeTimingTeamAdmin`의 `serviceusage.services.use`(또는 `roles/serviceusage.serviceUsageConsumer`)를 요청하고, 그동안 아래 콘솔 게시 절차를 씁니다.

콘솔·관리 API의 팀원 권한과 Android 앱 사용자의 Security Rules는 별개입니다. 팀원은 콘솔에서 DB를 관리할 수 있어도, 앱 요청은 게시된 `firestore.rules`가 허용하는 범위만 통과합니다. 개발 중에는 기존 공용 DB의 규칙을 전체 허용으로 바꾸지 않습니다.

#### 팀원 최초 연결과 확인

1. [Firebase CLI](https://firebase.google.com/docs/cli)를 설치합니다. 이 PC의 Firebase CLI에 로그인된 계정이 없으면 `firebase login`, 이미 다른 프로젝트용 계정이 있으면 `firebase login:add 팀원Gmail`로 팀 계정을 추가합니다. 다른 작업의 로그인을 지우지 않습니다.
2. 아래 명령의 `팀원Gmail`을 실제 초대받은 Google 계정으로 바꿔 실행합니다. 프로젝트·DB·인덱스 조회 성공 또는 오류는 기존 작업 이슈가 있으면 연결하고, 최초 로컬 setup이면 에이전트의 완료/미완료 보고로 남깁니다. 키·토큰을 출력하거나 `login:list --json` 결과를 공유하지 않습니다.

```sh
firebase projects:list --account 팀원Gmail
firebase firestore:databases:list --project kw-mobile-home-timing --account 팀원Gmail
firebase firestore:indexes --project kw-mobile-home-timing --account 팀원Gmail
```

3. Firebase Console의 프로젝트 설정 → 일반 → Android 앱에서 `google-services.json`을 내려받아 **`app/google-services.json`**에 둡니다. 파일은 Git에서 제외됩니다. `local.properties` 전체는 개인 SDK 경로·기상청 키가 있으므로 공유하지 않습니다.
4. `./gradlew -PrequireFirebase=true :app:assembleDebug`로 실제 설정이 있는 기본 빌드를 확인합니다. 이 옵션은 파일 존재를 검사하며 실제 가입·로그인·규칙·두 기기 공유 성공을 보장하지 않습니다.

설정 파일이 있으면 Google Services 플러그인이 적용됩니다. 없으면 기본 화면 빌드만 가능합니다. `BuildConfig.FIREBASE_CONFIGURED`로 기능 진입을 막고 설정을 안내하는 것은 후속 기능 구현의 책임입니다.

#### 규칙 수정과 배포

`.firebaserc`·`firebase.json`이 프로젝트와 규칙 파일을 지정합니다. 담당자는 저장소의 `firestore.rules`를 수정하고 허용·거부 테스트와 함께 PR로 남깁니다. 규칙 테스트는 로컬 emulator에서 `demo-home-timing` 프로젝트로만 실행하며 [검증 기준의 규칙 테스트](TESTING.md#firestore-규칙-테스트)를 따릅니다. emulator는 Java 21 이상이 필요하지만 `JAVA_HOME`은 JDK 17로 유지합니다. 승인된 변경을 main에 병합한 뒤 최신 main에서 아래 명령으로 **규칙만** 배포합니다. 콘솔에서 먼저 수정했다면 같은 내용을 저장소에도 반영해 서버와 Git을 일치시킵니다.

```sh
git switch main
git pull --ff-only origin main
firebase deploy --only firestore:rules --project kw-mobile-home-timing --account 팀원Gmail --dry-run
firebase deploy --only firestore:rules --project kw-mobile-home-timing --account 팀원Gmail
```

`--dry-run`은 게시하지 않고 권한·API 확인과 서버 규칙 컴파일만 검사합니다. 통과하면 마지막 명령이 로컬의 `firestore.rules`를 실제 프로젝트에 게시합니다. `serviceusage` 권한 오류(403)가 나면 위 팀원 권한을 확인하고, 해결될 때까지 콘솔 게시 절차를 씁니다. 미커밋 변경이 있으면 [작업 시작 절차](../.agents/skills/team-task-start/SKILL.md)에 따라 보존하고, `git diff 검토·병합한커밋SHA -- firestore.rules`로 검토된 정확한 커밋과 규칙 파일의 차이가 없는지 확인한 뒤 실행합니다. main 갱신이 실패하면 배포를 진행하지 않습니다. 결과는 이슈/PR에 배포 커밋·계정·시간·성공/실패로 남깁니다. 인덱스가 필요하면 먼저 인덱스 정의 파일을 추가하고 `firebase.json`에 연결해 같은 방식으로 관리합니다. 현재 MVP 쿼리는 [TRD 기반 범위](MVP.md)를 따라 필요한 인덱스만 추가합니다.

**콘솔 게시 (CLI 권한이 없을 때):** Firebase Console → Firestore Database → 규칙 탭에서만 게시하며, 내용은 병합된 커밋의 `firestore.rules`를 **한 글자도 바꾸지 않고** 통째로 넣습니다. 편집기에서 직접 고치지 않습니다.

1. 최신 main에서 `git diff 병합한커밋SHA -- firestore.rules`로 차이가 없는지 확인하고, 파일의 SHA-256을 기록합니다.
2. 편집기 내용을 파일 내용으로 바꾼 뒤, 게시 전에 편집기 내용이 파일과 같은지 확인합니다. 자동 들여쓰기·괄호 자동 완성이 내용을 바꿀 수 있으므로 직접 타이핑하지 않습니다.
3. **게시**를 누르고, 페이지를 새로 불러와 서버에서 읽은 규칙이 파일과 같은지, "게시되지 않은 변경사항"이 없는지 확인합니다. 반영에는 최대 1분이 걸릴 수 있습니다.
4. 이슈에 커밋·해시·게시 방법·계정·시간·성공/실패를 남깁니다.

### 기상청

`local.properties.example`을 참고해 자신의 `local.properties`에 `KMA_SERVICE_KEY`를 추가합니다. Android Studio가 만든 `sdk.dir`을 덮어쓰지 마세요. 공공데이터포털의 디코딩 키를 사용하며 URL 인코딩은 네트워크 계층에서 처리합니다. CI나 터미널에서는 같은 이름의 환경 변수로 주입할 수 있고 환경 변수가 우선합니다.

키는 `BuildConfig.KMA_SERVICE_KEY`로 제공됩니다. Git에서 제외해도 APK에 넣은 키는 비밀 저장소처럼 보호되지 않습니다. HTTP 로그에는 키가 포함된 URL을 남기지 않아야 합니다. 실제 API 호출·키 유효성 검증은 후속 날씨 기능 범위입니다.

## 검증 안내

최초 설정 후에는 기본 빌드를 확인합니다. 이후에는 [변경별 검증·결과 기록·산출물 안내](TESTING.md)를 따릅니다. 문서만 바꾸는 작업에는 Android 빌드를 요구하지 않습니다.

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
