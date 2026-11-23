# 때맞춤 문서 안내

[앱 소개](../README.md) · [APK 다운로드](https://github.com/kw-mobile/home-timing/releases/latest) · [개발 기록](report/WORKLOG.md)

앱을 둘러보려는 사람, 개발에 참여하는 사람, 결과를 검토하는 사람이 각자 필요한 문서부터 읽을 수 있도록 정리했습니다.

## 어디서 시작하면 될까요?

| 목적 | 추천 경로 |
| --- | --- |
| 앱이 궁금해요 | [앱 소개와 기능](../README.md) → [화면 JPG](design-jpg/overview-light.jpg) → [MVP 제품 규칙](MVP.md) |
| 처음 개발에 참여해요 | [환경 설정](SETUP.md) → [MVP](MVP.md) → [아키텍처](ARCHITECTURE.md) → [협업 규칙](../CONTRIBUTING.md) |
| 화면을 구현·수정해요 | [디자인 시스템](DESIGN_SYSTEM.md) → [오프라인 화면 갤러리](design-review.html) → [변경별 검증](TESTING.md) |
| 저장·권한을 수정해요 | [데이터 모델](DATA_MODEL.md) → [아키텍처](ARCHITECTURE.md) → [검증 기준](TESTING.md) |
| 결과·참여 내역을 확인해요 | [기여 기록 기준](report/README.md) → [개발 기록](report/WORKLOG.md) → 연결된 PR의 검증 근거 |

## 개발 기준

| 문서 | 여기서 확인할 것 |
| --- | --- |
| [MVP](MVP.md) | 제출 범위·사용자 흐름·날짜·회차·권한 등 제품 계약 |
| [디자인 시스템](DESIGN_SYSTEM.md) | 로고·컬러·타이포그래피·컴포넌트·화면 상태 |
| [아키텍처](ARCHITECTURE.md) | UI·ViewModel·Repository·domain의 책임과 의존 방향 |
| [데이터 모델](DATA_MODEL.md) | Firestore 경로·공유 분류·필드·트랜잭션 |
| [환경 설정](SETUP.md) | JDK·SDK·개인 설정·Firebase·기상청·서명·업데이트 배포 |
| [검증 기준](TESTING.md) | 변경별 필수 확인·테스트 실행·실제 결과 기록 |
| [협업 규칙](../CONTRIBUTING.md) | 이슈·브랜치·PR·리뷰·게시 단위 |
| [스킬 안내](SKILLS.md) · [에이전트 지침](../AGENTS.md) | 최초 설정과 AI 도구의 작업 시작·완료 절차 |

## 화면과 브랜드 자료

| 자료 | 쓰임새 |
| --- | --- |
| [로고 PNG](design-assets/app-icon.png) · [SVG](design-assets/app-icon.svg) | 소개·발표 자료에 사용할 기존 앱 심볼. 사용 규격은 [로고](DESIGN_SYSTEM.md#로고) 참고 |
| [라이트 요약](design-jpg/overview-light.jpg) · [다크 요약](design-jpg/overview-dark.jpg) | 화면 구성을 한 장으로 확인 |
| [전체 JPG 보드](design-jpg) | 인증·설정 하위 화면을 포함한 화면 자료 |
| [오프라인 HTML 갤러리](design-review.html) | 내려받아 브라우저에서 여는 테마·상태별 확대 갤러리 |
| [Android 미리보기 실행](DESIGN_SYSTEM.md#미리보기-실행) | debug APK에서 예시 상태를 직접 확인 |
| [캡처 명세](report/evidence/component-system/manifest.json) | 이미지별 앱 SHA·시나리오·테마 |

화면 자료는 캡처 당시의 디자인·예시 상태입니다. 최신 앱에서 달라진 배치와 실제 기능의 검증 여부는 기준 문서와 PR을 함께 확인합니다.

## 작업 기록과 검토 근거

| 기록 | 확인할 것 |
| --- | --- |
| [기여 기록 기준](report/README.md) · [개발 기록](report/WORKLOG.md) | 실제 참여자·역할·작업 결과와 이슈/PR 색인 |
| [디자인 정합성 점검](report/PRD_DESIGN_AUDIT.md) | PRD 의도·화면별 대응·개선 기준 |
| [전체 흐름 점검](report/UI_FLOW_REVIEW.md) | 화면 이동·취소·오류 흐름의 점검 결과 |
| [리팩터링 점검](report/REFACTOR_REVIEW.md) | 구조 정리에서 바꾸거나 유지한 책임 |
| [운영 보안 확인](report/SECURITY_OPERATIONS.md) | 운영 설정·잠금 의존성의 확인 범위 |
| [GitHub 기록 전환](report/NOTION_SYNC.md) | 기존 Notion 보존과 신규 기록 위치 |

날짜·커밋이 있는 보고서는 **당시의 검증 스냅샷**입니다. 현재 제품·구조·디자인의 기준 문서와 역할을 구분합니다.

## 구조

```text
docs/
├─ README.md             문서 안내와 관리 원칙
├─ DESIGN_SYSTEM.md       컬러·로고·글자·컴포넌트 규격
├─ MVP.md                제출 범위·사용자 흐름·제품 규칙
├─ ARCHITECTURE.md       패키지 책임·의존 방향·구조 변경 기준
├─ DATA_MODEL.md         Firestore 경로·필드·회차·함께 커밋할 문서
├─ SETUP.md              도구 버전·개인 설정·외부 서비스 준비
├─ TESTING.md            변경별 검증·결과 기록
├─ SKILLS.md             최초 환경 설정·작업 시작·완료 스킬
├─ design-review.html   확정 화면 HTML
├─ design-jpg/          확정 화면 JPG 보드
└─ report/
   ├─ README.md          참여자 기록·PR/댓글 기준·최종 보고서 목차
   ├─ WORKLOG.md         작업별 참여자·결과·근거 색인
   └─ NOTION_SYNC.md     GitHub 기록 기준·기존 Notion 보존
```

## 문서 관리 원칙

- `report/`의 날짜·커밋이 명시된 점검은 당시 스냅샷입니다. 현재 제품/구조/디자인 기준은 MVP·ARCHITECTURE·DESIGN_SYSTEM을 따르고, 과거 보고서의 계획·미검증을 현재 상태로 확대하지 않습니다.
- 문서는 합의한 기준, 코드는 실제 구현, 검증 결과는 확인된 동작의 근거입니다. 계획을 적었다고 구현이 완료된 것은 아닙니다.
- 범위·제품 규칙은 MVP, 기술 배치는 아키텍처에서 수정합니다. 버전 숫자는 빌드 설정 파일, 절차는 SETUP에 둡니다.
- 변경 의도·구현·상세 검증·참여자 역할은 PR 본문에 기록하고, WORKLOG는 참여자와 근거 링크를 찾는 색인으로 사용합니다. 작업 진행 상태는 GitHub 이슈에서 관리합니다.
- 실제 코드·사진·설계 내용이 생기기 전에 기능별 빈 폴더나 완료된 것처럼 보이는 문서를 만들지 않습니다. 기존 문서의 어느 주제에도 속하지 않는 내용이 생기면 필요한 문서만 추가하고 이 안내에 연결합니다.
- 그림·스크린샷·영상에는 캡처한 앱 커밋과 시나리오를 함께 남깁니다. 이미지에서 확인되지 않는 동작까지 증명했다고 쓰지 않습니다.
