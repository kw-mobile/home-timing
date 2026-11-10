# Firestore 데이터 모델 v1

[MVP의 데이터·권한·완료 계약](MVP.md#데이터권한-계약)을 Firestore 문서로 옮긴 설계 기준입니다. 구현 완료 목록이 아니며, 실제 저장·권한 동작은 `firestore.rules`·Repository 코드와 그 테스트로 확인합니다. 결정 경위는 [#22](https://github.com/kw-mobile/home-timing/issues/22)에 있습니다.

모든 제품 데이터는 **집(house)** 에 속합니다. 사용자 문서는 현재 집을 가리키는 포인터와 개인 설정만 가집니다. 서버 코드 없이(Spark 요금제) 권한과 불변식을 Firestore 보안 규칙과 트랜잭션·일괄 쓰기로 보장할 수 있는 구조를 택했습니다.

## 구조

```text
users/{uid}                          개인 설정·현재 집 포인터
houses/{houseId}                     집장·인원·항목 수·지역
├─ members/{uid}                     구성원 (소속의 기준)
├─ items/{itemId}                    생활용품
│  └─ history/{occurrenceId}         교체 이력 (완료한 회차 ID = 문서 ID)
└─ chores/{choreId}                  주간 집안일
   └─ logs/{logId}                   완료·일정 변경 기록
invites/{code}                       초대 코드 (후보, 초대·참여 설계에서 확정)
```

용품과 집안일은 필드와 검증 규칙(주기 vs 요일)이 달라 컬렉션을 나눕니다. 화면은 항목 종류(`item`/`chore`)와 문서 ID로 상세를 엽니다. 한 컬렉션에 `kind` 필드로 합치는 안은 규칙이 종류마다 갈라져 복잡해지므로 택하지 않았습니다.

## 공통 규약

| 항목 | 규약 |
| --- | --- |
| 날짜 | 제품 날짜는 `yyyy-MM-dd` 문자열(Asia/Seoul). 변환은 `domain.DateRules`만 사용. 문자열 비교 = 날짜 비교 |
| 시각 | `createdAt`·`updatedAt`·`expiresAt`은 Firestore timestamp. 작성 시각은 서버 시각(`serverTimestamp`)이며 규칙에서 `request.time`과 비교 |
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

구성원만 읽습니다. 집장만 지역·초대를 바꿉니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `ownerId` | string | ✓ | 집장 uid. 생성 후 변경 불가(집장 양도는 P0 제외) |
| `memberCount` | number | ✓ | 1~6. 참여·나가기·내보내기와 같은 쓰기에서 ±1 |
| `itemCount`, `choreCount` | number | ✓ | 현재 항목 수. 등록·삭제와 같은 쓰기에서 ±1. 1인 집 참여 조건 검사용 |
| `regionName` | string | | 표시용 시·군·구 |
| `nx`, `ny` | number | | 기상청 격자. 지역을 정하기 전에는 없음 |
| `activeInviteCode` | string | | 유효한 초대 코드. 재발급하면 바뀌어 이전 코드가 무효 |
| `createdAt`, `updatedAt` | timestamp | ✓ | |

규칙은 하위 컬렉션 문서 수를 셀 수 없으므로 개수 필드를 둡니다. 증감은 `FieldValue.increment`로 써서 동시 쓰기에도 맞게 합니다.

## houses/{houseId}/members/{uid}

문서가 있으면 구성원입니다. 규칙의 구성원 검사는 `exists(.../members/$(request.auth.uid))`로 합니다. 로그인할 때 내보내졌는지 확인하도록, 사용자는 구성원이 아니어도 **자기 uid의** member 문서는 `get`할 수 있어야 합니다(없으면 새 1인 집으로 복구).

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `displayName` | string | ✓ | 참여 시점의 이름 복사본 |
| `joinedAt` | timestamp | ✓ | |
| `inviteCode` | string | | 초대로 참여할 때 사용한 코드(규칙이 초대 유효성을 확인) |

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
| `createdBy` | string | ✓ | 등록자. 삭제 권한(등록자·집장) 판단, 변경 불가 |
| `updatedBy` | string | ✓ | |
| `createdAt`, `updatedAt` | timestamp | ✓ | |

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

완료 기록의 문서 ID는 `done_<occurrenceId>`로 해 중복 완료를 막고, 일정 변경 기록은 자동 ID를 씁니다. 생성만 허용합니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `action` | string | ✓ | `DONE` 또는 `RESCHEDULED` |
| `occurrenceId` | string | ✓ | 대상 회차 |
| `date` | string | ✓ | 완료일 또는 옮긴 날짜 |
| `fromDate` | string | | 옮기기 전 날짜(`RESCHEDULED`) |
| `reason` | string | | `RAIN_FORECAST`, `USER` |
| `actorId`, `actorName` | string | ✓ | 수행자 uid·이름 복사본 |
| `createdAt` | timestamp | ✓ | |

## invites/{code} (후보)

비구성원이 집을 읽지 않고 참여할 수 있도록 집 밖에 둡니다. 로그인 사용자는 코드로 `get`만 할 수 있고 `list`는 거부합니다. 초대·참여 설계 작업에서 확정합니다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `houseId` | string | 참여할 집 |
| `createdBy` | string | 발급한 집장 |
| `expiresAt` | timestamp | 발급 + 24시간 |

코드는 6자리 숫자입니다. 이미 있는 코드면 생성이 실패하므로 다른 코드로 다시 시도합니다.

## 함께 커밋하는 작업

한 작업의 문서들은 하나의 트랜잭션 또는 일괄 쓰기로 커밋합니다. 규칙은 `getAfter()`·`existsAfter()`로 커밋 후 상태가 서로 맞는지 확인합니다.

| 작업 | 함께 쓰는 문서 | 규칙이 확인할 것 |
| --- | --- | --- |
| 최초 로그인 (트랜잭션) | `users/{uid}` 생성, `houses/{new}` 생성, `members/{uid}` 생성 | `ownerId`=본인, `memberCount`=1, 개수 0, `currentHouseId`=새 집 |
| 소속 복구 (트랜잭션) | `houses/{new}`·`members/{uid}` 생성, `users/{uid}`의 `currentHouseId`·`updatedAt` 갱신 | 위와 같은 새 집 조건, 사용자 문서는 그 두 필드만 변경 |
| 용품 등록 | `items/{id}` 생성, 집 `itemCount` +1 | 구성원, 필수 필드·주기 범위, `createdBy`=본인, `revision`=1 |
| 용품 수정 | `items/{id}` 갱신 | 구성원, `createdBy` 불변, 일정 필드가 바뀌면 `revision` +1 |
| 용품 완료 (트랜잭션) | `history/{현재 occurrenceId}` 생성, `items/{id}` 갱신 | 이력 ID = 직전 `occurrenceId`, 새 `occurrenceId` ≠ 이전, `revision` +1, 이전 교체일 ≤ 새 교체일 |
| 용품 삭제 | `history/*` 삭제, `items/{id}` 삭제, 집 `itemCount` −1 | 등록자 또는 집장 |
| 집안일 완료 (트랜잭션) | `logs/done_<occurrenceId>` 생성, `chores/{id}` 갱신 | 위 용품 완료와 같은 회차·revision 검사 |
| 이번 회차 날짜 변경 | `chores/{id}` 갱신, `logs/{auto}` 생성 | `occurrenceId`·`plannedDueDate` 불변, `revision` +1 |

용품 등록·수정·완료·삭제는 모두 트랜잭션으로 커밋합니다(`ItemRepository`). 트랜잭션은 서버가 필요하므로 오프라인 변경이 로컬 대기열에 쌓여 저장된 것처럼 보이지 않고 네트워크 오류로 돌아옵니다. 수정·완료는 화면이 보여준 항목의 `occurrenceId`·`revision`을 저장된 문서와 비교해, 회차가 다르면 "이미 완료", 회차는 같고 revision이 다르면 "일정 변경"으로 거부합니다. 수정은 문서 전체를 다시 쓰되 저장된 `createdAt`을 그대로 넣습니다. 완료 전이면(`lastReplacedDate` = `startDate`) 시작일 수정이 마지막 교체일도 함께 옮기고, 완료 후에는 마지막 교체일을 유지하며 시작일이 그보다 늦을 수 없습니다. 앱은 `domain` 규칙으로 다음 예정일을 계산하고, 규칙도 `nextDueDate = lastReplacedDate + cycleDays`와 미래 날짜 금지(서울 기준)를 다시 확인합니다. 이력이 500개를 넘으면 삭제를 여러 번에 나눠 커밋합니다. 참여·나가기·내보내기·탈퇴의 쓰기 묶음은 초대·참여 설계와 정리 정책 작업에서 정합니다.

## 보안 규칙 범위 (v1)

`firestore.rules` v1은 위 표의 최초 로그인·소속 복구·용품 등록·수정·완료·삭제만 허용합니다. 나머지는 기본 거부입니다: 집안일(`chores`·`logs`), 초대(`invites`), 기존 집 참여·나가기·내보내기, 지역 변경, 사용자·집 삭제. 각 작업에서 규칙과 테스트를 함께 추가합니다.

- 집 `itemCount`는 구성원이 ±1씩만 바꿀 수 있고, 용품 생성·삭제는 같은 커밋의 ±1을 요구합니다. 항목 없이 개수만 올리는 쓰기는 막지 못하며, 이 경우 그 집이 참여 조건(빈 1인 집)을 잃을 뿐입니다.
- 수정(F-03)은 같은 회차에서 `lastReplacedDate`를 고칠 수 있습니다. 이전 교체일 하한(#20)은 회차가 바뀌는 완료에만 적용됩니다.
- 규칙 테스트: `firestore-rules/`([TESTING](TESTING.md#firestore-규칙-테스트)).

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
| SharedPreferences | 격자·발표 회차별 예보 요약 캐시, 테스트 날씨 모드 여부 |
| `assets/regions.json` | 시·군·구 → 격자 좌표 표 |
| `assets/test_forecast.json` | 테스트 날씨 모드 예보 |
| 기기 알림 예약 | AlarmManager 예약 상태 (항목 종류·집·회차·알림 종류로 식별) |

## 미정 사항

- 용품 분류 코드 목록: TRD는 `WATER_FILTER`·`SHOWER_FILTER`·`TOOTHBRUSH`·`AIR_FILTER`·`ETC`, 디자인 PR #19는 장소형 분류(욕실·주방 등)를 씀
- 완료 취소(undo): 디자인 PR #19에 있으나 MVP에 없음. 지원하면 이력에 이전 상태 필드를 추가해야 함
- 초대 코드 하나로 24시간 안에 여러 명이 참여할 수 있는지
