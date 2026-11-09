# 개발 기록

작업 완료 시 날짜·참여자·내용·근거를 한 행으로 남깁니다. 상세 구현·역할·검증은 PR 본문이 기준입니다. 날짜는 Asia/Seoul 기준입니다.

계획된 담당은 이슈에, 실제 수행자는 이 표와 PR에 기록합니다. 미병합 여부는 PR 링크에서 확인하며, 아래 기록 자체가 main 반영을 뜻하지 않습니다. 기록 방식은 [기여 기록 기준](README.md)을 따릅니다.

| 날짜 | 실제 참여자·역할 | 작업 내용 | 근거 |
| --- | --- | --- | --- |
| 2026-11-08 | 조민제: MVP·공통 환경 요구사항 결정; Codex (도구): 작성·검증 | 제출 MVP 범위 정리, Android 공통 환경·버전 잠금·CI 준비. 환경 검증 기준 `5015e9d`: 로컬/CI 기본 빌드 확인, 제품 테스트는 NO-SOURCE | [계획 #1](https://github.com/kw-mobile/home-timing/issues/1), [환경 PR #2](https://github.com/kw-mobile/home-timing/pull/2), [환경 검증 CI](https://github.com/kw-mobile/home-timing/actions/runs/42461366760) |
| 2026-11-08 | 조민제: 협업·보고서 기록 기준 요청; Codex (도구): 문서·템플릿 작성 및 정적 검증 | 공통 AGENTS·Claude import, 문서 구조, 참여자별 PR 기록과 보고서 근거 수집 기준 준비 | [작업 기준 PR #2](https://github.com/kw-mobile/home-timing/pull/2), 상세 검증은 PR 본문 |
| 2026-11-08 | 조민제: 병합 시점·Notion 기록 요구사항 결정; Codex (도구): 구현·오프라인 검증 | 병합 PR의 참여자·검증 근거를 Development History로 옮기는 자동화와 비활성 연결 설정 준비. 실제 Actions→Notion 쓰기는 인증 후 검증 필요 | [자동화 이슈 #3](https://github.com/kw-mobile/home-timing/issues/3), [PR #4](https://github.com/kw-mobile/home-timing/pull/4) |
| 2026-11-08 | 조민제: 공통 작업 방식·스킬 요청; Codex (도구): 스킬·협업 절차 작성 및 검증 | 최신 main·이슈 기반 시작, 기존 변경 보존과 재개, 기여·검증·PR 마무리 스킬 준비 | [작업 스킬 이슈 #5](https://github.com/kw-mobile/home-timing/issues/5), [PR #6](https://github.com/kw-mobile/home-timing/pull/6) |
| 2026-11-08 | 조민제: 병합 후 정리 정책 결정; Codex (도구): 완료 스킬·설정 안내 작성 및 검증 | 병합 후 작업 브랜치·worktree 정리와 개인 파일 보존, Notion 토큰 발급 경로 안내 | [정리 절차 이슈 #7](https://github.com/kw-mobile/home-timing/issues/7), [PR #8](https://github.com/kw-mobile/home-timing/pull/8) |
| 2026-11-08 | 조민제: Notion 연결·DB 접근·토큰 직접 등록; Codex (도구): 활성화·실제 API 검증 및 문서 작성 | Notion 기록 활성화, PR #2 갱신·재실행 중복 방지, PR #4 새 행 생성 확인 | [자동화 이슈 #3](https://github.com/kw-mobile/home-timing/issues/3), [실제 생성 실행](https://github.com/kw-mobile/home-timing/actions/runs/42472667632), [활성화 PR #9](https://github.com/kw-mobile/home-timing/pull/9) |
| 2026-11-08 | 조민제: 계정·이름 연결 및 팀 권한 조건 지정; Codex (도구): 설정·구현·검증 | 공용 Firebase·Android 앱·서울 DB와 초기 거부 규칙, 결제 제외 팀 관리 권한, GitHub 계정 실명 표시 및 기존 기록 보정 | [팀 환경 이슈 #10](https://github.com/kw-mobile/home-timing/issues/10), [PR #11](https://github.com/kw-mobile/home-timing/pull/11) |

| 2026-11-08 | 조민제: 팀원 접근 검증·기존 기여 기록 보완 요청; Codex (도구): 권한 보완·원격 검증·문서 작성 | Firebase CLI 조회 권한 보완, 팀원 연결·규칙 배포 절차 정리, 기존 PR 역할 확정 및 Notion 재동기화 | [환경·기여 기록 이슈 #12](https://github.com/kw-mobile/home-timing/issues/12), [PR #13](https://github.com/kw-mobile/home-timing/pull/13) |

| 2026-11-08 | 조민제: clone 후 에이전트 setup 흐름 요청; Codex (도구): 공통 스킬·안내 작성 및 검증 | 개인 설정을 보존하는 JDK·SDK·본인 Firebase 연결·기본 빌드 온보딩 절차 | [온보딩 이슈 #14](https://github.com/kw-mobile/home-timing/issues/14), [PR #15](https://github.com/kw-mobile/home-timing/pull/15) |

| 2026-11-08 | 조민제: PRD 화면 시안·디자인 스킬·홈 하단 내비게이션 요청; Codex (도구): 디자인·HTML 구현 및 브라우저 검증 | MVP 7개 화면과 P1 캘린더 참고 시안, 홈·용품·설정 고정 내비게이션, 예시 데이터 상호작용 | [화면 시안 이슈 #16](https://github.com/kw-mobile/home-timing/issues/16), [PR #17](https://github.com/kw-mobile/home-timing/pull/17) |

| 2026-11-09 | 사용자(성명 확인 필요): 레퍼런스·단색 평면 디자인 방향·Android XML 제작 지정; Codex (도구): XML·Java·규격 작성, 빌드·에뮬레이터 검증 | 딥 그린 상단·밝은 목록 구조의 홈·용품·설정 네이티브 미리보기, 공통 색상·글자·치수·로고와 3탭 전환, 단색 태양·실제 드래그 패널·핸들·뒤로가기·회전 상태 복원, 중성 유리 바·배경 전용 블러, Pretendard·Apple 참고 글자 위계, 기한 초과 우선 목록·압축한 날씨·설정 그룹·무채색 유리 바, 글래스 캡처 후 화면 누락 보정. 홈만 드래그 패널 유지, 용품·설정 일반 스크롤 화면 전환, 집 실루엣·원형 시계 로고와 1024px 앱 아이콘 제작. 실제 제품 데이터 미연결 | [디자인 규격](../DESIGN_SYSTEM.md), [검증·스크린샷](evidence/native-pages-logo/verification.md). 로컬 `feature/design-system-foundations`, 원격 접근 실패로 이슈·PR 미생성 |

## 기록 예시

아래는 형식 예시이며 실제 실적이 아닙니다. 새 기록은 위 표에 추가합니다.

```text
YYYY-MM-DD | @구현자: 교체일 계산 구현; @검증자: 윤년/월말 검증 | F-04 날짜 계산·상태 표시 | #PR번호, 검증 댓글 링크
```

PR 기여 표와 참여자 표기가 다르거나 ‘확인 필요’가 남은 항목은 보고서 작성 전에 실제 참여자에게 확인합니다. 기록을 채우기 위해 수행하지 않은 작업이나 사람을 추가하지 않습니다.
