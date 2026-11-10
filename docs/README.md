# 프로젝트 문서 안내

처음 참여하면 [환경 설정](SETUP.md) → [MVP](MVP.md) → [아키텍처](ARCHITECTURE.md) 순서로 읽고, 작업 전 [검증 기준](TESTING.md)과 [기여 기록](report/README.md)을 확인합니다.

## 문서별 역할

| 알고 싶은 것 | 기준 위치 |
| --- | --- |
| 확정 화면 JPG | [라이트 요약](design-jpg/overview-light.jpg) · [다크 요약](design-jpg/overview-dark.jpg) · [전체 보드](design-jpg) |
| 팀 공유용 전체 화면 HTML | [오프라인 화면 갤러리](design-review.html) |
| PRD 의도·화면별 대응·업그레이드 기준 | [디자인 정합성 점검](report/PRD_DESIGN_AUDIT.md) |
| 컬러·로고·글자·컴포넌트 규격 | [디자인 시스템](DESIGN_SYSTEM.md) |
| 현재 화면 시안을 어떻게 확인하는가 | [Android 미리보기 실행](DESIGN_SYSTEM.md#미리보기-실행) |
| 무엇을 제출하고 무엇을 제외하는가 | [MVP.md](MVP.md) |
| 코드·화면·데이터 책임을 어디에 두는가 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 어떻게 같은 환경으로 빌드하는가 | [SETUP.md](SETUP.md) |
| 변경 후 무엇을 확인하고 어떤 근거를 남기는가 | [TESTING.md](TESTING.md) |
| 누가 무엇을 했는지, 보고서 근거를 어떻게 모으는가 | [report/README.md](report/README.md) |
| Notion 자동 개발 기록 | [report/NOTION_SYNC.md](report/NOTION_SYNC.md) |
| 실제 작업과 참여자 기록 | [report/WORKLOG.md](report/WORKLOG.md) |
| 최초 환경 설정·작업 시작·완료 스킬 | [SKILLS.md](SKILLS.md) |
| 브랜치·리뷰·PR 절차 | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| AI 도구가 반드시 지킬 지침 | [AGENTS.md](../AGENTS.md) |

## 구조

```text
docs/
├─ README.md             문서 안내와 관리 원칙
├─ DESIGN_SYSTEM.md       컬러·로고·글자·컴포넌트 규격
├─ MVP.md                제출 범위·사용자 흐름·제품 규칙
├─ ARCHITECTURE.md       패키지 책임·의존 방향·구조 변경 기준
├─ SETUP.md              도구 버전·개인 설정·외부 서비스 준비
├─ TESTING.md            변경별 검증·결과 기록
├─ SKILLS.md             최초 환경 설정·작업 시작·완료 스킬
├─ design-review.html   확정 화면 HTML
├─ design-jpg/          확정 화면 JPG 보드
└─ report/
   ├─ README.md          참여자 기록·PR/댓글 기준·최종 보고서 목차
   ├─ WORKLOG.md         작업별 참여자·결과·근거 색인
   └─ NOTION_SYNC.md     Development History 자동 기록 연결
```

## 문서 관리 원칙

- 문서는 합의한 기준, 코드는 실제 구현, 검증 결과는 확인된 동작의 근거입니다. 계획을 적었다고 구현이 완료된 것은 아닙니다.
- 범위·제품 규칙은 MVP, 기술 배치는 아키텍처에서 수정합니다. 버전 숫자는 빌드 설정 파일, 절차는 SETUP에 둡니다.
- 변경 의도·구현·상세 검증·참여자 역할은 PR 본문에 기록하고, WORKLOG는 참여자와 근거 링크를 찾는 색인으로 사용합니다. 작업 진행 상태는 GitHub 이슈에서 관리합니다.
- 실제 코드·사진·설계 내용이 생기기 전에 기능별 빈 폴더나 완료된 것처럼 보이는 문서를 만들지 않습니다. 기존 문서의 어느 주제에도 속하지 않는 내용이 생기면 필요한 문서만 추가하고 이 안내에 연결합니다.
- 그림·스크린샷·영상에는 캡처한 앱 커밋과 시나리오를 함께 남깁니다. 이미지에서 확인되지 않는 동작까지 증명했다고 쓰지 않습니다.
