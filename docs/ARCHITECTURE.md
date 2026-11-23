# 코드 구조와 책임

이 문서는 **코드 책임과 의존 방향**을 설명합니다. [문서 안내](README.md) · [데이터 모델](DATA_MODEL.md) · [제품 규칙](MVP.md)

## 구현 상태와 구조 기준

앱 진입점은 [MainActivity](../app/src/main/java/com/kwmobile/hometiming/ui/main/MainActivity.java)입니다. 아래 계층은 기능을 배치할 기준이며 실제 파일 목록이나 구현 완료 목록이 아닙니다. 실제 구현은 `app/src/`에서 확인합니다.

일반 실행의 `MvpViewModel`이 Auth/Item/Chore/House Repository와 알림·캘린더·위젯 서비스를 연결합니다. 로그아웃·집 이동·소속 복구는 `stopSession()`으로 이전 구독·알림·캘린더·위젯을 같은 순서로 정리하고, 새 집은 `AuthRepository.restoreSession()`으로 확정합니다. 자신의 참여/나가기/탈퇴로 이전 권한이 사라지기 전에 구독을 끊어 완료 콜백과의 경쟁을 막습니다. 다른 기기의 내보내기는 권한 오류 후 서버 소속을 재확인합니다.

`DesignPreviewViewModel`은 화면 이동·초안·스크롤을 유지하고 `PreviewStore.Item`은 기존 화면의 표시 모델로도 사용합니다. `MvpRecords`는 실제 immutable domain 모델을 표시 모델로 바꾸며 종류별 ID(`item:<문서ID>`, `chore:<문서ID>`)와 화면이 본 회차/revision 스냅샷을 보존합니다. 편집·완료는 이 스냅샷으로 Repository 트랜잭션을 호출하고 성공 응답 후에만 초안을 비우거나 성공 안내를 합니다. 이력은 상세 화면의 별도 구독으로 읽고, 집안일의 일정 변경 기록을 완료로 세지 않습니다. Activity/화면 바인더는 Firebase·HTTP를 직접 호출하지 않습니다.

실제 데이터·로그인 비밀번호는 saved-state Bundle에 넣지 않습니다. 회전은 ViewModel의 메모리 상태를 유지하고, 프로세스 재시작은 저장된 Firebase 계정을 서버/캐시에서 다시 확인합니다. 캐시 소속 또는 목록/집의 서버 확인 전에는 변경을 막습니다. debug에서 명시적인 `preview_screen` Intent로 실행한 경우만 기존 fixture를 사용하며 로그인·권한 증거가 아닙니다.

`MainActivity`는 실제 화면과 명시적 debug 갤러리의 생명주기·화면 교체·탭·스크롤·전환 효과를 담당합니다. 화면 바인딩은 `OverviewScreens`(홈·목록·캘린더·날씨), `ItemScreens`(등록·상세), `AccountScreens`(인증), `MvpHouseholdScreens`(실제 설정·가족), `HouseholdScreens`(갤러리 예시), `CatalogScreens`(개발용 화면 모음)에 둡니다. 공유 표시 도구는 `PreviewWidgets`·`PreviewItemRows`·`PreviewWeather`이며, 화면은 `PreviewHost`의 화면 이동·다시 그리기 등 필요한 동작만 요청합니다. 화면 객체는 현재 root에만 연결하고 저장 가능한 상태는 기존 ViewModel에 유지합니다.

기본 패키지는 `com.kwmobile.hometiming`, UI는 Java + XML입니다. 앱 식별자는 [app/build.gradle](../app/build.gradle), 버전은 [환경 설정](SETUP.md)의 기준 파일에서 확인합니다.

## 날씨 화면 연결

