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

## Android Studio 동기화와 의존성 잠금

Studio Sync에서 `debugRuntimeClasspathCopy` 같은 임시 구성은 잠금 파일에 저장되지 않습니다. `app/build.gradle`은 `idea.sync.active=true`인 동기화에만 `LockMode.DEFAULT`를 사용해 기존 잠금 검증을 유지하면서 임시 구성의 잠금 정보 누락을 허용합니다. 일반 빌드·테스트·CI는 계속 `LockMode.STRICT`이며 잠금 파일을 재생성하거나 의존성 버전을 바꾸지 않습니다. IDE 도구 업그레이드로 해결하려 하지 말고 먼저 Sync 결과를 확인합니다.

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
| 앱 데이터 접근 | `firestore.rules`([허용 범위](DATA_MODEL.md#보안-규칙-범위)). 실제 프로젝트에 배포된 커밋과 시각은 규칙을 바꾼 작업 이슈의 배포 기록이 기준입니다(v1 [#28](https://github.com/kw-mobile/home-timing/issues/28), v2 [#38](https://github.com/kw-mobile/home-timing/issues/38) `0026bf3` 2026-11-11, 탈퇴 규칙 [#40](https://github.com/kw-mobile/home-timing/issues/40), 집안일 규칙 [#44](https://github.com/kw-mobile/home-timing/issues/44)) |

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

설정 파일이 있으면 Google Services 플러그인이 적용됩니다. 없으면 기본 화면 빌드만 가능합니다. 일반 실행은 `BuildConfig.FIREBASE_CONFIGURED`가 false일 때 로그인 설정 안내를 표시하고 실제 작업은 실패로 처리합니다.

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

기상청 [API허브](https://apihub.kma.go.kr/)에 가입해 **각자** 인증키(`authKey`)를 발급받고, [예·특보 API](https://apihub.kma.go.kr/apiList.do?seqApi=10)의 단기예보 탭에서 "4.3 단기예보조회"(`VilageFcstInfoService_2.0/getVilageFcst`)를 **활용신청**합니다. 신청하지 않은 키는 HTTP 403("활용신청이 필요한 API")으로 거부됩니다. 공공데이터포털 키가 아닙니다.

`local.properties.example`을 참고해 자신의 `local.properties`에 `KMA_SERVICE_KEY`로 인증키를 넣습니다. 변수 이름은 기존 설정을 유지하며 값은 API허브 `authKey`입니다. Android Studio가 만든 `sdk.dir`을 덮어쓰지 마세요. URL 인코딩은 네트워크 계층에서 처리합니다. CI나 터미널에서는 같은 이름의 환경 변수로 주입할 수 있고 환경 변수가 우선합니다.

키를 로컬에 저장한 뒤 앱을 다시 빌드·설치해야 `BuildConfig.KMA_SERVICE_KEY`에 반영됩니다. Sync만 하거나 실행 중인 앱을 새로고침하는 것으로는 키가 바뀌지 않습니다. 앱의 설정 → 날씨 설정에서 테스트 모드를 끄고, 날씨 지역을 선택해 조회합니다. 일반 실행의 지역은 로그인한 집에 저장되어 가족에게 공유됩니다. debug 디자인 미리보기의 지역만 기기 로컬에 저장합니다. 키 없음·권한 거부·조회 실패 때는 예시 기온으로 대체하지 않습니다. 실제 성공 여부는 화면의 기상청 예보·조회 시각과 지역을 확인해 기록하고 테스트 모드 성공과 구분합니다.

키는 `BuildConfig.KMA_SERVICE_KEY`로 제공됩니다. Git에서 제외해도 APK에 넣은 키는 비밀 저장소처럼 보호되지 않습니다. HTTP 로그에는 키가 포함된 URL을 남기지 않아야 합니다. `data/weather`는 HTTP 로깅을 설치하지 않고, 실패 예외에 원인 예외를 담지 않습니다.

PC에서 같은 앱 설정으로 제공자 응답만 확인하려면 JDK 17 터미널에서 `./gradlew.bat -I scripts/weather/check-connection.init.gradle :app:checkWeatherConnection --console=plain -q`를 실행합니다(macOS/Linux는 `./gradlew`). 이 수동 진단은 키·요청 URL·원문 응답을 출력하지 않습니다. 명령 종료 코드가 0이어도 API 성공을 뜻하지 않으며 출력의 HTTP 상태와 `KMA_00`을 함께 확인합니다. `HTTP 403` / `HUB_403` / `API_APPLICATION_REQUIRED`가 나오면 활용신청 화면의 승인 계정·대상 API와 현재 키의 연결 상태를 API허브에서 확인합니다. 키를 교체했다면 앱을 다시 빌드·설치합니다.

## Release APK

[Android release APK](../.github/workflows/android-release.yml)는 main에 포함된 커밋의 `vX.Y.Z` 태그를 push하면 서명 APK와 SHA-256 파일을 GitHub Releases에 게시합니다. 일반 브랜치·PR에서는 배포하지 않습니다. 배포 APK는 `GOOGLE_SERVICES_JSON`·`KMA_SERVICE_KEY` secrets를 주입하고 `-PrequireFirebase=true`로 빌드합니다. 설정이 없으면 게시 전에 실패합니다. 빌드에 포함된 설정은 실제 서비스/API 성공의 증명이 아니므로 배포 노트에 실제 검증 결과를 적습니다. 태그와 릴리스는 버전마다 새로 만들고 기존 게시물을 덮어쓰지 않습니다. Play Store 배포는 포함하지 않습니다.

Firebase/날씨 설정은 현재 프로젝트의 `app/google-services.json`과 `local.properties`의 `KMA_SERVICE_KEY`를 확인한 뒤 GitHub Secrets의 `GOOGLE_SERVICES_JSON`·`KMA_SERVICE_KEY`로 등록합니다. 파일/키 내용을 저장소·이슈·로그에 출력하지 않습니다. 서명 secret 네 개는 아래 기존 절차를 유지합니다. 2026-11-14 진단에서는 기존 로컬 키가 HTTP 401 / HUB_401 / KEY_REJECTED였으므로 실제 날씨 확인에는 유효한 키가 필요합니다.

### 로컬 키와 서명 빌드 (Windows)

JDK 17을 `JAVA_HOME`으로 지정하고 프로젝트 루트에서 실행합니다.

```powershell
./scripts/release/signing.ps1 -Action CreateKey
./scripts/release/signing.ps1 -Action Build -VersionName 0.1.0 -VersionCode 2
```

- 첫 명령은 `.signing/home-timing-release.jks`(RSA3072, alias `hometiming`, 10,000일)와 `password.dpapi.xml`을 생성합니다. 기존 키/비밀번호 파일이 있으면 덮어쓰지 않습니다.
- 비밀번호는 난수로 생성하며 해당 Windows 사용자·PC에서만 복호화되는 DPAPI로 저장합니다. 디렉터리 권한도 현재 사용자로 제한합니다. 키·비밀번호는 Git과 로그에 넣지 않습니다.
- 장기 보관할 키는 별도 안전한 저장소에 백업하고 비밀번호는 비밀번호 관리자에 보관하세요. **DPAPI XML을 복사하는 것만으로 다른 PC에서 복구할 수 없습니다.** 현재 PC에서 비밀번호 관리자로 옮길 때만 아래 명령을 직접 실행합니다. 채팅·이슈·스크린샷에 붙여넣지 않고, 붙여넣은 뒤 클립보드를 비웁니다.
- `-SigningDirectory`로 기존 키 디렉터리를 지정할 수 있습니다. worktree 안에 키를 만들었다면 안전한 별도 백업을 확인하기 전 그 worktree를 삭제하지 않습니다.

```powershell
(Import-Clixml .signing/password.dpapi.xml).GetNetworkCredential().Password | Set-Clipboard
# 비밀번호 관리자에 저장한 다음:
Set-Clipboard -Value ''
```

서명 빌드는 네 환경 변수 `RELEASE_STORE_FILE`·`RELEASE_STORE_PASSWORD`·`RELEASE_KEY_ALIAS`·`RELEASE_KEY_PASSWORD`를 사용합니다. 스크립트는 값을 현재 프로세스에만 주입하고 종료 시 복원합니다. 다른 OS에서는 같은 환경 변수를 지정하고 Wrapper로 빌드합니다. 키가 없으면 release 빌드는 실패하며 debug 빌드·IDE Sync는 영향을 받지 않습니다. 서명 산출물은 `app/build/outputs/apk/release/app-release.apk`입니다. debug 설치본과 서명 키가 달라 그대로 업데이트 설치할 수 없습니다.

### GitHub 최초 준비

1. 저장소 `Settings → Secrets and variables → Actions`에 다음 Repository secrets를 등록합니다. 암호화된 로컬 키를 그대로 사용하며 새 키로 교체하지 않습니다.

| 이름 | 값 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | JKS 파일의 Base64 인코딩 |
| `ANDROID_STORE_PASSWORD` | 키 저장소 비밀번호 |
| `ANDROID_KEY_ALIAS` | `hometiming` |
| `ANDROID_KEY_PASSWORD` | 생성 시 저장소와 같은 비밀번호 |

현재 PC에서는 GitHub CLI 인증 후 아래 명령으로 값을 콘솔에 출력하지 않고 stdin으로 등록할 수 있습니다. 기존 이름이 하나라도 있으면 서명 키 교체를 피하기 위해 중단하므로 기존 키와 설정을 먼저 확인합니다. 중간 실패 시 이미 등록된 secret은 남을 수 있습니다.

```powershell
./scripts/release/signing.ps1 -Action UploadSecrets
```

CLI가 PATH에 없으면 `-GhPath 'GitHub CLI 실행 파일 경로'`를 추가합니다. fine-grained PAT에는 해당 저장소의 **Secrets: Read and write**가 필요합니다. 워크플로 파일 push에는 **Workflows: Read and write**와 **Contents: Read and write**가 필요합니다. 조직 승인이 필요한 경우 승인 후 진행합니다. 토큰은 스크립트·저장소에 저장하지 않습니다. Actions 실행 중에는 PAT 대신 작업의 `GITHUB_TOKEN`을 사용합니다.

2. 워크플로 PR을 검토·병합한 다음, 최신 main의 배포할 커밋에 아직 사용하지 않은 태그를 만듭니다. 아래 버전은 예시이므로 실제 릴리스 버전으로 바꿉니다.

```sh
git fetch origin main
git tag -a v0.1.0 origin/main -m "Home Timing v0.1.0"
git push origin v0.1.0
```

`versionName`은 태그의 `v`를 뺀 값이고 `versionCode`는 이 워크플로의 `github.run_number + 1`입니다(개발 기본값은 1). 배포 버전은 작업 실행 순서로 증가합니다. 워크플로를 삭제·재생성하거나 다른 경로로 전환해 번호가 초기화되면 기존 게시 APK의 코드보다 큰 값이 되도록 정책을 먼저 조정합니다. 같은 실행의 재실행은 같은 코드를 사용합니다.

3. Actions의 서명·단위 테스트·Lint·APK 검증이 모두 성공하면 Releases에서 `home-timing-vX.Y.Z.apk`와 `.apk.sha256`을 내려받습니다. 서명 키는 runner 임시 디렉터리에서만 복원하고 종료 시 삭제하며 캐시·배포 파일에 포함하지 않습니다. 첫 실제 게시 성공은 해당 Actions URL로 별도 확인합니다.

배포 빌드는 위에 등록한 Firebase/기상청 secrets를 읽습니다. 기본 PR CI는 이 secrets를 사용하지 않습니다. 서비스 설정과 실제 동작 검증은 위 [외부 서비스](#외부-서비스)를 따릅니다. 인증키가 거부되면 APK 빌드가 성공해도 날씨 조회는 실패하므로 배포 노트에 구분합니다. APK에 포함하는 API 키를 비밀 저장소로 취급하지 않습니다.

공식 기준: [Android 앱 서명](https://developer.android.com/studio/publish/app-signing), [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

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

## 운영 보안 점검

배포 규칙·Auth·App Check·IAM·키 제한과 잠금 의존성의 실제 확인 결과는 [운영 보안 확인](report/SECURITY_OPERATIONS.md)에 둡니다. 조회 거부는 미검증이며 설정 안전성을 뜻하지 않습니다. 공용 규칙 배포와 데이터 전환은 해당 문서의 결과 및 [데이터 모델 전환 절차](DATA_MODEL.md#무결성-필드-전환-이슈-57)를 검토한 뒤 수행합니다.

## 사진 OCR 실험 의존성

CameraX 1.4.2와 ML Kit bundled 한글/라틴 16.0.1을 카탈로그에 고정합니다. 무료 기기 내 OCR이며 키·외부 상품 DB·서버 OCR 설정은 없습니다. 모델은 APK에 포함되어 첫 실행 전 모델 다운로드가 필요하지 않습니다. 사진은 저장/전송하지 않으며 SDK 자체의 성능 지표 통신 가능성은 별개입니다. 기존 도구 버전을 업그레이드하지 않았으며 의존성 lock은 위 절차로 재생성합니다. debug APK에서만 사진 등록 실험에 진입하고 정식 채택은 MVP의 실물 Gate를 따릅니다.

### 앱 내 업데이트용 배포 정보

정식 배포 job은 서명/단위/Lint 검증 후 `scripts/release/create_update_metadata.py`로 `update.json`을 만들고 APK·sha256과 같은 릴리스에 게시합니다. versionName/versionCode/sizeBytes/sha256/apkUrl은 실제 산출물에서 생성하며 사람이 임의로 복사하지 않습니다. 자동 확인 주소는 `https://github.com/kw-mobile/home-timing/releases/latest/download/update.json`이며 공개 저장소라 토큰이나 추가 API 키를 앱에 넣지 않습니다. stable 릴리스를 사용하고 증가하는 기존 run_number 코드 정책을 유지합니다.

처음 이 기능을 포함한 버전은 GitHub 릴리스에서 직접 설치합니다. 이후 정식 APK는 실행 시 새 버전을 확인하고 로그인 화면·설정의 앱 업데이트에서도 확인할 수 있습니다. ‘다운로드 및 설치’의 명시적 요청 후 같은 패키지/서명/상위 코드를 확인하며, 기기에서 설치 허용 또는 확인이 필요할 수 있습니다. 외부 미검증 파일로 설치 화면을 우회하지 않습니다.
