# 운영 보안 확인 결과

이슈 [#59](https://github.com/kw-mobile/home-timing/issues/59)의 읽기 전용 확인 결과입니다. 코드·규칙 흐름 검증과 운영 서비스 설정의 확인 범위를 구분합니다. 기준 소스는 `da35612`, 의존성 기준은 그 시점의 `app/gradle.lockfile`입니다. 후속 변경은 해당 PR의 최종 검증 결과를 따릅니다.

## 실제 Firebase 설정 조회

최신 확인은 **2026-11-12 11:59–12:03 UTC**, 사용자가 지정해 인증한 기존 Firebase CLI 계정으로 수행했습니다. 명령별 계정만 선택했으며 기본 계정·배포·데이터·IAM·API 키·App Check 설정을 변경하지 않았습니다. 토큰·개인 계정·API 키 문자열은 출력하지 않았습니다.

| 확인 대상 | 실제 조회 결과 | 판단 |
| --- | --- | --- |
| 게시된 Firestore 규칙 | 릴리스·원문 조회 HTTP 200, 갱신 시각 `2026-11-11T06:03:03.222266Z` | 공백 정규화 후 현재 `origin/main` 및 `37d492e4ca94b8edc4f3610a44a53058e813bb2e` 규칙과 일치. PR #58·후속 보안 규칙과는 다름 |
| Authentication | HTTP 200. 이메일/비밀번호 활성, 비밀번호 필요, 익명 로그인 활성 설정 없음, 이메일 열거 보호 활성, MFA `DISABLED` | MVP 이메일 인증 경로와 일치. MFA 추가는 이번 범위에서 변경하지 않음 |
| 비밀번호 정책 | 관리자 설정의 `passwordPolicyConfig` 없음. 별도 정책 조회는 HTTP 401 `UNAUTHENTICATED` | 사용자 정의 정책은 확인되지 않음. 실제 최소 길이·강도 정책을 별도 API로 확인했다고 쓰지 않으며 콘솔 확인 필요 |
| Firestore·Auth App Check | 목록 HTTP 200·설정 0개. 두 서비스의 개별 GET HTTP 200이며 `enforcementMode` 미설정 | 공식 enum 계약에 따라 기본 `OFF`. `UNENFORCED` 모니터링이나 `ENFORCED` 상태가 아님 |
| Android 앱 | HTTP 200. `com.kwmobile.hometiming`, 상태 `ACTIVE` | 예상 패키지 일치 |
| 등록 서명 인증서 | HTTP 200, SHA 인증서 **0개** | 허용할 debug·release 인증서 목록을 먼저 확보해야 함. 게시할 지문 없음 |
| Android API 키 제한 | 메타데이터 HTTP 200, Android 애플리케이션 제한의 허용 목록 비어 있음, API 대상 27개 | 패키지·서명에 결합된 제한 없음. 키 문자열 조회는 하지 않음 |
| Browser API 키 제한 | 메타데이터 HTTP 200, 허용 referrer 목록 비어 있음, API 대상 27개 | 허용 출처 제한 없음. 실제 사용처 확인 전 키 폐기·차단하지 않음 |
| 프로젝트 IAM | HTTP 200, `allUsers`·`allAuthenticatedUsers` 할당 없음 | 직접 프로젝트 정책에서 공개 주체는 발견되지 않음. 상위 리소스·그룹의 전체 유효 권한 검증과는 구분 |
| 팀 전용 역할 | HTTP 200, 권한 777개, 사용자 2명에 할당. Firebase Viewer도 사용자 2명에 할당 | DB·Auth 삭제 권한 포함. 조회한 목록에는 `iam.*`, `billing.*`, `setIamPolicy` 권한 없음. 데이터 관리자 권한의 필요성은 팀 역할과 함께 검토 |

App Check의 미설정 기본값은 [공식 EnforcementMode 문서](https://firebase.google.com/docs/reference/appcheck/rest/v1/EnforcementMode)를 따릅니다. `ENFORCED`는 유효한 토큰이 없는 요청을 거부하므로 정상 클라이언트 준비 전에 켜면 안 됩니다.

게시된 규칙 원문 SHA-256은 `06cb4c77b8a1a806ba8b587cf8310c08c4b92fa2dff3545c8ffc127e81b2e16a`, 공백 제거 후 SHA-256은 `bb590ddb471b3b6ffefafcaaca06886e761e4eac8542a5842306765f6512eb70`입니다. 원문 해시만 비교하면 줄바꿈·서식 차이를 코드 불일치로 오인할 수 있어 Git 이력의 규칙도 공백 정규화해 비교했습니다. 현재 main과는 일치하지만 `integrityVersion`·`recordCount`와 계정/집 수명 보강이 들어간 PR #58·후속 브랜치는 아직 배포되지 않은 상태입니다. 코드 병합·배포·관리자 데이터 전환은 각각 구분합니다.

프로젝트 IAM의 직접 할당은 개인 Owner 1명, 전용 팀 역할 사용자 2명, Viewer 사용자 2명과 서비스 계정 역할들입니다. `roles/iam.serviceAccountTokenCreator` 할당은 서비스 계정 1개이며 사람의 직접 할당은 아니었습니다. 개인 이메일·서비스 계정 이름은 공용 문서에 남기지 않습니다. IAM 역할이 있다는 사실만으로 앱 Security Rules를 통과한다고 판단하지 않습니다.

### 실행 전 검토할 운영 변경안

다음은 **준비할 변경안**이며 이번에 실행한 설정 변경이 아닙니다.

1. **규칙·데이터 전환:** PR #58과 후속 보안 수정의 최종 조합을 먼저 검토·병합합니다. 백업·쓰기 중단 시점을 정하고 새 규칙 게시→대상 커밋/해시 확인→관리자 읽기 검사→승인된 전환→정상 등록·참여·탈퇴 확인 순서로 진행합니다. 현재 게시 규칙에 새 전환 도구를 먼저 적용하지 않습니다.
2. **Android 인증서·키 제한:** 배포할 debug·release 서명에서 승인된 SHA-1/SHA-256 지문 목록을 확보합니다. 등록 인증서가 0개라 현재 정보만으로 특정 지문을 임의 등록할 수 없습니다. 그 목록과 패키지를 기준으로 Android 키의 허용 앱을 구성하고 가입·로그인·토큰 갱신·Firestore가 유지되는지 확인한 뒤 제한을 적용합니다.
3. **Browser 키·API 허용 목록:** 브라우저 키의 실제 사용처와 허용 referrer를 확인합니다. 미사용 키라면 사용량·연결을 확인한 뒤 폐기 여부를 판단합니다. 두 키의 API 대상 27개 중 사용하는 서비스 목록을 검토하고 정상 경로 검증 후 줄입니다. API 키 제한이 Security Rules·IAM의 대체는 아닙니다.
4. **App Check:** 먼저 SDK/provider 구성, 승인된 서명 인증서, debug 테스트 경로와 실제 기기 attestation 성공을 준비합니다. Play Integrity 설정 GET은 HTTP 200·토큰 TTL 3600초였지만 이것만으로 실제 attestation 성공을 증명하지 않습니다. 정상 트래픽을 확인하고 모니터링 단계를 거쳐 Firestore 강제를 검토합니다. Auth 강제는 해당 서비스·요금제·클라이언트 조건을 별도로 확인하며 임의로 켜지 않습니다.
5. **Auth·IAM:** 이메일 열거 보호는 유지합니다. 비밀번호 정책의 실제 최소 길이는 콘솔에서 확인하고 앱 검증과 맞춥니다. 관리자 역할 변경은 실제 팀 업무·위험을 검토한 별도 승인 대상이며 이번 조회에서 축소하거나 확대하지 않았습니다.

### 이전 조회와 인증 경위

2026-11-12 11:37 UTC의 기본 계정 조회는 위 6개 메타데이터 API와 DB 목록에서 403을 받았습니다. 당시 gcloud 토큰 발급도 실패했습니다. 11:58 UTC에는 사용자 지정 계정이 이 기기의 CLI에 없어서 그 계정의 API를 호출하지 못했습니다. 사용자가 추가 인증을 마친 뒤 11:59 UTC 이후 명령별 계정 선택으로 다시 조회해 위 성공 결과를 얻었습니다. 과거 실패를 성공으로 바꾸지 않고 최신 결과와 구분합니다.

추가 조사에서 quota-project 헤더를 붙인 App Check·IAM 요청은 `SERVICE_DISABLED`로 거부됐으나, 일반 API 요청은 성공했습니다. 이는 일반 요청으로 읽은 실제 서비스 설정을 무효화하는 결과가 아니며 App Check 서비스 조회 실패로 잘못 보고하지 않습니다. 별도 비밀번호 정책 API의 401은 남은 확인 한계입니다.

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

수정 대상은 카탈로그의 Guava와 생성된 잠금 파일입니다. [SETUP의 버전 변경 절차](../SETUP.md#의존성버전-변경)에 따라 별도 버전 변경 작업에서 `33.7.2-android` 이상으로 패치하고, 잠금을 직접 편집하지 않으며 빌드·단위 테스트·Lint로 확인합니다. 패치는 [이슈 #60](https://github.com/kw-mobile/home-timing/issues/60) / [PR #61](https://github.com/kw-mobile/home-timing/pull/61)에서 별도로 작성했습니다. 최종 적용 여부와 검증은 그 PR이 기준입니다. 패치 SHA는 `e72e078adf51447f5e3cb5da5996c992fced346c`이며 로컬 빌드·단위 255·Lint 및 [호스팅 Android 검사](https://github.com/kw-mobile/home-timing/actions/runs/43211598239)가 통과했습니다. 패치 후 출시 런타임 147개 좌표 재조회에서 OSV 매칭은 0이었습니다. 기능 PR은 이 패치 브랜치를 base로 사용하므로, 의존성 diff는 #61에서 분리해 검토합니다.

### 도구 전용 매칭

나머지 매칭은 잠금 파일의 `_internal-unified-test-platform-*` 구성에 있는 Guava JRE, protobuf-java/protobuf-kotlin, Netty 모듈입니다. 이 버전들은 출시 APK의 `releaseRuntimeClasspath`에 포함되지 않습니다. 대표적으로 protobuf-java 3.24.4의 [공식 보안 권고](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8)가 매칭됐습니다.

이 결과를 앱의 원격 취약점 42건으로 보고하거나, 내부 도구 의존성을 앱의 직접 의존성으로 덮어쓰지 않습니다. 도구 사용 경로·공격 노출과 수정된 AGP/도구 조합을 별도 버전 변경에서 검토합니다. 이번 읽기 전용 확인에서는 도구 취약점의 실제 악용을 재현하지 않았습니다.

## 남기는 근거와 제한

로컬 상세 결과는 최초 조회 `/tmp/home-timing-operational-metadata.json`, 지정 계정 조회 `/tmp/home-timing-operational-selected-account.json`, 개별 서비스·서명·IAM·규칙 비교 `/tmp/home-timing-operational-selected-extras-noquota.json`, 의존성 `/tmp/home-timing-osv-results.json`에 있습니다. 운영 결과에는 설정 메타데이터와 해시·권한 종류만, 의존성 결과에는 공개 패키지 좌표와 OSV 권고문만 포함합니다. 토큰·키·개인 계정을 담은 원본 CLI 출력은 공유하지 않습니다.

이 확인은 원격 보안 설정을 변경하지 않았고 Android 빌드나 Firestore 규칙 게이트를 실행하지 않았습니다. 실제 서비스 설정은 위 성공 범위에서 확인했으나 비밀번호 정책 별도 API, 실제 attestation·서명 제한 적용·실기기 동작은 미검증입니다. 소스의 방어 코드와 운영 설정 적용을 각각 확인해야 합니다.