`WeatherViewModel`이 `WeatherRepository`의 지역 목록·예보를 비동기로 요청하고, 홈·날씨 화면은 LiveData를 관찰해 로딩·오류·캐시 출처·조회 시각·테스트 상태를 렌더링합니다. Activity는 HTTP를 호출하지 않습니다. 요청 세대로 지역/모드 전환 이전의 늦은 결과를 무효화하고, 회전에는 ViewModel 결과를 유지합니다. 일반 실행은 `useSharedRegion`으로 현재 집의 공용 지역을 따르고, 관리자 확인 후 `MvpViewModel.region` → `HouseRepository.setRegion`으로 저장합니다. 지역이 없으면 설정 안내를 표시합니다. `weather_ui`의 기기별 선택은 debug 갤러리의 예시 지역이며 공용 지역을 대신하지 않습니다.

`ForecastHour`는 강수·습도 외에 TMP·SKY·TMN·TMX의 nullable 값을 전달합니다. `WeatherDisplay`는 오늘 현재 시각 이후의 가장 가까운 예보와 제공된 일 최저/최고만 선택하며 실황·미제공 기온을 추정하지 않습니다. 기온·예보 시각, 이전 캐시·테스트 여부를 표시합니다. 이전 강수 전용 캐시는 새 표시 필드가 없어 v2 캐시 키와 분리합니다. 세탁 판정은 보관된 시간별 예보를 현재 서울 시각으로 다시 요약해 지난 시간대를 제외합니다. 예시 집안일의 일정 제안도 같은 규칙을 사용하지만 저장·가족 공유 성공을 뜻하지 않으며 화면에 로컬 예시임을 명시합니다.

## 의존 방향

```text
Activity / Fragment → ViewModel → Repository → Firebase / 기상청 / 로컬 저장
                          ↓           ↓
                          └── domain ─┘

알림 수신·동기화 → Repository / domain
```

`domain`은 외부 계층을 참조하지 않습니다. UI는 데이터 전송 대신 사용자 의도를 전달하고, ViewModel의 화면 상태를 표시합니다.

| 위치 | 책임 | 넣지 않는 것 |
| --- | --- | --- |
| `ui/<기능>` | Activity/Fragment, ViewModel, Adapter, 화면 상태·입력 | 직접적인 Firestore·HTTP 접근 |
| `domain` | 교체일·회차·날씨 판정 등 순수 Java 규칙과 모델 | Context, LiveData, Firebase 문서·DTO |
| `data/auth` | 인증 상태와 계정 작업 | 화면 이동·다이얼로그 |
| `data/firestore` | 집·용품·집안일 저장, 트랜잭션, 리스너 수명 관리 | 화면별 중복 제품 규칙 |
| `data/weather` | API DTO·파싱·예보 캐시·발표 회차 선택 | 화면 위젯 |
| `data/local` | 개인 설정·로컬 저장·지역 표 | 집 권한 판정 |
| `data/calendar` | 기기 캘린더 선택·현재 회차 투영·Provider 소유 일정 정리 | 개인 일정 전체 읽기·양방향 편집 |
| `data/recognition` | 기기 내 사물/OCR 처리·이미지 수명 관리 | 사진 서버 보관·화면 입력 상태 |
| `data/update` | 공식 배포 메타데이터·APK/서명 검사·설치 세션 | 임의 URL·미검증 APK 설치 |
| `widget` | 읽기 전용 투영·계정/집 경계·RemoteViews·주기 갱신 | 별도 완료 규칙·권한 우회 |
| `notify` | 알림 예약·취소, Receiver, Worker. 화면은 `ReminderManager`만 호출(로그인·집 변경 `onSessionStarted`, 로그아웃 `onSessionEnded`, 설정 저장 `updateSettings`, 일반 재동기화 `requestSync`). 직전 서버 확인·동기화 읽기·변경 이벤트는 `data/firestore/ReminderRepository`를 호출 | Activity 참조와 별도의 완료 규칙 |
| `res/layout`, `res/values` | XML 화면, 문자열·색상·테마 | Java에 박아 넣은 사용자 문구·색상 |

인터페이스나 공통 기반 클래스를 모든 클래스에 의무적으로 만들지 않습니다. 실제 교체 가능성·테스트 필요성이 있을 때 분리합니다.

## 화면과 상태

