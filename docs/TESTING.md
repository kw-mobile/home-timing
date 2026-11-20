# 변경별 검증과 결과 기록

## 필요한 검증 선택

| 변경 | 최소 확인 |
| --- | --- |
| 문서·이슈/PR 템플릿·CLAUDE/AGENTS 지침만 변경 | 링크·import·문서 간 지침 정합성, `git diff --check` |
| 순수 Java 제품 규칙 | 관련 단위 테스트와 아래 기본 빌드·Lint |
| UI·리소스 | 기본 빌드·Lint, 변경 화면의 기기 동작·글자 크기·상태 확인 |
| Repository·저장 구조·권한 | 기본 검증, 실패·동시 변경·권한 거부 등 관련 계약 테스트 |
| Firestore 보안 규칙·`firebase.json` | 아래 [규칙 테스트](#firestore-규칙-테스트)의 허용·거부 테스트, 해당 CI 결과 |
| 알림·권한·공유 | 기본 검증과 해당 실기기/두 기기 시나리오 |
| 도구·SDK·의존성 | SETUP의 잠금 갱신 절차 후 일반 기본 검증 |
| Notion 자동화 | `python -m unittest discover -s scripts/notion/tests -v`, 실제 API 검증은 [연결 안내](report/NOTION_SYNC.md) |
| GitHub workflow | `actionlint`로 구문 확인, 변경한 동작은 해당 CI 결과 확인 |

순수 문서 변경에 Android 빌드를 요구하지 않습니다. 구현을 그대로 반복하는 테스트보다 날짜 경계·동시 완료·권한 거부·네트워크 실패처럼 결과가 달라지는 조건을 확인합니다.

회귀 테스트는 사용자 작업 흐름의 결과가 갈리는 경계에 둡니다. 이번 백엔드 무결성 변경은 등록→완료→수정, 삭제 시작→동시 완료 거부→중단→재시도, 계정·집 전환 중 늦은 알림 응답을 검증합니다. 함수마다 구현을 반복하는 테스트를 추가하지 않습니다. 보안 후속 변경은 탈퇴·참여의 두 순서, 두 기기의 삭제·복원, 회차 ID 조작 후 정상 완료, 초대 제한과 직접 참여 우회, 같은 계정 재로그인·집 이동·겹친 복원, 설정 저장 직후 오프라인과 중복 알림 방지를 흐름으로 묶습니다. Firestore 전환 도구 변경은 `python3 -m unittest discover -s scripts/firestore/tests -v`로 중단·재시도와 고아 데이터 보존을 확인합니다.

## 기본 명령

JDK·SDK 준비는 [SETUP](SETUP.md)을 따릅니다. Windows에서는 `./gradlew.bat`를 사용합니다.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

JUnit 클래스만 빠르게 확인할 때는 `./gradlew :app:testDebugUnitTest --tests '전체.패키지.TestClass'`로 범위를 좁힙니다. 관련 테스트 성공과 전체 기본 검증은 구분해 기록합니다.

## 실제 MVP 화면·두 계정 통합 검증

`MvpFlowInstrumentation`은 추가 테스트 SDK 없이 실행하는 Android instrumentation입니다. `connectedAndroidTest`의 JUnit 개수와 혼동하지 않고 역할별 최종 `MVP owner/member PASS`를 확인합니다. UI의 가입·등록·수정·완료 버튼과 ViewModel/Repository를 함께 사용합니다. 화면 이동 일부는 테스트가 ViewModel 경로를 정하고 Activity를 재생성합니다. 모든 화면을 사람이 터치한 검증이나 실기기 검증을 뜻하지 않습니다.

1. 전용 Android 에뮬레이터 두 대를 준비합니다. 다른 작업의 AVD·데이터·서비스를 초기화하지 않습니다.
2. 저장소 밖 임시 Firebase config에 `firestore.rules`의 현재 절대 경로와 Auth·Firestore의 비어 있는 loopback 포트를 설정하고 `firebase emulators:start --only auth,firestore --project demo-home-timing --config <임시-config>`로 실행합니다. 기존 서비스가 포트를 쓰면 다른 포트를 고르고 runner 인자에 같은 값을 넘깁니다. 규칙 테스트는 같은 DB를 비우므로 아래 통합 검증과 동시에 실행하지 않습니다.
3. 실제 팀 설정 파일이 있는 환경에서 `./gradlew -PrequireFirebase=true :app:assembleDebug :app:assembleDebugAndroidTest`를 실행하고 두 전용 기기에 app/debug APK와 androidTest/debug APK를 설치합니다. 테스트 runner는 시작할 때 Firebase 기본 앱을 `demo-home-timing`과 `10.0.2.2`의 명시한 포트로 교체합니다. 일반 APK에는 emulator 전환 진입점을 넣지 않습니다. debug network 설정의 평문 예외도 loopback/Android emulator host에 한정하며 release에는 포함하지 않습니다.
4. 첫 터미널에서 owner를 실행합니다. 예시 serial/포트는 자신의 전용 기기로 바꿉니다. `runId`는 매 실행 새 값으로 두 터미널에 동일하게 전달합니다.

```sh
adb -s emulator-5570 shell am instrument -w -e role owner -e runId mvp-unique-run \
  -e authPort 19099 -e firestorePort 18080 \
  com.kwmobile.hometiming.test/com.kwmobile.hometiming.ui.main.MvpFlowInstrumentation
```

5. owner가 `INSTRUMENTATION_STATUS: invite=......`를 출력하면 두 번째 터미널에서 member를 시작합니다. 초대 코드는 이 로컬 테스트 실행에서 출력된 값만 사용합니다.

```sh
adb -s emulator-5572 shell am instrument -w -e role member -e runId mvp-unique-run \
  -e invite <owner의-6자리-코드> -e authPort 19099 -e firestorePort 18080 \
  com.kwmobile.hometiming.test/com.kwmobile.hometiming.ui.main.MvpFlowInstrumentation
```

두 역할은 새 테스트 계정의 빈 집에서 시작해 용품 등록/완료/이력·공유 수정·오래된 revision 거부·집안일 등록/이번 회차 변경/다음 주 회차·개인 알림 설정·공용 지역·집 이동·재로그인·자신의 계정 정리를 확인합니다. 실패하면 마지막 step과 앱 로그를 확인하고, adb 프로세스 종료 코드 0만으로 통과 처리하지 않습니다. 테스트 계정은 성공 시 정리되지만 실패 시 남을 수 있습니다. 공용 프로젝트나 다른 작업의 데이터를 일괄 초기화하지 않습니다.

공용 Firebase를 확인할 때만 `-e service configured -e projectId <의도한-공용-project-id>`와 `mvp-production-`로 시작하는 새 runId를 명시합니다. 이 모드는 Firebase 기본 앱을 교체하지 않습니다. 프로젝트 ID가 다르거나 기존 로그인 계정이 있으면 작업 전에 거부하므로 **본인 개인 설치 대신 새 전용 테스트 설치에서만** 실행합니다. 테스트는 자신이 만든 두 계정·초대·집만 사용합니다. 모의 Firebase 성공과 공용 Firebase 성공을 따로 기록합니다.

물리 기기의 알림 도착·재부팅·동시 완료 경쟁·release 업데이트 설치, 비밀번호 재설정 메일 수신, 글자 크기/다크/가로 화면은 해당 기기에서 별도 확인하고 미실행 상태를 유지합니다.

## 날씨 화면 연결 검증

- 키 없는 빌드에서 홈·날씨에 설정 안내·기온 ‘—’가 보이고 18° 같은 예시 값이 나오지 않는지 확인합니다.
- 설정 → 날씨 설정에서 테스트 모드를 켜면(일반 release에도 명시적 테스트 모드 제공) 테스트임이 표시되고 기온·3일 예보·세탁 판정이 연결되는지, 다시 끄면 실제 모드로 돌아가는지 확인합니다. 이는 실제 API 성공이 아닙니다.
- 집 지역을 바꾸고 화면 이동·재생성·앱 재시작과 다른 구성원의 기기에서 공용 지역이 유지되는지, 이전 지역의 늦은 결과가 새 지역에 표시되지 않는지 확인합니다.
- 실제 API허브 키와 활용신청 완료 후 재빌드하여 지역·예보 대상 시각·조회 시각·출처를 확인합니다. 실패·6시간 이내 이전 캐시·누락 기온·예보 시간 경계·테스트 모드는 별도로 기록합니다. 키·요청 URL은 근거에 넣지 않습니다.
- `WeatherDisplayTest`와 날씨 파서·로더 테스트는 과거/다음 날 기온 오표시 방지, 공식 최저/최고 누락, 음수·소수 기온, SKY/PTY 우선순위, 캐시 직렬화와 오류를 검증합니다. 네트워크 성공·실기기 검증은 별도입니다.

### 현재 위치로 날씨 지역 찾기

계정 없는 전용 API36 AVD에서 `HouseLocationInstrumentation`(대략적 위치·수동 선택)과 `DongLocationInstrumentation`(정확한 GPS·주소)을 사용합니다. 기본 실행은 집/계정을 메모리에만 주입하며 Firebase 저장을 호출하지 않습니다. 실기기나 개인 계정이 있는 설치에서 실행하지 않습니다.

1. `-PrequireFirebase=true -PdeviceTestRunner=com.kwmobile.hometiming.ui.main.DongLocationInstrumentation :app:assembleDebug :app:assembleDebugAndroidTest`로 빌드해 두 APK를 설치합니다. OS mock 제공자는 `appops set com.kwmobile.hometiming android:mock_location allow`로 허용하고, 위치를 켭니다. coarse/fine 권한을 둘 다 허용한 상태에서 `am instrument -w com.kwmobile.hometiming.test/com.kwmobile.hometiming.ui.main.DongLocationInstrumentation`을 실행합니다. 모의 GPS+합성 주소로 동 확인창·정확도 개선 대기·주소 오류/8초 제한·늦은 결과·화면 중지/세션 전환을 검사합니다. `-e mode backend`는 별도로 공개 서울 좌표를 실제 Android 주소 서비스로 변환합니다. 제공자/네트워크 상태에 따라 실패할 수 있고 모의 성공으로 대체하지 않습니다.
2. coarse만 허용한 뒤 `HouseLocationInstrumentation`을 실행해 network/fused 각각의 후보, 범위 밖·20초 미수신·세션 변경을 확인합니다. 권한 설명에서는 ‘구 단위로 찾기’를 선택합니다. `-e mode manual`은 시·도 → 시·군·구 → 확인/취소, 위치를 끈 `-e mode disabled`와 권한을 철회한 `-e mode denied`는 안내/요청 미시작을 검사합니다. denied는 영어 시스템 권한 창의 거부 버튼을 사용합니다. 반복 검증 시 전용 설치의 `user-set/user-fixed` 권한 플래그를 초기화하여 실제 권한 창을 구분합니다.
3. 실제 팀 Firebase 저장은 `DongLocationInstrumentation -e service configured -e projectId <의도한-프로젝트> -e runId mvp-production-<새ID>`로만 실행합니다. 프로젝트/빈 로그인 설치를 확인하고 자신이 만든 임시 계정·집만 사용합니다. GPS와 주소는 모의이지만 확인 버튼 → 실제 서버 재조회 → 자동 날씨 갱신·비테스트 예보를 검사합니다. 성공/실패 후 자신의 계정·집을 정리하며, 미완료 정리를 통과로 쓰지 않습니다. 기상청 키를 로그·원본 URL에 남기지 않습니다. 중단된 자신의 검사만 복구할 때는 같은 프로젝트를 지정한 `-e mode identify`로 이 runner의 임시 run ID를 확인하고, 그 정확한 ID와 `service configured`를 지정한 `-e mode cleanup`을 사용합니다. 임의 계정은 거부하며, 서버 삭제가 이미 완료된 계정은 `ERROR_USER_NOT_FOUND` 확인 뒤 남은 로컬 세션만 비웁니다.
4. runner가 생성한 mock 제공자를 제거하고 위치/권한/mock app-op을 원복합니다. ADB 종료 코드가 아닌 출력의 `HOUSE LOCATION PASS`/`DONG LOCATION PASS`와 개별 시나리오를 확인합니다. 확인창·수동 선택·홈·날씨의 테스트 스크린샷은 앱 files에 저장할 수 있으며 생산 사진 저장 기능과 무관합니다.

실기기는 정확한 위치/대략적인 위치를 구분해 허용하고 현재 위치 버튼 → 주소·후보까지의 시간 → 집 위치 확인/취소 → 저장 후 홈·날씨 자동 갱신을 기록합니다. 실내/실외·주소 서비스 미지원·권한 거부·기기 위치 꺼짐을 구분합니다. 법정동과 행정동이 다를 수 있고, 모의 좌표나 실제 Geocoder/HTTP200으로 실기기 GNSS 수신·정확도를 증명하지 않습니다. API26~32의 legacy 주소 경로는 별도 기기 검증 대상입니다. 실제 좌표·도로명·번지·키는 로그/PR에 넣지 않습니다.

## 식품안전나라 바코드 API 개별 확인

[로컬 I2570 조회 도구](../scripts/foodsafety/README.md)를 사용합니다. 기본 번호는 `4902430597241`이며 `.env.foodsafety.local`에 본인 키를 넣고 실행합니다. 모의 응답 테스트와 실제 인증키 조회 결과를 구분합니다. 제품 앱 등록·사진 인식의 구현을 뜻하지 않습니다.
## Firestore 규칙 테스트

`firestore-rules` 모듈의 JUnit 테스트가 Firestore emulator의 REST API로 `firestore.rules`를 검증합니다. 외부 라이브러리나 npm 패키지 없이 JDK `HttpClient`만 씁니다. 테스트는 항상 `demo-home-timing` emulator 프로젝트에서만 실행되며 공용 Firebase 프로젝트에 접근하지 않습니다.

준비: [Firebase CLI](SETUP.md#firebase)와 emulator용 Java 21 이상. Firebase CLI 15의 emulator는 Java 21이 필요하지만 Gradle은 계속 JDK 17을 씁니다. `JAVA_HOME`은 JDK 17로 두고, CLI가 찾는 `PATH`의 `java`만 21로 앞에 둡니다. Android Studio에 포함된 JBR이 Java 21입니다.

Windows PowerShell:

```powershell
$env:PATH = "C:\Program Files\Android\Android Studio\jbr\bin;" + $env:PATH
firebase emulators:exec --only firestore --project demo-home-timing ".\gradlew.bat :firestore-rules:rulesTest"
```

macOS (Android Studio 기본 설치 경로):

```sh
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
  firebase emulators:exec --only firestore --project demo-home-timing "./gradlew :firestore-rules:rulesTest"
```

- emulator 없이 `:firestore-rules:rulesTest`를 실행하면 통과가 아니라 실패합니다. 기본 `test` 태스크는 꺼져 있어 `./gradlew test`·`build`에서는 SKIPPED로 표시됩니다.
- 거부는 HTTP 403, 허용은 200으로 판정합니다. 형식이 잘못된 요청(400)은 거부로 세지 않습니다.
- 테스트 사용자는 서명 없는 emulator 전용 토큰(`Auth.user(uid)`)을 씁니다. 데이터 준비·확인은 규칙을 우회하는 `Auth.admin()`으로만 하고, 검증 대상 요청에는 쓰지 않습니다.
- 일괄 쓰기·트랜잭션 규칙(`getAfter()`)은 `FirestoreEmulator.commit(...)`으로 한 번에 커밋해 검증합니다. 각 테스트 전에 emulator 데이터를 비웁니다.
- CI의 `Firestore rules` 워크플로가 규칙·설정·모듈 변경 PR에서 같은 테스트를 실행합니다.

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

## Release APK 검증

키 준비·배포 절차는 [SETUP](SETUP.md#release-apk)을 따릅니다.

- 키 없는 환경에서 기본 debug 빌드·단위 테스트·Lint가 성공하는지, `:app:assembleRelease`는 서명 설정 누락으로 실패하는지 확인합니다.
- 로컬 서명으로 `:app:assembleRelease :app:testReleaseUnitTest :app:lintRelease`를 실행하고 SDK의 `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`로 유효한 서명과 공개 인증서 SHA-256을 확인합니다. SDK 경로·Build Tools 버전은 각 환경과 카탈로그를 사용합니다.
- `aapt dump badging`으로 applicationId·versionName·versionCode를 확인하고 `application-debuggable`이 없는지 확인합니다. 공개 인증서 지문만 기록하고 개인 키·비밀번호는 출력하지 않습니다.
- `actionlint .github/workflows/android-release.yml .github/workflows/android.yml`로 구문을 확인합니다. 잘못된 태그, main 외 커밋, 누락 secret, 빌드/서명 실패에서는 게시 단계에 도달하지 않아야 합니다.
- 실제 배포 후 Actions URL·태그·커밋·APK 해시·인증서 지문을 기록하고 다운로드한 SHA-256과 APK를 비교합니다. 이전 release APK와 동일 서명으로 업데이트 설치되는지는 별도 기기 검증입니다.
- 로컬 성공과 실제 태그 배포 성공은 구분합니다. PR 검증은 비밀값을 사용하거나 Releases에 게시하지 않습니다.

## 산출물과 CI

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Lint: `app/build/reports/lint-results-debug.html`
- JUnit: `app/build/reports/tests/testDebugUnitTest/index.html` (테스트가 있을 때 생성)
- Android verification CI는 Wrapper·도구 설정 후 키 없는 debug 빌드·단위 테스트·Lint를 실행합니다. 별도 Android release APK 워크플로는 main에 포함된 커밋의 버전 태그에만 서명 release 빌드·단위 테스트·Lint·서명 검증 후 GitHub Releases에 게시합니다. 에뮬레이터 행렬은 구성하지 않습니다.
- Android 워크플로는 앱·Gradle·JDK 설정·해당 워크플로 변경에 실행합니다. 문서와 독립된 Notion 스크립트 변경에는 Android 빌드를 실행하지 않고, Notion 전용 검증 작업에서 Python 계약 테스트를 확인합니다. `workflow_dispatch`로 수동 실행할 수 있습니다. 혼합 PR은 PR 전체 변경에 코드가 포함되므로 문서만 추가 push해도 다시 실행될 수 있습니다.
- 이 워크플로를 추후 필수 검사로 지정한다면 경로 필터로 건너뛴 PR도 병합 가능한지 함께 검토합니다. 저장소 보호 설정은 이 문서로 변경되지 않습니다.

실제 Firebase·기상청·알림·두 기기 검증은 관련 기능이 구현된 뒤 별도 기록합니다. 기능별 필수 시나리오는 [MVP 완료 기준](MVP.md#구현-순서와-완료-기준)을 따릅니다.

## 휴대폰 캘린더 검증

기본 빌드·단위·Lint 외에 전용 테스트 APK를 빌드합니다.

```sh
./gradlew -PdeviceTestRunner=com.kwmobile.hometiming.data.calendar.CalendarProviderInstrumentation :app:assembleDebug :app:assembleDebugAndroidTest
```

개인 계정이 없는 전용 AVD에 두 APK를 설치하고 READ_CALENDAR/WRITE_CALENDAR를 허용한 뒤 `adb -s 전용AVD shell am instrument -w com.kwmobile.hometiming.test/com.kwmobile.hometiming.data.calendar.CalendarProviderInstrumentation`을 실행합니다. 고유 테스트 로컬 캘린더만 생성·정리하며 실제 Provider의 생성/날짜 변경/중복/완료/삭제/개인 일정 보존/UTC 종일/알림 없음 검사를 수행합니다.

`-e service configured -e projectId kw-mobile-home-timing`를 추가하면 실제 설정 프로젝트에 고유 검증 계정·집·항목을 생성해 등록→주기 수정→완료→삭제→연동 해제까지 확인하고 자기 데이터만 탈퇴 정리합니다. 기존 로그인 계정이 있거나 프로젝트가 다르면 거부합니다. 기본 Provider 검사와 실제 Firebase 검사는 구분해 기록합니다. 실기기에서는 설정의 권한 거부/재허용·대상 계정/캘린더 삭제·연동 끄기·로그아웃/집 이동·백그라운드 지연을 추가 확인합니다.
## 사진 입력 OCR 하위 경로 회귀 검증

```sh
./gradlew -PdeviceTestRunner=com.kwmobile.hometiming.ui.main.OcrPipelineInstrumentation :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

전용 AVD에 두 APK를 설치하고 카메라 권한을 허용합니다. Wi-Fi/모바일 데이터를 끈 뒤 `adb -s 전용AVD shell am instrument -w com.kwmobile.hometiming.test/com.kwmobile.hometiming.ui.main.OcrPipelineInstrumentation`을 실행합니다. 합성 포장 이미지로 bundled 한글/라틴 모델 → 후보 추출을 검사하고, 이 실제 모델 결과를 후보 화면에 전달하여 명시적 적용·시작일/주기 유지·취소/늦은 요청/다른 초안 거부를 검사합니다. 이는 카메라 실물 인식률 검사가 아닙니다. AVD 카메라의 글자 없는 장면은 실패/수동 복귀만 확인할 수 있습니다. 권한 거부 후 직접 입력 복귀도 별도 확인합니다. 종료 후 전용 AVD의 네트워크를 원복합니다.

이 절의 합성 이미지는 OCR 하위 경로를 확인하며, 범용 사물 추론은 아래 #83 검증을 따릅니다.

사용자 실물 검증: 변경이 포함된 APK 설치 → 가입/로그인 → 용품 등록 → ‘사진으로 용품 찾기’ → 포장 앞면 촬영 → 후보 선택/수정 → ‘이 이름 사용’ → 날짜·주기 유지 확인 → 등록 → 재실행 후 서버 저장 확인. 카메라 권한 거부·취소·8초 제한·회전·백그라운드 복귀도 확인합니다. 개발용10개로 규칙을 조정한 뒤 다른20개 표본에서 MVP의 Gate(첫 사진 상위3 후보16개 이상·3초19개 이상)를 평가합니다. 기종/OS·앱 SHA·제품 표본·첫 시도/재촬영·시간·결과를 이슈 #79에 남깁니다. 실물 정확도·속도 목표는 아직 미검증이며, 사용자 요청으로 정식 진입을 허용한 결정과 구분합니다. v0.2.0에는 OCR 진입이 없습니다. 로컬 검증용 release APK는 공식 키가 아닌 테스트 키로 서명하므로 공식 설치본에 업데이트할 수 없습니다. 공식 배포는 기존 서명 키를 유지합니다.

정식 빌드 경로는 debug keystore를 로컬 release 테스트 서명 환경 변수에 지정하고 `-PrequireFirebase=true :app:assembleRelease :app:assembleDebugAndroidTest`를 빌드하여 전용 AVD에 release 앱과 debug 테스트 패키지를 설치합니다. 테스트 패키지와 대상 앱의 서명·패키지가 일치해야 하며, 대상 앱은 debuggable=false인지 확인합니다. 별도 release 테스트 의존성·잠금 변경은 하지 않습니다. `MvpFlowInstrumentation`의 `-e role ocr`가 실제 계정 가입→용품 편집의 OCR 진입→후보 확인·적용→원격 저장→자신의 계정·집 정리를 검사합니다. release는 평문 emulator 예외를 추가하지 않으므로 앞 절의 `service configured`, 프로젝트 ID, `mvp-production-` runId 보호를 반드시 사용합니다. 테스트 키는 공식 배포 키로 취급하지 않습니다.

이 시나리오는 합성 이미지의 실제 bundled 한글·라틴 인식 결과를 후보 화면에 주입하며 실물 포장·카메라 촬영 정확도를 증명하지 않습니다. 카메라 준비/재생성 후 재연결·AVD 장면의 실제 셔터/이미지 변환·인식 완료, 결과 선택의 회전 보존, 8초 제한, 명시적 적용 전 폼 유지, 날짜/주기 보존, 취소와 다른 초안의 늦은 결과 거부, 집안일 미노출을 확인합니다. `-e cameraDenied true`에서는 OS 카메라 권한을 거부한 뒤 직접 입력 복귀·초안 보존을 확인합니다. 계정 정리 전 실패했다면 테스트 run의 본인 계정만 확인해 정리하며 공용 데이터를 일괄 삭제하지 않습니다.

오프라인 모델만 검사할 때는 테스트 패키지의 runner를 `-PdeviceTestRunner=com.kwmobile.hometiming.ui.main.OcrPipelineInstrumentation`으로 빌드하고, 전용 AVD에서 네트워크를 끈 뒤 `-e modelOnly true`로 실행합니다. 새 설치 데이터에서 실행하며 끝나면 네트워크를 원복합니다. 이 검사는 release 앱에 debug 테스트 패키지를 붙여도 가능하며 실제 포장 정확도를 뜻하지 않습니다. 중단된 OCR 계정은 동일한 configured-project/run 보호를 적용한 `-e role ocr-cleanup`으로만 정리할 수 있습니다. 테스트가 만든 정확한 이메일과 로그인 계정이 일치해야 하며 서버 계정이 이미 삭제된 경우에만 남은 로컬 세션을 정리합니다.

## 범용 사물 사진 입력 검증 (#83)

LiteRT 실행 의존성과 모델만 추가했으므로 debug/release 빌드·단위·Lint를 수행하고 기존 잠금 버전이 유지되는지 비교합니다. 모델 파일 SHA와 출처·Apache-2.0 고지는 assets/licenses에 보존합니다.

`-PdeviceTestRunner=com.kwmobile.hometiming.ui.main.ProductPhotoInstrumentation :app:assembleDebugAndroidTest`로 테스트 패키지를 빌드합니다. 같은 로컬 테스트 키로 서명한 release 앱에 설치할 수 있습니다. 검증 사진은 저장소에 넣지 않으며 COCO 공식 val2017의 `000000365385.jpg`(칫솔), `000000311303.jpg`(컵), `000000167353.jpg`(시계)를 별도로 받아 test 패키지의 private files에 전달합니다. 이 사진은 COCO 데이터에 속하는 알려진 예시이고 실제 이용자 정확도 평가용 독립 표본이 아닙니다. 원본 Flickr 출처·CC BY/CC BY-SA 라이선스·사진 ID는 검증 영수증에 남깁니다.

Wi-Fi/데이터를 끈 전용 AVD에서 `am instrument -w com.kwmobile.hometiming.test/com.kwmobile.hometiming.ui.main.ProductPhotoInstrumentation`을 실행합니다. 실제 이미지→CPU 모델→한국어 일반명을 검사하고 빈 사진, 전체 사물+OCR 칫솔 경로와 OCR 일반명 보조를 확인합니다. `MvpFlowInstrumentation role=ocr`는 기존과 같이 후보의 명시적 적용·취소/다른 초안·날짜/주기 보호·실제 저장을 확인합니다. 코드·모델이 바뀐 뒤 오래된 인식 결과를 현재 성공으로 재사용하지 않습니다. 카메라·물리 기기·새 품목 성능은 해당 기기/독립 표본에서 따로 확인합니다.

## APK 앱 내 업데이트 검증

- `UpdateInfoTest`로 고정 HTTPS 저장소/파일명·정수 코드·해시·크기/범위와 같은/낮은 코드를 검사합니다. `python3 -m unittest discover -s scripts/release/tests -v`와 actionlint로 배포 메타데이터의 파일/버전/해시 계약과 workflow를 확인합니다. debug/release의 빌드·단위·Lint를 수행합니다.
- 계정 없는 전용 API36 AVD의 `UpdateFlowInstrumentation`은 기본 versionCode1의 debug 앱과, 같은 로컬 테스트 키로 서명한 비디버그 0.4.1/code2 fixture를 사용합니다. 공식 키/정식 배포를 대신하지 않습니다. fixture는 외부 저장소가 아닌 대상 앱 private files/fixtures의 valid.apk와 official-old.apk(다른 공식 서명)로 전달합니다. 올바른 새 APK 수락, 다른 서명·버전 불일치·변조 거부, 저장한 파일 재검사, 자동 확인 설정/Activity 재생성, 설치 허용 취소 시 session 미생성을 검사합니다. 다음 프로세스의 `-e mode tamper`는 저장한 파일을 바꾼 뒤 재검사/삭제되는지 확인합니다. 실제 설치를 commit하지 않습니다.
- 실제 공식 배포는 metadata/APK/sha256·기존 인증서·증가한 코드·비디버그·Firebase/날씨 구성을 확인합니다. 업데이트 기능이 포함된 이전 공식 APK를 전용 AVD에 설치한 후, 최신 릴리스 자동 감지 → 다운로드 취소/재시도 → 해시/서명 검사 → 설치 허용/시스템 확인 또는 허용된 자동 설치 → 실제 설치된 최신 versionCode를 확인합니다. 실기기/OEM·API26~30의 설치 확인 경로·실사용 로그인 상태 유지는 별도로 기록합니다.
- 공식 APK는 debug 테스트 패키지와 서명이 다르므로 무단 instrumentation으로 검증하지 않습니다. 전용 AVD의 실제 화면/패키지 버전을 확인하고 개인 기기·타인 앱 데이터나 공용 서비스 데이터를 초기화하지 않습니다.

## 연결 화면의 세부 디자인 점검 (#95)

일반 실행의 빈 계정 로그인에서 보조 업데이트/복구 버튼·불필요한 상단 바가 없는지, 입력 오류·회원가입·비밀번호 보기가 유지되는지 확인합니다. 저장된 계정 복구 실패는 별도 상태로 구분합니다. 실제 화면 바인더에 메모리 전용 집/구성원/사진 후보를 넣어 설정 섹션·업데이트 진입·초대 코드 입력·후보 선택/직접 수정/적용 활성 상태를 점검할 수 있으며, 이는 실제 계정·Firebase 저장·사진 추론의 근거가 아닙니다.

캘린더 초기 화면의 요약/선택과 전체 안내의 닫기, 업데이트 현재/새 버전·자동 확인·버튼 위계, 사진의 준비/결과/권한 오류를 확인합니다. 날씨 로딩/데이터 없음은 빈 최고/최저·강수와 같은 안내를 반복하지 않아야 합니다. 기본/작은 폰·시스템 큰 글씨·가로·태블릿·라이트/다크·애니메이션 축소를 구분해 캡처하고, 활성 화면의 포커스를 확인한 뒤 글자 잘림과 48dp 버튼·스크롤 도달을 확인합니다. 물리 기기/TalkBack 실제 읽기·기존 로그인 유지·서버 저장·실물 인식은 별도입니다. 임시 UI 검사 도구와 환경/화면/결과는 연결 PR의 검증 영수증에 보존하며 공용 데이터를 초기화하지 않습니다.
