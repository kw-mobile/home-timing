# 개발 기록

작업 완료 시 날짜·참여자·내용·근거를 한 행으로 남깁니다. 상세 구현·역할·검증은 PR 본문이 기준입니다. 날짜는 Asia/Seoul 기준입니다.

계획된 담당은 이슈에, 실제 수행자는 이 표와 PR에 기록합니다. 미병합 여부는 PR 링크에서 확인하며, 아래 기록 자체가 main 반영을 뜻하지 않습니다. 기록 방식은 [기여 기록 기준](README.md)을 따릅니다.

| 날짜 | 실제 참여자·역할 | 작업 내용 | 근거 |
| --- | --- | --- | --- |
| 2026-11-08 | Codex 작성·검증; 사람별 구현·검토 역할 확인 필요 (PR 작성 계정: @minestero00) | 제출 MVP 범위 정리, Android 공통 환경·버전 잠금·CI 준비. 환경 검증 기준 `5015e9d`: 로컬/CI 기본 빌드 확인, 제품 테스트는 NO-SOURCE | [계획 #1](https://github.com/kw-mobile/home-timing/issues/1), [환경 PR #2](https://github.com/kw-mobile/home-timing/pull/2), [환경 검증 CI](https://github.com/kw-mobile/home-timing/actions/runs/42461366760) |
| 2026-11-08 | Codex 문서·템플릿 작성 및 정적 검증; 사람별 역할 확인 필요 | 공통 AGENTS·Claude import, 문서 구조, 참여자별 PR 기록과 보고서 근거 수집 기준 준비 | [작업 기준 PR #2](https://github.com/kw-mobile/home-timing/pull/2), 상세 검증은 PR 본문 |

| 2026-11-08 | Codex 구현·오프라인 검증; 사람별 담당 확인 필요 | 병합 PR의 참여자·검증 근거를 Development History로 옮기는 자동화와 비활성 연결 설정 준비. 실제 Actions→Notion 쓰기는 인증 후 검증 필요 | [자동화 이슈 #3](https://github.com/kw-mobile/home-timing/issues/3), [PR #4](https://github.com/kw-mobile/home-timing/pull/4) |

| 2026-11-08 | Codex 스킬·협업 절차 작성 및 검증; 사람별 역할 확인 필요 | 최신 main·이슈 기반 시작, 기존 변경 보존과 재개, 기여·검증·PR 마무리 스킬 준비 | [작업 스킬 이슈 #5](https://github.com/kw-mobile/home-timing/issues/5), [PR #6](https://github.com/kw-mobile/home-timing/pull/6) |

| 2026-11-08 | Codex 완료 스킬·설정 안내 작성 및 검증; 사람별 역할 확인 필요 | 병합 후 작업 브랜치·worktree 정리와 개인 파일 보존, Notion 토큰 발급 경로 안내 | [정리 절차 이슈 #7](https://github.com/kw-mobile/home-timing/issues/7), [PR #8](https://github.com/kw-mobile/home-timing/pull/8) |

| 2026-11-08 | 사용자 토큰 등록; Codex 활성화·실제 API 검증 및 문서 작성; 사람별 신원·역할 추가 확인 필요 | Notion 기록 활성화, PR #2 갱신·재실행 중복 방지, PR #4 새 행 생성 확인 | [자동화 이슈 #3](https://github.com/kw-mobile/home-timing/issues/3), [실제 생성 실행](https://github.com/kw-mobile/home-timing/actions/runs/42472667632), [활성화 PR #9](https://github.com/kw-mobile/home-timing/pull/9) |

## 기록 예시

아래는 형식 예시이며 실제 실적이 아닙니다. 새 기록은 위 표에 추가합니다.

```text
YYYY-MM-DD | @구현자: 교체일 계산 구현; @검증자: 윤년/월말 검증 | F-04 날짜 계산·상태 표시 | #PR번호, 검증 댓글 링크
```

PR 기여 표와 참여자 표기가 다르거나 ‘확인 필요’가 남은 항목은 보고서 작성 전에 실제 참여자에게 확인합니다. 기록을 채우기 위해 수행하지 않은 작업이나 사람을 추가하지 않습니다.