- 화면 간에는 항목 종류와 문서 ID를 전달하고, 최신 데이터는 Repository에서 읽습니다. 문서 전체나 오래된 화면 객체를 Intent에 전달하지 않습니다.
- UI 수명과 리스너 수명을 맞추고, 집 전환·로그아웃 시 이전 집 구독을 해제합니다. 한 화면이 사라져도 Activity가 데이터 계층에 남지 않도록 합니다.
- 로딩·빈 목록·실패·오프라인 상태를 구분합니다. Firebase 설정이 없으면 설정 안내를 제공하고 제품 기능이 동작하는 것처럼 표시하지 않습니다.
- ViewModel은 화면 상태를 유지하고, Repository는 domain 모델로 변환한 데이터를 제공합니다. API DTO와 Firestore 저장 구조를 화면의 계약으로 사용하지 않습니다.

## 제품·데이터 계약의 위치

날짜·회차·집 소속·권한·오프라인·알림 동작은 [MVP의 계약](MVP.md)에서 관리합니다. 저장 필드나 규칙을 구현하기 전에 해당 계약과 기존 기획을 대조하고, 미확정인 참여 문서·원자성 설계는 관련 이슈/PR에서 확정합니다.

Firestore 경로·필드·회차·revision과 함께 커밋할 문서는 [데이터 모델](DATA_MODEL.md)이 기준입니다. 스키마·enum·이력 구조를 바꾸면 그 문서를 갱신하고, 기존 데이터 영향과 권한 검증을 함께 검토합니다. 새 서버·서비스·계층을 도입하거나 의존 방향을 바꿀 때는 PR에 이유·대안·영향을 남기고 이 문서를 갱신합니다.

<a id="사진바코드-등록-연결-계획-debug-실험"></a>

## 사진 용품 종류 입력 연결

