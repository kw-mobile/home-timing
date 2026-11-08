---
name: team-setup
description: Set up a home-timing clone on a teammate's machine when asked to set up, onboard, or prepare the development environment. Check JDK/Android SDK, preserve local settings, connect their Firebase account, and verify the bootstrap build. Use team-task-start for feature implementation.
---

# 팀 개발 환경 설정

“clone했으니 setup 해줘”, “개발 환경 설정해줘” 같은 요청에 적용합니다. [AGENTS](../../../AGENTS.md)와 [SETUP](../../../docs/SETUP.md)을 읽고 실제 로컬 준비를 진행합니다. 상세 버전·프로젝트·권한 기준은 SETUP과 아래 설정 파일을 따릅니다.

## 범위와 기존 작업 보존

- 레포 루트·OS·현재 브랜치·`git status --short --branch`를 확인합니다. 로컬 환경 설정만으로 이슈·브랜치·PR을 만들거나 개인 설정을 커밋하지 않습니다. 저장소 수정이 필요하면 [작업 시작](../team-task-start/SKILL.md)으로 별도 처리합니다.
- 깨끗한 main이면 `git pull --ff-only origin main` 후 HEAD와 `origin/main`이 같은지 확인합니다. 미커밋 변경·미게시 커밋·작업 브랜치는 보존하고 강제 switch/reset/stash로 최신화하지 않습니다. 공유 파일이 오래됐으면 그 사실을 보고하고 독립적으로 가능한 환경 점검을 계속합니다.
- `local.properties`, `app/google-services.json`, 기존 SDK/JDK·다른 프로젝트의 CLI 로그인을 보존합니다. 파일 전체를 출력하지 말고 필요한 경로·프로젝트 ID·패키지·키의 존재 여부만 로컬에서 확인합니다.
- 온보딩은 로컬 준비입니다. 공용 Firebase 프로젝트·DB를 새로 만들거나 IAM·결제·Auth 제공업체·서버 보안 규칙·공용 데이터를 변경하지 않습니다. `firebase init`, 서버 배포, 자동 전체 허용 규칙, 임의 키 생성은 이 작업에 포함하지 않습니다.

## 1. JDK·SDK·Wrapper 준비

- [.java-version](../../../.java-version), [버전 카탈로그](../../../gradle/libs.versions.toml), [Wrapper](../../../gradle/wrapper/gradle-wrapper.properties)를 읽어 설치된 JDK·Gradle 실행 Java·SDK Platform·Build Tools를 대조합니다. 전역 Gradle이나 IDE 제안 버전으로 바꾸지 않습니다.
- 호환 JDK가 이미 있으면 해당 경로를 이번 명령/세션의 `JAVA_HOME`으로 사용합니다. SDK는 기존 `sdk.dir`, `ANDROID_HOME`, Android Studio 설치 경로를 확인합니다. 다른 프로젝트의 전역 설정은 바꾸지 않습니다.
- 누락된 도구·SDK 패키지만 공식 배포/신뢰하는 패키지 관리자로 준비합니다. 전체 업그레이드·캐시 삭제로 해결하지 않습니다. 설치 권한·대화형 SDK 라이선스 동의·IDE 설치가 필요하면 사용자에게 정확한 해당 단계만 안내하고, 나머지 독립 작업은 계속합니다. 동의나 설치가 안 됐다면 완료라고 쓰지 않습니다.
- `local.properties`가 없을 때만 만들고 SDK 경로를 설정합니다. 이미 있으면 관련 키만 필요한 만큼 갱신하며 SDK 경로·기상청 키·주석 등 다른 내용을 보존합니다. [예시](../../../local.properties.example)로 파일 전체를 덮어쓰지 않습니다. Windows 경로는 SETUP의 슬래시 규칙을 따릅니다.
- `./gradlew --version`과 `./gradlew teamDoctor`로 실제 실행 환경을 확인합니다. Windows에서는 `./gradlew.bat`를 사용합니다. teamDoctor의 출력만으로 Firebase 연결이나 앱 기능 성공을 판단하지 않습니다.

## 2. 본인 Firebase 계정과 설정 연결

