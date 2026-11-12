# 운영 보안 확인 결과

이슈 [#59](https://github.com/kw-mobile/home-timing/issues/59)의 읽기 전용 확인 결과입니다. 코드·규칙 흐름 검증과 운영 서비스 설정의 확인 범위를 구분합니다. 기준 소스는 `da35612`, 의존성 기준은 그 시점의 `app/gradle.lockfile`입니다. 후속 변경은 해당 PR의 최종 검증 결과를 따릅니다.

## 실제 Firebase 설정 조회

2026-11-12 11:37 UTC에 기존 Firebase CLI 기본 계정으로 공용 프로젝트 `kw-mobile-home-timing`의 메타데이터만 조회했습니다. 로그인 추가·기본 계정 변경·배포·데이터 쓰기·IAM 변경은 수행하지 않았습니다. 토큰·개인 계정·API 키 값은 출력하지 않았습니다.

| 확인 대상 | 호출한 읽기 API | 실제 결과 |
| --- | --- | --- |
| 게시된 Firestore 규칙 | Firebase Rules `projects/releases/cloud.firestore` | HTTP 403 `PERMISSION_DENIED` |
| Authentication 설정 | Identity Platform `projects/config` | HTTP 403 `PERMISSION_DENIED` |
| App Check 적용 상태 | App Check `projects/services` | HTTP 403 `PERMISSION_DENIED` |
| 등록 Android 앱 메타데이터 | Firebase Management `projects/androidApps` | HTTP 403 `PERMISSION_DENIED` |
| API 키 제한 | API Keys `projects/locations/global/keys` (키 문자열 조회 제외) | HTTP 403 `PERMISSION_DENIED` |
| 프로젝트 IAM 정책 | Cloud Resource Manager `projects:getIamPolicy` | HTTP 403 `PERMISSION_DENIED` |

Firebase CLI의 대상 프로젝트 DB 목록 조회도 403이었습니다. 별도로 존재하는 gcloud 계정은 액세스 토큰을 발급받지 못했으며 재로그인하지 않았습니다. 결과는 설정을 읽지 못했다는 뜻입니다. 실제 규칙이 저장소와 같거나, App Check가 활성화됐거나, IAM·키 제한이 안전하다는 근거로 사용할 수 없습니다. 계정·권한 문제와 API 설정 문제를 이 조회만으로 단정하지 않습니다.

2026-11-12 11:58 UTC에 사용자가 지정한 계정으로 재시도를 준비했으나, 그 계정은 이 기기의 기존 Firebase CLI 계정 목록과 gcloud 계정 목록에 등록되어 있지 않았습니다. 따라서 해당 계정으로는 토큰을 발급하거나 위 6개 API를 호출하지 않았습니다. 이는 **지정 계정의 인증 부재**이며, 그 계정으로도 403을 받았다는 뜻이 아닙니다. 기존 기본 계정은 변경하지 않았고 추가 로그인도 수행하지 않았습니다. 지정 계정의 `firebase login:add` 인증이 끝난 뒤 명령별 `--account` 선택으로 읽기 전용 조회를 재실행해야 합니다. 로그인할 개인 계정 정보나 원본 계정 목록은 공용 문서에 기록하지 않습니다.

권한 있는 프로젝트 담당자가 기존 계정으로 다음을 확인하고 결과를 이슈에 연결해야 합니다. 이 문서는 새 권한을 부여하라는 지시가 아닙니다.

- 게시 규칙 원문 SHA-256과 검토한 Git 커밋의 규칙 SHA-256 비교. 코드 병합과 배포를 구분합니다.
- 이메일/비밀번호·익명 인증·이메일 열거 보호·비밀번호 정책과 팀이 의도한 가입 흐름 비교.
- Firestore 등 사용하는 서비스별 App Check 강제 상태와 정상 앱·debug 검증 경로 확인.
- 공개 주체(`allUsers`, `allAuthenticatedUsers`) 및 사람·서비스 계정 역할 범위 검토. DB 관리자 권한과 앱 Security Rules는 별개입니다.
- Android 앱용 Firebase 키의 애플리케이션/API 제한 및 기상청 키의 배포·폐기·호출량 정책 확인. APK에 포함한 키를 비밀 관리자 자격으로 취급하지 않습니다.

원격 설정 수정·규칙 배포·데이터 전환은 최종 코드와 이 확인 결과를 검토한 뒤 해당 작업의 승인 범위에서 진행합니다. 실패한 조회를 성공으로 기록하지 않습니다.

## 잠금 의존성의 취약점 조회

2026-11-12 11:37:49 UTC에 잠금 파일의 Maven 좌표·버전만 [OSV QueryBatch API](https://google.github.io/osv.dev/post-v1-querybatch/)로 조회했습니다. 프로젝트 데이터·자격 증명은 전송하지 않았습니다.

| 범위 | 실제 수 |
| --- | --- |
| 잠금 파일의 좌표·버전 조합 | 273 |
| `releaseRuntimeClasspath`에 포함된 조합 | 147 |
| OSV의 좌표·버전·권고문 일치 조합 | 84 |
| 중복 제거 권고문 | 42 |
| 출시 APK 런타임의 일치 좌표 | **Guava 32.1.3-android 한 개** |

이 숫자는 취약한 앱 기능의 수가 아닙니다. 같은 권고문이 여러 도구 버전에 중복 매칭됩니다. 패키지 버전 일치와 실제 공격 가능한 코드 경로를 나누어 판단합니다. OSV 조회 결과가 없다는 사실도 모든 취약점 부재를 보장하지 않습니다.

### Guava 런타임 보안 패치

`com.google.guava:guava:32.1.3-android`는 [GHSA-xxph-c9ww-hj94 / CVE-2026-102554](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94)의 영향 범위입니다. 조작된 Java 네이티브 직렬화 입력을 역직렬화하면 비정상적인 메모리 할당이 발생할 수 있습니다. 공식 [33.7.2 릴리스](https://github.com/google/guava/releases/tag/v33.7.2)는 Android 패키지에도 수정이 포함됐음을 명시합니다.

현재 앱에서 외부 입력을 `ObjectInputStream.readObject()`로 처리하는 경로는 찾지 못했습니다. 확인한 직렬화 복원은 앱 자신의 미리보기 저장 상태 `Bundle.getSerializable()`입니다. 따라서 **영향받는 패키지 포함은 확인했지만 원격 악용 경로를 재현했다고 주장하지 않습니다.** JSON·Firestore 파싱이 이 네이티브 직렬화 경로와 같다고 취급하지 않습니다.

수정 대상은 카탈로그의 Guava와 생성된 잠금 파일입니다. [SETUP의 버전 변경 절차](../SETUP.md#의존성버전-변경)에 따라 별도 버전 변경 작업에서 `33.7.2-android` 이상으로 패치하고, 잠금을 직접 편집하지 않으며 빌드·단위 테스트·Lint로 확인합니다. 패치는 [이슈 #60](https://github.com/kw-mobile/home-timing/issues/60) / [PR #61](https://github.com/kw-mobile/home-timing/pull/61)에서 별도로 작성했습니다. 최종 적용 여부와 검증 SHA는 그 PR이 기준이며 최종 SHA는 `e72e078adf51447f5e3cb5da5996c992fced346c`이며 로컬 빌드·단위 255·Lint 및 [호스팅 Android 검사](https://github.com/kw-mobile/home-timing/actions/runs/43211598239)가 통과했습니다. 패치 후 출시 런타임 147개 좌표 재조회에서 OSV 매칭은 0이었습니다. 본 기능 브랜치에는 의존성 변경을 섞지 않았습니다.

### 도구 전용 매칭

나머지 매칭은 잠금 파일의 `_internal-unified-test-platform-*` 구성에 있는 Guava JRE, protobuf-java/protobuf-kotlin, Netty 모듈입니다. 이 버전들은 출시 APK의 `releaseRuntimeClasspath`에 포함되지 않습니다. 대표적으로 protobuf-java 3.24.4의 [공식 보안 권고](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8)가 매칭됐습니다.

이 결과를 앱의 원격 취약점 42건으로 보고하거나, 내부 도구 의존성을 앱의 직접 의존성으로 덮어쓰지 않습니다. 도구 사용 경로·공격 노출과 수정된 AGP/도구 조합을 별도 버전 변경에서 검토합니다. 이번 읽기 전용 확인에서는 도구 취약점의 실제 악용을 재현하지 않았습니다.

## 남기는 근거와 제한

로컬 상세 결과는 `/tmp/home-timing-operational-metadata.json`, `/tmp/home-timing-osv-results.json`에 있습니다. 첫 파일은 상태·HTTP 코드만, 두 번째는 공개 패키지 좌표와 OSV 권고문만 포함합니다. 토큰·키·개인 계정을 담은 원본 CLI 출력은 공유하지 않습니다.

이 확인은 원격 보안 설정을 변경하지 않았고 Android 빌드나 Firestore 규칙 게이트를 실행하지 않았습니다. 실제 서비스 설정은 접근 제한으로 미검증이며, 소스의 방어 코드와 운영 설정 적용을 각각 확인해야 합니다.