현재 채택 경로는 사물 인식 우선·OCR 일반명 보조이며 바코드 촬영·DB 조회·바코드 이름 기억은 1차 구현에서 제외합니다. 제품 범위·실행 순서·상태 계약은 [MVP 계획](MVP.md#사진바코드-등록-탐색-계획-p1-실험)을 따릅니다. `PhotoNameActivity`가 CameraX Preview/ImageCapture를 화면 수명에 바인딩하고 명시적 셔터로 한 장을 처리합니다. `PhotoNameViewModel`은 8초 제한·취소·후보/선택 상태를 갖고 세대로 늦은 결과를 차단합니다. `ProductPhotoRecognizer`가 이미지 소유권과 사물→OCR 순서를 관리합니다. `ProductObjectRecognizer`는 CPU LiteRT에서 번들 EfficientDet-Lite2를 실행하고 `HouseholdNames`가 지원 라벨을 일반 한국어 이름으로 변환·정렬합니다. 사물 결과 뒤에 기존 `ProductTextRecognizer`의 OCR 결과에서 찾은 일반명을 합칩니다. 사람·저신뢰·알 수 없는 라벨은 자동 이름 후보에서 제외합니다. ImageProxy는 변환 직후 닫고 원본 Bitmap은 OCR까지 처리한 뒤 정리합니다. 파일/서버 업로드는 없습니다.

`MainActivity`의 Activity Result는 메모리의 요청 ID·원래 Draft 객체·현재 편집 화면·OperationScope를 대조해 이름만 적용합니다. 회전에는 요청과 초안을 유지하고 프로세스 종료·로그아웃·집 변경·다른 초안에는 결과를 버립니다. debug/release 모두 용품 편집에서 선택적으로 진입하며 Activity는 외부에 export하지 않습니다. 카메라는 onStart에서 다시 바인딩하고 onStop에서 해제합니다. 권한 요청 여부는 회전에 보존해 거부 뒤 자동으로 다시 묻지 않고, 재촬영은 사용자 동작으로 요청합니다. 아래 책임 분리는 현재 구현에도 적용합니다.

| 위치 | 구현 책임 | 경계 |
| --- | --- | --- |
| 등록/촬영 Fragment·XML | 권한·프리뷰·셔터·후보 선택·렌더링·이동 | 직접 Firestore·외부 API 호출 금지 |
| 등록 ViewModel | 초안, 처리 상태, 작업 ID, 이름 적용, 저장 요청 | 취소·다른 초안·집 전환 뒤 오래된 결과 차단 |
| 인식 데이터 계층 | CPU LiteRT·bundled ML Kit 호출·이미지 수명·방향·결과 변환 | Android·ML Kit·LiteRT 타입을 domain에 전달하지 않음 |
| domain | 사물 라벨/포장 글자의 일반명·후보 순위 규칙, 기존 날짜·주기 검증 | Android·Firebase 타입 없음 |
| 기존 용품 Repository | P0 등록과 서버 성공/실패 | OCR 때문에 권한·날짜·회차 계약 우회 금지 |

CameraX는 화면 수명에 연결하고 종료 시 해제합니다. 사진 인식은 동시에 한 작업만 처리하며 모든 완료·오류·취소 경로에서 `ImageProxy.close()`를 보장합니다. CameraX 회전 정보를 사용하고 작업 ID가 다른 결과는 버립니다. 큰 사진과 OCR 전문은 ViewModel/SavedStateHandle에 넣지 않습니다. 이름·날짜·주기·선택 결과는 ViewModel에서 회전 동안만 보존하며 OS Bundle에 저장하지 않습니다. 프로세스 종료 시 입력 초안은 소실될 수 있고 이전 OCR 결과는 적용하지 않습니다.

## 백엔드 비동기·삭제 경계

- `RecordState`는 domain 문서 전체 쓰기에서 검증된 이력 상태를 유지하고, `DocumentDeletion`은 삭제 시작·이력 정리·부모 삭제를 재개 가능한 절차로 공유합니다. 저장 필드와 이전 데이터 전환은 [데이터 모델](DATA_MODEL.md#무결성-필드-전환-이슈-57)을 따릅니다.
- `ItemTasks`는 용품·집안일·알림이 공유하는 오류 분류와 거부 후 충돌 확인을 담당합니다. 쓰기의 `commit`·`commitWithConflictCheck`는 기존 세션 검사·트랜잭션·최종 결과 검사를 한 순서로 실행합니다. 다른 기능의 Repository를 오류 유틸리티로 호출하지 않습니다.
- `AccountDeletionCoordinator`는 한 탈퇴 시도의 UID·세대를 고정하고 계정 상태 생성→집 닫기→데이터 정리를 수행합니다. `AuthRepository`는 재인증·토큰 갱신·최종 Auth 삭제를 담당합니다. `HouseInvitations`는 초대 읽기·발급·중지·시도 예약을 담당하고 소속 이동은 `HouseRepository`에 남깁니다. 두 객체의 세션 검사와 `HouseError` 변환은 `HouseTasks`에서 공유합니다.
- `AuthSessionGuard`는 계정 작업 세대로 늦은 결과와 후속 데이터 작업을 차단합니다. 이미 발행한 Firebase SDK 요청 자체를 취소하지는 못합니다.
- `ReminderSession`은 저장한 알림 기록을 계정·집에 연결합니다. Worker·리스너·알람 확인에는 세션 토큰을, 동기화에는 추가 순서를 적용해 이전 결과를 버립니다. 앱 재시작 시 소유자가 없는 구형 기록이나 현재 계정과 다른 기록은 지우며, 같은 계정·집의 기록만 재예약합니다.
- Repository의 `fromCache`는 스냅샷 출처이며 기기의 네트워크 연결 여부 자체가 아닙니다. 메타데이터 변경도 구독하고, 실제 쓰기 성공은 서버 커밋으로 판단합니다.

`OperationScope`는 계정·집·세대를 묶어 일반 Repository 작업에도 적용합니다. Auth가 자기 사용자 문서로 소속을 복원한 뒤에만 scope를 연결합니다. 집 이동·로그아웃·계정 삭제는 이전 작업과 구독을 무효화합니다. 목록·상세는 현재 scope의 집만 구독하고 이전 캐시 콜백을 버립니다. Firestore 캐시 파일 자체를 암호화하거나 지우는 기능은 아니며, 오프라인에서는 마지막으로 확인한 소속의 캐시를 읽는 기존 계약을 유지합니다. 원격 소속 상실을 오프라인에서 즉시 감지한다고 보장하지 않습니다.

개인 알림 설정은 `ReminderManager.updateSettings`로 저장합니다. 저장 성공을 반환하기 전에 동기화된 일정과 새 설정으로 로컬 예약을 다시 계산하므로, 서버 재동기화가 지연돼도 이전 시각 예약을 유지하지 않습니다. 이후 전체 서버 읽기로 새 항목까지 보완합니다. 계정 삭제 상태·닫힌 집은 알림 서버 확인과 계획에서 제외합니다.

세션 복원 요청은 별도 순서 번호로 최신 결과만 채택합니다. 같은 계정·집의 오프라인 복원은 기존 알림·구독을 불필요하게 지우지 않고, 실제 집 변경을 확인했을 때 이전 scope를 무효화합니다. 설정만으로 다시 만든 로컬 알림 계획은 이미 발송한 회차의 중복 방지 기록을 지우지 않습니다. 전체 서버 스냅샷만 사라진 회차를 확정해 그 기록을 정리합니다.

## 휴대폰 캘린더 투영

`CalendarEntry`가 실제 Item/Chore의 현재 예정일과 최근 완료일을 순수 Java 값으로 투영합니다. `DeviceCalendarManager`는 기기별 opt-in과 UID/집 바인딩·OperationScope 무효화를 관리하고, 기존 `ReminderRepository.readFromServer`의 서버·소속 검증을 재사용합니다. 데이터 조회와 Provider 쓰기를 직렬화해 오래된 응답 역전을 막습니다. `DeviceCalendarStore`는 선택한 캘린더와 설치별 임의 토큰의 정확한 마커에 한해서 Calendar Provider를 읽고 수정하며, 항목·다음/완료 키로 재시도와 중복을 정리합니다. 전체 개인 일정은 읽지 않습니다. 관리 마커는 Provider의 CUSTOM_APP_URI에 두고 사용자에게 보이는 설명에는 앱에서 수정·완료하라는 안내만 표시합니다. `DeviceCalendarWorker`가 온라인에서 주기적으로 갱신합니다. `CalendarSettingsDialogFragment`는 기존 MainActivity 위에서 `CalendarSettingsViewModel`을 통해 권한·선택·해제·재시도를 표시합니다. 선택/상세/해제 자식 DialogFragment도 부모 상태를 공유하며 회전에 복원됩니다. 테스트 날씨의 `WeatherSettingsDialogFragment`는 호스트 Activity의 WeatherViewModel을 공유합니다. 짧은 설정을 위해 별도 Activity나 새 데이터 경로를 만들지 않습니다.

## 현재 위치와 집 날씨 연결

`HouseLocationController`는 버튼으로 시작한 위치 권한·OS 위치 요청·후보/수동 선택·확인 화면의 수명을 관리합니다. MainActivity가 생성/종료와 현재 화면의 진행 표시만 연결합니다. 요청 시점의 세션·관리자 권한·지역 화면을 계속 확인하고 onStop/화면 이탈에서 취소합니다. 위치는 최대20초, 주소 검색은 최대8초이며 화면 중지나 세션 변경 시 늦은 결과를 무시합니다. `CurrentPlaceResolver`는 Android Geocoder의 비동기 API(33+) 또는 별도 bounded executor(26~32)를 사용하며, 날씨 요청 executor를 막지 않습니다. `PlaceAddress`가 알려진 한국 시·군·구와 동·읍·면만 추출하고 도로·번지·건물 주소를 버립니다. Android 주소 검색이 없는 기기나 낮은 정확도는 기존 지역 후보·시도별 수동 선택을 사용합니다.

확인한 `Region`의 이름과 `RegionLocationMatcher.at`으로 받은 좌표에서 계산한 예보 격자는 기존 `MvpViewModel.region` → `HouseRepository.setRegion`에 전달합니다. 새 Firestore 필드·규칙은 추가하지 않습니다. 집 구독의 변경 → `WeatherViewModel.useSharedRegion`이 기존 요청 세대를 무효화하고 새 격자를 조회하므로 지역을 따로 다시 선택하지 않습니다. 원본 위경도·도로명 주소는 서버·로그에 저장하지 않습니다. Android 주소 검색 서비스에는 좌표가 전달될 수 있습니다.

## APK 업데이트 채널

`UpdateFeed`는 프로젝트의 HTTPS GitHub latest/update.json만 읽고 `UpdateInfo`가 고정 저장소의 stable 버전·코드·크기·APK URL·해시를 검증합니다. application-scoped `UpdateManager`가 자동 확인 간격, DownloadManager ID/확인한 파일의 복구, 취소 세대와 상태 LiveData를 관리합니다. 다운로드 완료 알림에서는 미검증 APK를 열지 않고, private cache 복사 뒤 `ApkVerifier`가 바이트/패키지/버전/현재 서명을 확인합니다. 설치 직전에도 다시 검사합니다.

`UpdateActivity`와 MainActivity는 상태·동의/설치 권한 화면만 담당합니다. 파일은 PackageInstaller 세션으로 전달하며, non-exported `UpdateInstallReceiver`의 explicit mutable PendingIntent로 자기 세션 결과만 수신합니다. Android가 `STATUS_PENDING_USER_ACTION`을 요구하면 받은 시스템 Intent로 연결합니다. 새 버전 설치 후 같은/낮은 메타데이터와 남은 업데이트 파일은 정리하며, Firebase/사진/사용자 항목 데이터와는 독립적입니다. API/SDK/라이브러리는 추가하지 않습니다.

## 홈 화면 일정 위젯

`domain/WidgetSchedule`이 용품·집안일의 실제 예정일을 기한 지남~다가오는7일로 투영하고, 서울 날짜·정렬·D-day 부호 규칙을 공유합니다. `widget/ScheduleWidgetProvider`는 framework RemoteViews와 항목별 immutable PendingIntent를 사용합니다. 앱의 Java/XML·색상·폰트를 그대로 재사용하며 새 UI 의존성을 추가하지 않습니다. API31 이상은 크기별 RemoteViews 맵으로 목록/첫 일정 카드를 전환하고, 이전 버전은 가로/세로 옵션을 사용합니다. `ScheduleWidgetIcons`가 항목 이름·종류·분류를 기존 벡터로 변환하며 로컬 투영의 분류는 선택 필드여서 v0.5.0 캐시도 읽습니다.

`ScheduleWidgetManager`는 `MvpViewModel`에서 현재 집의 두 목록이 모두 수신된 뒤 계정/집에 묶인 읽기 전용 로컬 투영을 받습니다. `OperationScope` 무효화와 로그아웃은 투영을 지웁니다. `ScheduleWidgetWorker`는 기존 `ReminderRepository.readFromServer(uid, house)`의 소속 검증을 재사용하며, 로컬 게시/세션 변경보다 오래된 서버 응답은 세대 번호로 폐기합니다. 서버 오류를 빈 일정으로 저장하지 않습니다. 저장된 일정은 Provider가 현재 서울 날짜로 다시 투영합니다. 범위·갱신 한계는 [MVP](MVP.md#홈-화면-위젯-106)를 따릅니다.

## 공통 날짜와 종료 경계

서울의 제품 날짜는 `DateRules.today(Clock)`와 `DateRules.ZONE`, 날짜 차이는 `ReplacementCalculator.daysUntil`을 사용합니다. 실제 화면·미리보기·위젯이 같은 규칙을 따르며, 날짜/저장 규칙을 UI마다 만들지 않습니다. Calendar Provider의 종일 일정은 별도 계약인 UTC를 유지합니다.

`MvpViewModel.stopSession()`은 사용자/집의 접근 범위가 끝날 때 사용합니다. 같은 집의 재바인딩과 ViewModel의 `onCleared()`는 구독만 정리하며, 단순 화면 소멸 때문에 백그라운드 예약을 지우지 않습니다. 구조 점검과 유지한 경계는 [리팩터링 점검 기록](report/REFACTOR_REVIEW.md)에 남깁니다.
