# 코드 구조와 책임

## 구현 상태와 구조 기준

앱 진입점은 [MainActivity](../app/src/main/java/com/kwmobile/hometiming/ui/main/MainActivity.java)입니다. 아래 계층은 기능을 배치할 기준이며 실제 파일 목록이나 구현 완료 목록이 아닙니다. 실제 구현은 `app/src/`에서 확인합니다.

기본 패키지는 `com.kwmobile.hometiming`, UI는 Java + XML입니다. 앱 식별자는 [app/build.gradle](../app/build.gradle), 버전은 [환경 설정](SETUP.md)의 기준 파일에서 확인합니다.

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
| `notify` | 알림 예약·취소, Receiver, Worker | Activity 참조와 별도의 완료 규칙 |
| `res/layout`, `res/values` | XML 화면, 문자열·색상·테마 | Java에 박아 넣은 사용자 문구·색상 |

인터페이스나 공통 기반 클래스를 모든 클래스에 의무적으로 만들지 않습니다. 실제 교체 가능성·테스트 필요성이 있을 때 분리합니다.

## 화면과 상태

- 화면 간에는 항목 종류와 문서 ID를 전달하고, 최신 데이터는 Repository에서 읽습니다. 문서 전체나 오래된 화면 객체를 Intent에 전달하지 않습니다.
- UI 수명과 리스너 수명을 맞추고, 집 전환·로그아웃 시 이전 집 구독을 해제합니다. 한 화면이 사라져도 Activity가 데이터 계층에 남지 않도록 합니다.
- 로딩·빈 목록·실패·오프라인 상태를 구분합니다. Firebase 설정이 없으면 설정 안내를 제공하고 제품 기능이 동작하는 것처럼 표시하지 않습니다.
- ViewModel은 화면 상태를 유지하고, Repository는 domain 모델로 변환한 데이터를 제공합니다. API DTO와 Firestore 저장 구조를 화면의 계약으로 사용하지 않습니다.

## 제품·데이터 계약의 위치

날짜·회차·집 소속·권한·오프라인·알림 동작은 [MVP의 계약](MVP.md)에서 관리합니다. 저장 필드나 규칙을 구현하기 전에 해당 계약과 기존 기획을 대조하고, 미확정인 참여 문서·원자성 설계는 관련 이슈/PR에서 확정합니다.

스키마·enum·이력 구조를 바꾸면 기존 데이터 영향과 권한 검증을 함께 검토합니다. 새 서버·서비스·계층을 도입하거나 의존 방향을 바꿀 때는 PR에 이유·대안·영향을 남기고 이 문서를 갱신합니다.

## 사진·바코드 등록 연결 계획 (미구현)

위의 의존 방향을 유지하며 제품 범위·상태 계약은 [MVP 계획](MVP.md#사진바코드-등록-탐색-계획-p1-미구현)을 따릅니다. 아래는 실제 구현 시 책임이며 지금 빈 패키지·클래스를 만들지 않습니다.

| 위치 | 구현 책임 | 경계 |
| --- | --- | --- |
| 등록/촬영 Fragment·XML | 카메라 권한·프리뷰·셔터·상태 렌더링·이동 | 직접 Firestore·외부 API 호출 금지 |
| 등록 ViewModel | 폼 초안, 처리 상태, 작업 ID, 후보 선택, 저장 요청 | 중복 요청·취소 뒤 오래된 결과 차단 |
| 인식 데이터 계층 | ML Kit 호출과 모델 수명, Android 이미지를 결과 값으로 변환 | Android·ML Kit 타입을 domain에 전달하지 않음 |
| domain | 바코드 정규화·체크 숫자·후보 정리 규칙 | Android·Firebase 타입 없음 |
| 로컬 데이터 계층 | 계정/집별 확정 이름 기억 | 원격 저장 성공 뒤 기록, 다른 집 데이터 혼합 금지 |
| 기존 용품 Repository | 실제 P0 등록과 서버 성공/실패 | OCR 때문에 날짜·회차·권한 계약을 우회하지 않음 |

CameraX는 화면 수명에 연결하고 화면 종료 시 카메라를 해제합니다. 바코드 분석은 동시 한 작업만 실행하며 완료·오류·취소 경로에서 `ImageProxy.close()`를 보장합니다. OCR도 동시 요청을 하나로 제한하고 작업 ID가 다른 결과는 버립니다. 이미지 방향은 CameraX 회전 정보를 사용합니다. 프로세스 종료 뒤에는 큰 사진을 복원하려 하지 않고 초안으로 돌아가 재촬영을 제안합니다.
