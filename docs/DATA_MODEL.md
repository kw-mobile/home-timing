# Firestore 데이터 모델

[MVP의 데이터·권한·완료 계약](MVP.md#데이터권한-계약)을 Firestore 문서로 옮긴 설계 기준입니다. 구현 완료 목록이 아니며, 실제 저장·권한 동작은 `firestore.rules`·Repository 코드와 그 테스트로 확인합니다. 결정 경위는 [#22](https://github.com/kw-mobile/home-timing/issues/22)에 있습니다.

모든 제품 데이터는 **집(house)** 에 속합니다. 사용자 문서는 현재 집을 가리키는 포인터와 개인 설정만 가집니다. 서버 코드 없이(Spark 요금제) 권한과 불변식을 Firestore 보안 규칙과 트랜잭션·일괄 쓰기로 보장할 수 있는 구조를 택했습니다.

## 구조

```text
users/{uid}                          개인 설정·현재 집 포인터
houses/{houseId}                     관리자·인원·항목 수·지역
├─ members/{uid}                     구성원 (소속의 기준)
├─ categoryCatalogs/{item|chore}      종류별 공유 분류 이름·이동 대상
├─ items/{itemId}                    생활용품
│  └─ history/{occurrenceId}         교체 이력 (완료한 회차 ID = 문서 ID)
└─ chores/{choreId}                  주간 집안일
   └─ logs/{logId}                   완료·일정 변경 기록
invites/{code}                       초대 코드 (집 밖, 코드로 get만)
inviteLookups/{uid}                  짧은 초대 조회·발급 허가와 요청 창
accountDeletions/{uid}               삭제 재생성 방지용 영속 상태
```

용품과 집안일은 필드와 검증 규칙(주기 vs 요일)이 달라 컬렉션을 나눕니다. 화면은 항목 종류(`item`/`chore`)와 문서 ID로 상세를 엽니다. 한 컬렉션에 `kind` 필드로 합치는 안은 규칙이 종류마다 갈라져 복잡해지므로 택하지 않았습니다.

## 공유 분류

집마다 생활용품(item)과 집안일(chore) 분류 문서를 하나씩 저장합니다. `labels`는 활성 식별자와 이름, `redirects`는 삭제된 식별자와 이동 대상의 매핑입니다. `revision`과 변경 종류·대상·작성자·서버 시각을 같은 트랜잭션에서 갱신합니다. 구성원은 분류 한 개씩 추가·변경·삭제할 수 있고, 활성 분류를 하나 이상 남겨야 합니다. 종류별로 활성 식별자와 이동 기록을 합쳐 1,000개로 제한합니다.

신규 저장에는 `categoryId`와 표시 이름을 포함합니다. 집안일의 표시 분류는 날씨·반복 규칙에 쓰는 `type`과 별개입니다. 기존 항목의 분류명이나 집안일 종류는 고정 식별자로 읽으며, 이전 생활용품의 사용자 분류는 발견할 때 목록에 편입합니다. 삭제한 식별자는 다시 쓰지 않고 이동 대상으로 연결해 항목과 이력 문서를 그대로 보존합니다. 이전 앱에서도 기존 필드로 항목을 읽을 수 있지만 공유 분류 표시와 편집에는 새 앱이 필요합니다.

## 공통 규약

| 항목 | 규약 |
| --- | --- |
| 날짜 | 제품 날짜는 `yyyy-MM-dd` 문자열(Asia/Seoul). 변환은 `domain.DateRules`만 사용. 문자열 비교 = 날짜 비교 |
| 시각 | `createdAt`·`updatedAt`·`joinedAt`은 Firestore timestamp. 작성 시각은 서버 시각(`serverTimestamp`)이며 규칙에서 `request.time`과 비교 |
| 작성자 | `createdBy`·`updatedBy`·`completedBy`는 `request.auth.uid`와 같아야 함 |
| `occurrenceId` | 현재 회차의 무작위 ID. 등록과 완료 때만 새로 발급하고, 수정·일정 이동에는 유지 |
| `revision` | 1부터 시작. 일정·제안 상태 필드가 바뀔 때마다 정확히 +1. 이름·메모 같은 표시 필드만 바뀌면 유지 |
| 확정 | 등록·수정·삭제·완료·가족 변경은 온라인에서 서버 커밋이 성공해야 확정 |
| 선택 필드 | 비어 있으면 필드를 쓰지 않음(빈 문자열 저장 안 함) |

완료할 때 `occurrenceId`가 다르면 **이미 다른 사람이 완료**, 같은데 `revision`이 다르면 **그 사이 일정이 바뀜**으로 구분해 안내합니다.

## users/{uid}

본인만 읽고 씁니다. 이메일은 Auth에 있으므로 저장하지 않습니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `currentHouseId` | string | ✓ | 현재 집. 소속의 기준은 `members` 문서이며, 내보내지면 다음 접속에서 복구 |
| `displayName` | string | ✓ | 가족에게 보일 이름. 가입 화면에서 입력, 공백 제외 1~20자 |
| `notifyTime` | string | ✓ | 알림 시각 `HH:mm`, 기본 `09:00` |
| `advanceDays` | number | ✓ | 사전 알림 일수 0~30, 기본 3, 0이면 끔 |
| `createdAt`, `updatedAt` | timestamp | ✓ | |

## houses/{houseId}

구성원만 읽습니다. 관리자만 지역·초대를 바꿉니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `ownerId` | string | ✓ | 관리자 uid. 생성 후 변경 불가(관리자 양도는 P0 제외) |
| `memberCount` | number | ✓ | 1~6. 참여·나가기·내보내기와 같은 쓰기에서 ±1 |
| `itemCount`, `choreCount` | number | ✓ | 현재 항목 수. 등록·삭제와 같은 쓰기에서 ±1. 1인 집 참여 조건 검사용 |
| `integrityVersion` | number | ✓ | 새 집은 2. 구형 집은 관리자 전환 완료 뒤 2로 설정 |
| `closing` | boolean | ✓ | 새 집 false. 혼자인 관리자 탈퇴 시작 시 true로 바꾸고 되돌리지 않음. 구형 필드 부재는 false로 읽고 전환 도구가 채움 |
| `itemMutationId`, `choreMutationId`, `memberMutationId` | string | | 개수 변경과 함께 쓰는 대상 문서 ID. 집·대상 양쪽 규칙이 실제 생성·삭제를 확인 |
| `regionName` | string | | 표시용 시·군·구 또는 동·읍·면 주소(30자 이하). 관리자만 변경 |
| `nx`, `ny` | number | | 기상청 격자(1~149, 1~253). 지역을 정하기 전에는 없음 |
| `activeInviteCode` | string | | 지금 유효한 초대 코드. 관리자만 바꾸며, 재발급하면 이전 코드가 무효 |
| `createdAt`, `updatedAt` | timestamp | ✓ | |

규칙은 하위 컬렉션 문서 수를 셀 수 없으므로 개수 필드를 둡니다. 증감은 `FieldValue.increment`로 쓰며, 대상 ID가 가리키는 문서의 실제 생성·삭제를 양쪽 규칙에서 확인합니다. 한 집의 같은 종류 개수 변경은 한 커밋에 대상 하나만 허용합니다.

## houses/{houseId}/members/{uid}

문서가 있으면 구성원입니다. 규칙의 구성원 검사는 `exists(.../members/$(request.auth.uid))`로 합니다. 로그인할 때 내보내졌는지 확인하도록, 사용자는 구성원이 아니어도 **자기 uid의** member 문서는 `get`할 수 있어야 합니다(없으면 새 1인 집으로 복구).

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `displayName` | string | ✓ | 참여 시점의 이름 복사본 |
| `joinedAt` | timestamp | ✓ | |
| `inviteCode` | string | | 초대로 참여한 구성원만. 사용한 코드로, 규칙이 참여 시점의 초대 유효성을 확인 |

## houses/{houseId}/items/{itemId}

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | string | ✓ | 공백 제외 1자 이상, 60자 이하 |
| `category` | string | ✓ | 분류 코드(30자 이하), 기본 `ETC`. 코드 목록은 미정 |
| `startDate` | string | ✓ | 사용 시작일, 오늘(서울) 이후 불가 |
| `cycleDays` | number | ✓ | 1~730 |
| `lastReplacedDate` | string | ✓ | 최초에는 `startDate`. 완료마다 실제 교체일, 뒤로 갈 수 없음 |
| `nextDueDate` | string | ✓ | `lastReplacedDate + cycleDays`. 정렬·조회용 저장값 |
| `occurrenceId` | string | ✓ | 현재 회차(64자 이하) |
| `revision` | number | ✓ | `startDate`·`cycleDays`·`lastReplacedDate`·`nextDueDate`·`occurrenceId` 변경 시 +1 |
| `productName`, `location`, `memo` | string | | 선택 입력. 60·30·500자 이하 |
| `createdBy` | string | ✓ | 등록자. 삭제 권한(등록자·관리자) 판단, 변경 불가 |
| `updatedBy` | string | ✓ | |
| `createdAt`, `updatedAt` | timestamp | ✓ | |

### 용품·집안일 공통 이력 상태

`items`와 `chores` 부모에 다음 필드를 저장합니다. domain 표시 필드와 별개이며, 전체 문서를 다시 쓸 때도 `RecordState`가 보존합니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `recordCount` | number | ✓ | 실제 history/logs 개수. 새 항목은 0 |
| `deleting` | boolean | ✓ | 새 항목은 false. 삭제 시작 시 true로 바꾸며 되돌리지 않음 |
| `recordMutationId` | string | | 마지막으로 생성·삭제한 이력 ID. 한 커밋에 이력 하나의 증감만 허용 |

완료·일정 변경은 이력 생성과 부모 개수 +1을 함께 쓰고, 양쪽 규칙이 대상 ID를 확인합니다. 활성 항목에서는 이력을 지울 수 없습니다. 따라서 용품의 `hasCompleted`는 `recordCount > 0`에서 도출하며, 등록일과 같은 날 완료해도 완료 전으로 오인하지 않습니다. 삭제 중 문서는 일반 domain 목록·상세에서 제외하며, 삭제 API는 원본 문서로 정리를 재개합니다. 구형 부모는 읽을 수 있지만 새 필드가 없으면 변경은 거부합니다.

### items/{itemId}/history/{occurrenceId}

완료한 회차의 ID를 문서 ID로 씁니다. 같은 회차를 두 번 완료하면 문서 생성이 실패하므로 중복 완료가 서버에서도 막힙니다. 생성만 허용하고 수정하지 않습니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `replacedDate` | string | ✓ | 실제 교체일 |
| `previousDueDate` | string | ✓ | 완료 직전 예정일 |
| `cycleDaysUsed` | number | ✓ | 완료 시점의 주기 |
| `completedBy` | string | ✓ | 완료자 uid |
| `completedByName` | string | ✓ | 완료 시점 이름 복사본(구성원이 나가도 이력 표시 유지) |
| `createdAt` | timestamp | ✓ | |

## houses/{houseId}/chores/{choreId}

P0는 매주 요일 반복만 지원합니다(N일 간격은 P1).

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | string | ✓ | |
| `type` | string | ✓ | `BEDDING_WASH`, `OUTDOOR_DRY`, `VENTILATION`, `CLEANING`, `ETC` |
| `weatherSensitive` | boolean | ✓ | 날씨 제안 대상. 기본값은 침구 세탁·야외 건조만 true |
| `weekday` | number | ✓ | 1=월 … 7=일 |
| `plannedDueDate` | string | ✓ | 반복 규칙상 이번 회차의 원래 날짜 |
| `nextDueDate` | string | ✓ | 이번 회차 실행일. 날씨로 옮겨도 회차·`plannedDueDate`는 유지 |
| `occurrenceId` | string | ✓ | 현재 회차 |
| `revision` | number | ✓ | 일정 필드·`weatherSensitive`·`dismissedOccurrenceId` 변경 시 +1 |
| `dismissedOccurrenceId` | string | | "그대로 두기"한 회차. 새 회차에서는 제안을 다시 평가 |
| `lastDoneDate` | string | | |
| `location`, `memo` | string | | 선택 입력 |
| `createdBy`, `updatedBy` | string | ✓ | |
| `createdAt`, `updatedAt` | timestamp | ✓ | |

### chores/{choreId}/logs/{logId}

완료 기록의 문서 ID는 `done_<occurrenceId>`로 해 중복 완료를 막고, 일정 변경 기록은 `moved_<occurrenceId>_<변경 후 revision>`을 씁니다. ID가 정해져 있어 규칙이 날짜 변경에 기록이 동반됐는지 확인할 수 있습니다. 생성만 허용하고, 삭제 중인 집안일의 개수를 함께 줄일 때만 삭제합니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `action` | string | ✓ | `DONE` 또는 `RESCHEDULED` |
| `occurrenceId` | string | ✓ | 대상 회차 |
| `date` | string | ✓ | 완료일 또는 옮긴 날짜 |
| `fromDate` | string | | 옮기기 전 날짜(`RESCHEDULED`) |
| `reason` | string | | `RAIN_FORECAST`, `USER` |
| `actorId`, `actorName` | string | ✓ | 수행자 uid·이름 복사본 |
| `createdAt` | timestamp | ✓ | |

## invites/{code}

비구성원이 집을 읽지 않고 참여할 수 있도록 집 밖에 둡니다. 문서 ID가 코드입니다. 로그인 사용자는 아래 짧은 조회 허가를 먼저 받은 코드로 `get`할 수 있고 `list`는 거부합니다. 관리자는 현재 자기 집에 저장된 초대 코드도 조회할 수 있습니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `houseId` | string | ✓ | 참여할 집 |
| `createdBy` | string | ✓ | 발급한 관리자(= 그 집 `ownerId`) |
| `createdAt` | timestamp | ✓ | 발급 시각(서버). 만료는 이 시각 + 24시간이며 따로 저장하지 않음 |

- 코드는 `000000`~`999999` 중 `SecureRandom`으로 고른 6자리 문자열입니다.
- **한 코드로 24시간 동안 여러 명이 참여할 수 있습니다**(최대 6명, 2026-11-11 결정 [#36](https://github.com/kw-mobile/home-timing/issues/36)). 집의 `activeInviteCode`와 같아야 유효하므로, 재발급하면 이전 코드는 즉시 무효입니다.
- 이미 쓰는 코드면 다른 코드로 다시 고릅니다. 만료된 코드는 어느 집이든 새 초대로 덮어쓸 수 있어 코드가 고갈되지 않습니다.

## 가족 공유: 초대·참여·나가기·내보내기

모두 트랜잭션 하나로 커밋합니다. 서버 코드 없이 규칙이 `get()`·`getAfter()`·`existsAfter()`로 커밋 전후 상태를 비교해 정원과 소속 변경을 검증합니다. 아래 J는 참여자, H는 참여할 집, E는 J가 지금 가진 집, M은 나가거나 내보내지는 구성원입니다.

| 작업 | 함께 쓰는 문서 | 규칙이 확인할 것 |
| --- | --- | --- |
| 초대 발급·재발급 (관리자) | `invites/{새 코드}` 생성(또는 만료 코드 덮어쓰기), 집 `activeInviteCode` = 새 코드, 이전 `invites/{이전 코드}` 삭제 | 발급자 = 관리자, `createdAt` = `request.time`(만료는 규칙이 `createdAt` + 24시간으로 계산), 커밋 후 집의 `activeInviteCode` = 이 코드, 덮어쓰기는 기존 초대가 만료됐을 때만 |
| 초대 중지 (관리자) | 집 `activeInviteCode` 제거, `invites/{코드}` 삭제 | 관리자 |
| 참여 (J) | `H/members/J` 생성(`inviteCode` 포함), H `memberCount` +1, `users/J`의 `currentHouseId` = H, `E/members/J`·`E` 삭제, E에 초대가 있으면 그 초대 삭제 | 아래 참여 조건 |
| 나가기 (관리자가 아닌 M) | `H/members/M` 삭제, H `memberCount` −1, 새 1인 집 N·`N/members/M` 생성, `users/M`의 `currentHouseId` = N | M ≠ 관리자, 커밋 후 H 인원 = 이전 −1, N은 최초 로그인과 같은 새 집 조건 |
| 내보내기 (관리자) | `H/members/M` 삭제, H `memberCount` −1 | 요청자 = 관리자, M ≠ 관리자, 커밋 후 H 인원 = 이전 −1 |

**참여 조건** (member 문서 생성 규칙이 모두 확인):

- J 본인이 자기 member 문서를 만들고, 이름은 `users/J`의 `displayName`과 같습니다.
- `get(invites/{inviteCode})`의 `houseId` = H, `request.time` < `createdAt` + 24시간, H의 `activeInviteCode` = 그 코드.
- 참여 전 H 인원 < 6, 커밋 후 H 인원 = 이전 + 1. H 문서 갱신 규칙은 J의 member 문서가 커밋 전에는 없고 커밋 후에 있을 때만 +1을 허용합니다.
- E ≠ H이고 E가 같은 커밋에서 삭제됩니다. **E 삭제 규칙**: 요청자가 E의 관리자, `memberCount` = 1, `itemCount` = 0, `choreCount` = 0, 커밋 후 J의 E member 문서가 없고 `currentHouseId` ≠ E. 이것으로 MVP의 "항목·이력이 없는 1인 집에서만 참여"를 보장합니다(이력은 항목 아래에만 있음). 여러 명인 집의 구성원은 먼저 나가서 새 1인 집을 받은 뒤 참여합니다.

**member 문서 삭제 규칙**은 세 경우만 허용합니다: 관리자가 아닌 본인이 나가기(H 인원 −1 동반), 관리자 본인이 빈 1인 집을 없애기(같은 커밋에서 집 삭제), 관리자가 다른 구성원을 내보내기(H 인원 −1 동반). 관리자는 다른 구성원이 있으면 나갈 수 없습니다(관리자 양도는 P0 제외).

**집 문서 갱신 규칙**은 항목 개수 증감에 다음을 더합니다: 참여의 `memberCount` +1(위 조건), 나가기·내보내기의 −1(member 삭제 동반), 관리자의 `activeInviteCode` 변경(커밋 후 그 초대가 이 집을 가리킬 때) 또는 제거. 각 개수 변경은 해당 mutation ID도 함께 쓰고, 나머지 갈래는 해당 필드만 바꿉니다.

**앱의 확인 순서** (참여): 비구성원은 H를 읽을 수 없으므로 앱은 초대 문서와 자기 집 E만 읽어 미리 확인합니다. 코드가 없거나 만료면 "코드 확인", E가 1인 빈 집이 아니면 "먼저 나가기·정리" 안내를 보여줍니다. 정원 초과나 그사이 재발급은 서버만 알 수 있으므로 규칙 거부를 "참여할 수 없는 초대"로 안내합니다.

**내보내진 사용자**는 다음 로그인(또는 세션 복원)에서 member 문서가 없음을 확인하고 새 1인 집으로 복구됩니다(소속 복구, AuthRepository). 열려 있던 목록 리스너는 권한 거부를 받으므로 화면은 세션 복원을 다시 호출합니다.

**위험과 한계**

- 6자리 코드는 추측할 수 있으므로 계정별 조회·발급 허가로 대입 속도를 제한합니다. 여러 계정 생성이나 거부된 요청의 규칙 평가 비용까지 없애지는 못합니다. 실제 App Check·인증·키·쿼터 상태는 [운영 보안 확인](report/SECURITY_OPERATIONS.md)을 따릅니다.
- `memberMutationId`로 구성원 삭제와 개수 감소를 양쪽에서 확인합니다. 구성원 삭제 없는 감소와 한 번의 감소로 여러 구성원을 삭제하는 요청은 거부합니다.
- 앱이 서버 시각을 미리 알 수 없어 만료 시각을 쓰지 않고, 규칙이 서버의 `createdAt`으로 만료를 계산합니다([#38](https://github.com/kw-mobile/home-timing/issues/38)에서 설계 수정).
- 위 표는 `firestore.rules` v2와 `HouseRepository`로 구현되어 있습니다([#38](https://github.com/kw-mobile/home-timing/issues/38)).

## 함께 커밋하는 작업

등록·수정·완료·가족 변경의 문서들은 하나의 트랜잭션 또는 일괄 쓰기로 커밋합니다. 항목 삭제는 아래 정리 정책의 단계별 트랜잭션을 사용합니다. 규칙은 `getAfter()`·`existsAfter()`로 커밋 후 상태가 서로 맞는지 확인합니다.

| 작업 | 함께 쓰는 문서 | 규칙이 확인할 것 |
| --- | --- | --- |
| 최초 로그인 (트랜잭션) | `users/{uid}` 생성, `houses/{new}` 생성, `members/{uid}` 생성 | `ownerId`=본인, `memberCount`=1, 개수 0, `currentHouseId`=새 집 |
| 소속 복구 (트랜잭션) | `houses/{new}`·`members/{uid}` 생성, `users/{uid}`의 `currentHouseId`·`updatedAt` 갱신 | 위와 같은 새 집 조건, 사용자 문서는 그 두 필드만 변경 |
| 용품 등록 | `items/{id}` 생성, 집 `itemCount` +1 | 구성원, 필수 필드·주기 범위, `createdBy`=본인, `revision`=1 |
| 용품 수정 | `items/{id}` 갱신 | 구성원, `createdBy` 불변, 일정 필드가 바뀌면 `revision` +1 |
| 용품 완료 (트랜잭션) | `history/{현재 occurrenceId}` 생성, `items/{id}` 갱신 | 이력 ID = 직전 `occurrenceId`, 새 `occurrenceId` ≠ 이전, `revision` +1, 이전 교체일 ≤ 새 교체일 |
| 용품 삭제 | `history/*` 삭제, `items/{id}` 삭제, 집 `itemCount` −1 | 등록자 또는 관리자 |
| 집안일 완료 (트랜잭션) | `logs/done_<occurrenceId>` 생성, `chores/{id}` 갱신 | 위 용품 완료와 같은 회차·revision 검사, 다음 회차가 원래 날짜 + 7일의 배수이면서 완료일보다 뒤인 첫날 |
| 이번 회차 날짜 변경 (트랜잭션) | `chores/{id}` 갱신, `logs/moved_<occurrenceId>_<revision>` 생성 | `occurrenceId`·`plannedDueDate` 불변, `revision` +1, 오늘 이후, 기록의 이전·이후 날짜 일치 |
| 집안일 등록 (트랜잭션) | `chores/{id}` 생성, 집 `choreCount` +1 | 구성원, 허용 필드·종류, 요일과 원래 날짜 일치, `revision` = 1 |
| 집안일 수정 (트랜잭션) | `chores/{id}` 갱신 | 요일을 바꾸면 그 요일의 오늘 이후 날짜로 이번 회차를 다시 잡음, 일정·날씨 민감·그대로 두기가 바뀌면 `revision` +1 |
| 집안일 삭제 | `logs/*` 삭제, `chores/{id}` 삭제, 집 `choreCount` −1 | 등록자 또는 관리자 |

용품 등록·수정·완료·삭제는 모두 트랜잭션으로 커밋합니다(`ItemRepository`). 트랜잭션은 서버가 필요하므로 오프라인 변경이 로컬 대기열에 쌓여 저장된 것처럼 보이지 않고 네트워크 오류로 돌아옵니다. 수정·완료는 화면이 보여준 항목의 `occurrenceId`·`revision`을 저장된 문서와 비교해, 회차가 다르면 "이미 완료", 회차는 같고 revision이 다르면 "일정 변경"으로 거부합니다. 수정은 문서 전체를 다시 쓰되 저장된 `createdAt`을 그대로 넣습니다. 완료 전이면(`recordCount` = 0) 시작일 수정이 마지막 교체일도 함께 옮기고, 완료 후에는 마지막 교체일을 유지하며 시작일이 그보다 늦을 수 없습니다. 앱은 `domain` 규칙으로 다음 예정일을 계산하고, 규칙도 `nextDueDate = lastReplacedDate + cycleDays`와 미래 날짜 금지(서울 기준)를 다시 확인합니다. 삭제는 아래 단계로 여러 트랜잭션에 나눠 커밋합니다. 참여·나가기·내보내기·초대의 쓰기 묶음은 [가족 공유](#가족-공유-초대참여나가기내보내기) 절에, 회원 탈퇴는 정리 정책 작업에서 정합니다.

## 보안 규칙 범위

`firestore.rules` v2는 최초 로그인·소속 복구·용품 등록·수정·완료·삭제(v1)에 더해 초대 발급·재발급·중지, 참여, 나가기, 내보내기, 빈 1인 집 해체(참여·탈퇴할 때), 관리자의 지역 변경, 회원 탈퇴의 사용자 문서 삭제, 집안일(`chores`·`logs`)을 허용합니다. 나머지는 기본 거부입니다: 소속을 남긴 사용자 문서 삭제, 구성원·항목이 남은 집의 삭제. 각 작업에서 규칙과 테스트를 함께 추가합니다.

- 집 개수는 실제 대상 문서 생성·삭제와 함께 ±1만 허용합니다. 부모·이력 개수도 동일하게 검증하며, 변경 가능한 집은 `integrityVersion=2`여야 합니다.
- 완료 후 수정(F-03)은 실제 교체일을 유지합니다. 완료 전 시작일 수정만 실제 교체일을 함께 옮깁니다. 이전 교체일 하한(#20)은 완료에도 적용됩니다.
- 규칙 테스트: `firestore-rules/`([TESTING](TESTING.md#firestore-규칙-테스트)).

## 정리 정책

부모 문서를 지워도 하위 문서는 자동으로 지워지지 않으므로, 앱이 하위 문서부터 지우고 규칙이 그 순서를 강제합니다.

| 상황 | 정리하는 것 | 구현 |
| --- | --- | --- |
| 용품 삭제 | 삭제 상태 → 이력·개수 정리 → 항목·집 개수 감소 | `ItemRepository.delete` → `DocumentDeletion` |
| 집안일 삭제 | 삭제 상태 → 기록·개수 정리 → 집안일·집 개수 감소 | `ChoreRepository.delete` → `DocumentDeletion` |
| 참여 | 원래 빈 1인 집, 그 member, 그 집의 초대 | `HouseRepository.join` (한 트랜잭션) |
| 재발급·중지 | 이전 초대 | `HouseRepository.issueInvite`·`stopInvite` |
| 만료된 초대 | 따로 지우지 않음. 어느 집이든 같은 코드로 새 초대를 만들 때 덮어씀 | 규칙의 만료 코드 재사용 |
| 회원 탈퇴 | 아래 표 | `AuthRepository.deleteAccount` |

삭제는 등록자 또는 관리자가 `deleting=true`를 먼저 확정합니다. 이후 수정·완료·일정 변경·이력 생성을 모두 거부합니다. `DocumentDeletion`은 100개씩 조회하고, 이력 하나 삭제와 부모 개수 −1·대상 ID를 매 트랜잭션에 함께 씁니다. 부모는 **기존 상태**가 삭제 중이고 개수가 0일 때만 지웁니다. 실패 후 같은 API를 다시 호출하면 남은 기록부터 재개하며, 다른 기기가 이미 지운 부모도 성공으로 취급합니다. 삭제 취소는 지원하지 않습니다. 기록 수가 많으면 네트워크 왕복이 늘지만, 부분 실패와 규칙의 접근 수 제한을 관리할 수 있습니다.

**회원 탈퇴** ([#40](https://github.com/kw-mobile/home-timing/issues/40)): 비밀번호로 다시 인증 → 집 데이터 정리 → 사용자 문서 삭제 → **마지막에** Auth 계정 삭제. 각 단계는 저장된 상태를 먼저 읽고 그에 맞는 단계만 하므로, 도중에 실패하면 다시 호출해 이어서 끝냅니다. Auth 삭제는 네트워크 오류일 때 최대 3번 시도하고, 그래도 실패하면 "데이터는 정리됨, 다시 시도" 오류를 돌려줍니다.

| 탈퇴하는 사람 | 함께 쓰는 문서 | 규칙이 확인할 것 |
| --- | --- | --- |
| 다른 사람 집의 구성원 | `H/members/me` 삭제, H 인원 −1, `users/me` 삭제 | 나가기와 같은 조건, 커밋 후 현재 집 member 문서 없음 |
| 혼자인 집의 관리자 | 항목(이력 포함)과 집안일(기록 포함)을 하나씩 삭제한 뒤, 초대·`E/members/me`·E·`users/me` 삭제 | 집 삭제 조건(1명·항목 0·집안일 0), 사용자 문서가 같은 커밋에서 사라짐 |
| 내보내진 뒤 복구 전 | `users/me` 삭제 | 현재 집 member 문서 없음 |
| 구성원이 남은 집의 관리자 | 거부 | 앱이 "먼저 구성원을 내보내기"로 안내(관리자 양도는 P0 제외) |

- 구성원이 등록한 항목과 이력은 집의 데이터이므로 탈퇴 후에도 남습니다. `createdBy`·`completedBy`는 탈퇴한 uid를 가리키고, 이력의 `completedByName` 복사본으로 이름을 표시합니다. 그 항목의 삭제는 관리자만 할 수 있습니다.
- Auth 계정만 남는 경우(데이터 정리 후 Auth 삭제가 끝내 실패)는 다음 탈퇴 시도로 끝냅니다. 그 사이 로그인·복원은 ACCOUNT_DELETING으로 거부되며 삭제 API로 재시도합니다. 새 1인 집을 만들지 않습니다.

## 쿼리와 인덱스

복합 인덱스 없이 단일 필드 자동 인덱스만 사용합니다. 그래서 v1에는 인덱스 정의 파일이 필요 없습니다.

| 화면 | 쿼리 |
| --- | --- |
| 홈·용품 목록 | `items` `orderBy(nextDueDate)`, `chores` `orderBy(nextDueDate)`를 각각 구독해 앱에서 합침 |
| 이번 주 임박 | 위 결과를 앱에서 날짜 범위로 거름 |
| 상세 이력 | `history` `orderBy(replacedDate, desc)`. 같은 날짜는 앱에서 `createdAt`으로 정렬 |
| 집안일 기록 | `logs` `orderBy(createdAt, desc)` |
| 가족 관리 | `members` 전체 |

## Firestore에 두지 않는 것

| 위치 | 내용 |
| --- | --- |
| SharedPreferences | 격자·발표 회차별 예보 요약 캐시, 테스트 날씨 모드 여부, 예약한 알림과 이미 보낸 알림(`notify/ReminderLedger`) |
| `assets/regions.json` | 시·군·구 → 격자 좌표 표. 기상청 단기예보 활용가이드 격자 파일(2610)의 2단계 행 |
| `assets/test_forecast.json` | 테스트 날씨 모드 예보 |
| 기기 알림 예약 | AlarmManager 예약 상태. 식별자는 `ReminderKey`: `<ITEM\|CHORE>/<houseId>/<문서 ID>/<ADVANCE\|DUE>/<occurrenceId>` |

## 미정 사항

- 용품 분류 코드 목록: TRD는 `WATER_FILTER`·`SHOWER_FILTER`·`TOOTHBRUSH`·`AIR_FILTER`·`ETC`, 디자인 PR #19는 장소형 분류(욕실·주방 등)를 씀
- 완료 취소(undo): 디자인 PR #19에 있으나 MVP에 없음. 지원하면 이력에 이전 상태 필드를 추가해야 함

## 무결성 필드 전환 (이슈 #57)

이 변경은 구형 클라이언트의 쓰기와 호환되지 않습니다. 규칙 배포와 관리자 전환은 별도 운영 작업이며, 이 PR에서 공용 프로젝트를 변경하지 않습니다. 새 규칙에서 구형 집·부모는 읽기를 유지하지만 변경을 거부합니다. 과거 규칙으로 이미 생긴 고아 데이터나 누락 이력은 자동 삭제·복원하지 않습니다.

1. 관리자 담당자가 쓰기 중단 시간을 정하고 백업을 확보합니다. 새 규칙·호환 앱의 배포 범위를 확인합니다.
2. 강화된 규칙을 먼저 게시합니다. 새 규칙이 실제로 적용됐는지 확인한 뒤 관리자 도구를 실행합니다. 이전 규칙 상태에서 전환하지 않습니다.
3. `FIRESTORE_ADMIN_TOKEN` 환경 변수에 관리자 단기 액세스 토큰을 공급하고, `python3 scripts/firestore/migrate_integrity.py --project <project-id>`로 읽기 전용 검사를 합니다. 토큰을 인자·로그·커밋에 넣지 않습니다.
4. 검토한 프로젝트에 같은 명령과 `--apply`를 실행합니다. 도구는 집 버전을 1로 내려 클라이언트 변경을 중단하고, 실제 구성원·항목·이력을 세어 부모 상태를 채운 뒤 집 개수와 버전 2를 마지막에 기록합니다. 모든 쓰기에 updateTime 선행조건을 사용합니다.
5. 중간 실패 시 그 집은 버전 1로 남습니다. 원인을 해결하고 재실행합니다. 고아 부모·잘못된 삭제 상태·관리자 없는 구성원 목록 등은 차단 건수로 보고하고 집을 활성화하지 않습니다. 데이터 수정은 별도 판단합니다.
6. 도구의 종료 코드 0과 차단 건수 0, 대표 등록·완료·삭제·참여 흐름을 확인한 뒤 앱 사용을 재개합니다. 저장 필드 누락 문서를 클라이언트에서 0으로 자동 보정하지 않습니다.

도구는 기본적으로 읽기만 합니다. `--emulator 127.0.0.1:<port>`는 `demo-` 프로젝트에서만 허용합니다. 검증: `python3 -m unittest discover -s scripts/firestore/tests -v`. 모의 저장소의 중단·재시도 테스트는 실제 공용 프로젝트 전환 성공을 뜻하지 않습니다.

## 계정·집 정리 수명과 초대 허가 (#59)

### 계정 삭제 상태

`accountDeletions/{uid}`는 `startedAt` 서버 시각 하나만 가지며 본인만 get할 수 있습니다. 삭제 시작을 같은 커밋의 집 정리 또는 소속 제거와 연결하고, 5분 이내 재인증 토큰으로만 생성합니다. 앱은 비밀번호 재인증 뒤 토큰을 갱신합니다. 클라이언트 수정·삭제는 금지합니다.

이 상태는 Auth 계정 삭제 뒤에도 UID 경로·시작 시각만 남겨 다른 기기나 오래된 토큰이 사용자 문서 부재를 신규 가입으로 해석하지 못하게 합니다. 이름·이메일·집 ID를 저장하지 않습니다. Spark 클라이언트만으로 Firestore와 Auth를 한 트랜잭션으로 지울 수 없으므로, 사용자 데이터 정리와 재생성 차단을 분리했습니다. 자동 만료하면 Auth 삭제 실패 상태가 다시 열리므로 자동 삭제하지 않습니다. 관리자 정리가 필요하면 Auth 부재·토큰 수명·재시도 정책을 별도로 확인한 뒤 결정합니다. 같은 이메일의 새 Auth 계정은 새 UID이므로 이전 상태와 구분됩니다.

- 혼자인 관리자: 인원 1 확인·삭제 상태 생성·`closing=true`·활성 초대 제거를 먼저 한 트랜잭션에 확정합니다. 참여가 먼저 끝났으면 데이터 정리 전에 거부합니다. 닫힌 집은 참여·등록·편집·완료·초대 재발급을 거부하고 관리자의 정리만 허용합니다.
- 일반 구성원: 삭제 상태 생성·본인 member 제거·집 개수 감소·사용자 문서 삭제를 한 트랜잭션으로 확정합니다. 집의 항목·다른 구성원은 보존합니다.
- 중단 후 재시도: 서버 상태를 먼저 확인합니다. 로그인·복원은 삭제 진행 안내를 반환하고, 비밀번호를 다시 받아 삭제 API를 재호출하면 남은 정리를 이어갑니다.
- 정상 집 이동: 새 소속을 만드는 것만으로 부족하며 이전 member가 같은 커밋에서 없어져야 합니다. 복구는 이전 member가 이미 없을 때만 허용합니다.
- 회차 ID: 경로 구분자·`.`·`..`를 금지하며, 다음 회차는 현재 회차와 다르고 과거 이력에도 사용되지 않아야 합니다. 클라이언트의 UUID 생성과 규칙 검증을 함께 적용합니다.

직접 Auth API만 호출하는 변조 클라이언트의 계정 삭제를 Firestore 규칙이 강제 정리할 수는 없습니다. 운영자는 그런 잔존 데이터를 별도 확인해야 합니다. 이 제한을 앱의 정상 삭제 성공으로 기록하지 않습니다.

### 초대 조회·발급 허가

`inviteLookups/{uid}`에는 `code`, `purpose`(LOOKUP/ISSUE), `windowStartedAt`, `attempts`, `updatedAt`을 저장합니다. 본인만 읽고 새 코드를 요청할 때 별도 트랜잭션으로 허가를 갱신합니다. 계정별 60초 창에 최대 10회, 허가한 코드의 사용 시간은 10초입니다. 실패한 초대 참여도 앞선 허가 요청 횟수에 포함합니다.

초대 읽기뿐 아니라 직접 member 문서를 만드는 참여와 초대 발급 쓰기도 목적·코드가 맞는 허가를 요구합니다. 발급은 무작위 후보 한 개를 확인하고 살아 있는 코드와 충돌하면 재시도를 안내합니다. 자기 집의 기존 활성 초대 읽기는 정리·재발급을 위해 허용합니다. 허가 문서를 지워 횟수를 초기화할 수 없으며, 계정 삭제 때만 정리합니다. 여러 계정·거부 요청을 이용한 한도 소진 방지까지 보장하지 않습니다.

### 이전 데이터 전환

기존 `closing` 부재는 false로 처리합니다. 전환 도구는 값을 채우거나 기존 true를 보존하고, 닫힌 집의 관리자 삭제 상태도 확인합니다. 구성원의 user 포인터가 다른 집을 가리키는 과거 중복 소속은 자동 삭제하지 않고 차단 건수로 보고합니다. 먼저 소속 관계를 검토한 뒤 전환을 재개해야 합니다.