1. 프로젝트 ID는 [.firebaserc](../../../.firebaserc), Android 패키지는 [앱 설정](../../../app/build.gradle), Firebase Android 앱 ID는 SETUP에서 읽습니다. 개발자마다 새 프로젝트를 만들지 않습니다.
2. Firebase CLI가 없으면 [공식 설치 안내](https://firebase.google.com/docs/cli)를 따라 로컬에 준비합니다. 로그인 목록이 필요하면 **일반 `firebase login:list`만** 사용합니다. `login:list --json`, 토큰/설정 저장소의 직접 출력, `--debug`로 인증 정보를 노출하지 않습니다.
3. 초대받은 본인 Google 계정을 사용합니다. 여러 계정이거나 한 계정이어도 팀원 본인 계정인지 불확실하면 선택을 확인합니다. CLI에 로그인 계정이 없으면 `firebase login`, 다른 작업용 로그인이 있으면 `firebase login:add 본인Gmail`을 사용하고 기존 로그인을 제거하지 않습니다. 이후 모든 원격 명령에 `--project 프로젝트ID --account 본인Gmail`을 지정합니다. projects:list처럼 project 인자가 불필요한 명령은 account만 지정합니다.
4. 브라우저 로그인은 사용자가 자기 계정으로 완료하도록 인계합니다. 비밀번호·토큰을 대화로 받지 않습니다. 인증이 진행 중이면 도구·SDK·기본 빌드 등 독립 작업을 진행하고, 완료 후 중단한 연결 단계부터 재개합니다. 세션을 유지할 수 없으면 재개 명령과 실제 미완료 항목을 남깁니다.
5. SETUP의 프로젝트·DB·인덱스 조회 명령으로 접근을 확인합니다. 접근 거부는 계정·초대·프로젝트를 확인할 후속 항목으로 남깁니다. Owner 부여·권한 확대나 다른 계정 사용으로 우회하지 않습니다.
6. `app/google-services.json`이 이미 있으면 JSON을 로컬 파싱해 `project_info.project_id`와 `client[].client_info.android_client_info.package_name`이 레포 기준과 맞는지 확인합니다. Firebase 앱 ID도 일치하는 해당 Android client를 선택합니다. 맞는 파일은 그대로 재사용하고, 다른 프로젝트/패키지·유효하지 않은 JSON이면 보존한 채 설명합니다. 자동 덮어쓰기·가짜 파일로 빌드를 통과시키지 않습니다. 잘못된 파일이 있으면 그 파일이 적용되는 기본/Firebase 빌드 모두 차단된 것으로 보고합니다. 사용자가 원본을 레포 밖의 안전한 개인 경로에 보관하고 올바른 파일로 교체하도록 정확한 위치·다운로드 방법을 안내한 뒤, 해결되면 이 단계부터 재개합니다.
7. 파일이 없고 접근이 확인되면 `firebase apps:sdkconfig ANDROID 앱ID --out 임시파일 --project 프로젝트ID --account 본인Gmail`로 사용자 전용 임시 경로에 받습니다. 프로젝트·앱 ID·패키지를 검증한 후 목적 파일이 여전히 없을 때만 배치합니다. 저장 시에도 기존 파일을 덮어쓰지 않습니다. 성공·실패 후 이번에 만든 임시 파일만 정리하고 `git check-ignore app/google-services.json`으로 제외 여부를 확인합니다. 실제 설정 값·키는 출력하지 않습니다. CLI로 받을 수 없으면 SETUP의 콘솔 다운로드 위치를 안내합니다.

## 3. 기상청 키와 빌드 검증

- `KMA_SERVICE_KEY`는 환경 변수 우선, 없으면 기존 `local.properties` 값을 사용합니다. 키 값은 출력하지 않고 존재 여부만 확인합니다. 비어 있어도 기본 빌드는 진행하고, 실제 날씨 연결 미완료로 구분합니다.
- 키가 필요하면 사용자가 발급받은 Decoding 키를 자기 로컬 파일/환경 변수에 직접 넣도록 안내합니다. Git·이슈·PR·Notion·대화에 키를 올리게 하지 않습니다. 키 없는 상태를 실제 날씨 조회 성공으로 쓰지 않습니다.
- 올바른 Firebase 설정이 있으면 `./gradlew -PrequireFirebase=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, 파일이 없으면 같은 기본 작업을 requireFirebase 옵션 없이 실행합니다. 기존 파일이 잘못됐다면 파일을 제거하지 말고 Firebase 빌드 차단 원인을 남깁니다. 실패 로그에서 키·개인 정보를 노출하지 않습니다.
- unit test의 NO-SOURCE는 테스트 없음으로 기록합니다. 기본 빌드는 앱 로그인·집별 보안 규칙·실기기·실제 기상청 동작의 증명이 아닙니다.

## 완료 또는 재개 안내

JDK/SDK·기본 빌드·본인 Firebase 접근·설정 파일·날씨 키를 각각 **완료/미완료/미검증**으로 보고합니다. 사용한 레포 SHA와 검증 명령·결과, 보존한 기존 설정, 사람이 해야 할 정확한 다음 단계만 남깁니다. Android Studio의 Gradle JDK 선택은 직접 확인하지 않았다면 사용자 확인 항목입니다. 기존 작업/온보딩 이슈가 있을 때만 결과를 연결하고, 없으면 최종 메시지로 보고합니다. SETUP의 결과 기록 안내를 새 이슈 생성 지시로 해석하지 않습니다.

토큰은 로컬/인증 도구에만, Notion 자동화 토큰은 GitHub Actions에만 둡니다. 새 팀원 PC에 Notion 토큰이나 Firebase 서비스 계정 키는 필요하지 않습니다. 이후 기능 작업은 team-task-start로 시작합니다.
