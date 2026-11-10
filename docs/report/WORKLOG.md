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
| 2026-11-10 | 전주용: 백엔드 작업 순서·이번 범위 결정; Claude Code (도구): domain 규칙·JUnit 작성 및 로컬 검증 | 서울 기준 날짜 변환, 실제 교체일+주기 예정일, D-day 4단계 상태, 필수 입력 검증(F-02·F-04·F-05). 화면·저장소 연결 전, 리뷰 대기 | [이슈 #20](https://github.com/kw-mobile/home-timing/issues/20), [PR #21](https://github.com/kw-mobile/home-timing/pull/21) |
| 2026-11-10 | 전주용: 스키마 설계 범위 결정; Claude Code (도구): 설계 초안·문서 작성 및 링크 검증 | Firestore 스키마 v1: 집 소속 항목·주간 집안일, members 기반 소속, occurrenceId·revision, 회차 ID 이력, 함께 커밋할 문서. 규칙·코드 전, 리뷰 대기 | [이슈 #22](https://github.com/kw-mobile/home-timing/issues/22), [PR #23](https://github.com/kw-mobile/home-timing/pull/23) |
| 2026-11-10 | 전주용: 규칙 테스트 방식(Java REST) 결정; Claude Code (도구): 테스트 모듈·CI·문서 작성 및 로컬 emulator 검증 | Firestore emulator 규칙 테스트 환경: JDK HttpClient 기반 도우미, demo 프로젝트 전용, 전체 거부 기준 테스트 6개, 규칙 변경 CI. 실제 권한 규칙 전, 리뷰 대기 | [이슈 #24](https://github.com/kw-mobile/home-timing/issues/24), [PR #25](https://github.com/kw-mobile/home-timing/pull/25) |
| 2026-11-10 | 전주용: 4번 범위 결정·Firebase 설정 확인 요청; Claude Code (도구): 인증 저장소·집 준비·테스트 작성 및 로컬 검증 | AuthRepository(가입·로그인·복원·로그아웃·재설정), 첫 로그인 1인 집·소속 복구 트랜잭션, 오류 한국어 분류, 가입 이름 1~20자 결정(F-01). 실제 Firebase 동작은 규칙 작업 후 확인, 리뷰 대기 | [이슈 #26](https://github.com/kw-mobile/home-timing/issues/26), [PR #27](https://github.com/kw-mobile/home-timing/pull/27) |
| 2026-11-10 | 전주용: 5번 범위 결정·배포 승인 예정; Claude Code (도구): 규칙·emulator 테스트·문서 작성 및 검증 | Firestore 보안 규칙 v1: 본인 사용자 문서, 1인 집 생성·소속 복구, 구성원 전용 집·용품, 필드·날짜·예정일·revision 검사, 회차 이력 기반 완료(#20 하한), 등록자·집장 삭제. 허용·거부 테스트 43개. 배포 전, 리뷰 대기 | [이슈 #28](https://github.com/kw-mobile/home-timing/issues/28), [PR #29](https://github.com/kw-mobile/home-timing/pull/29) |
| 2026-11-10 | 전주용: 규칙 게시 승인·직접 로그인, SETUP 보완 요청; Claude Code (도구): 콘솔 게시·검증, 문서 작성 | 규칙 v1(`335fb10`)을 CLI 권한 부족으로 콘솔에서 게시하고 서버 내용 일치 확인(#28). SETUP에 `serviceusage.services.use` 누락·소유자 조치·콘솔 게시 절차 기록. 리뷰 대기 | [배포 기록](https://github.com/kw-mobile/home-timing/issues/28#issuecomment-6766413991), [이슈 #30](https://github.com/kw-mobile/home-timing/issues/30), [PR #31](https://github.com/kw-mobile/home-timing/pull/31) |
| 2026-11-11 | 전주용: 6번 범위 결정; Claude Code (도구): 용품 domain·저장소·테스트·문서 작성 및 로컬 검증 | ItemRepository(F-02~F-05): 목록·상세·이력 구독, 트랜잭션 전용 등록·수정·완료·삭제, 이미 완료/일정 변경 충돌 구분, #20 교체일 하한·규칙 글자 수 검증. 단위 62·규칙 45 통과, 실기기 미확인, 리뷰 대기 | [이슈 #34](https://github.com/kw-mobile/home-timing/issues/34), [PR #35](https://github.com/kw-mobile/home-timing/pull/35) |

## 기록 예시

아래는 형식 예시이며 실제 실적이 아닙니다. 새 기록은 위 표에 추가합니다.

```text
YYYY-MM-DD | @구현자: 교체일 계산 구현; @검증자: 윤년/월말 검증 | F-04 날짜 계산·상태 표시 | #PR번호, 검증 댓글 링크
```

PR 기여 표와 참여자 표기가 다르거나 ‘확인 필요’가 남은 항목은 보고서 작성 전에 실제 참여자에게 확인합니다. 기록을 채우기 위해 수행하지 않은 작업이나 사람을 추가하지 않습니다.
