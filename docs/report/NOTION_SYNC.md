# 개발 기록: GitHub 전환

2026-11-14 사용자 요청으로 Notion 신규 기록을 중단합니다. 무료 블록을 모두 사용했다는 사용자 보고가 있었고, [PR #69 동기화](https://github.com/kw-mobile/home-timing/actions/runs/43436812662)와 [PR #71 동기화](https://github.com/kw-mobile/home-timing/actions/runs/43447501915)는 HTTP 403으로 실패했습니다. HTTP 상태만으로 용량 초과 원인을 단정하지 않습니다.

## 기록 기준

- 작업 목적·범위·담당·완료 기준·진행 상태: [GitHub 이슈](https://github.com/kw-mobile/home-timing/issues)
- 실제 변경·참여자별 기여·검증 근거: 연결된 PR의 최종 본문과 결정/검증 댓글
- 사람·작업별 근거 색인: [WORKLOG](WORKLOG.md)
- 확정 제품·구조·환경·검증 계약: [문서 안내](../README.md)의 저장소 문서

기존 Development History는 병합 PR의 복사본입니다. 원본 PR·댓글·WORKLOG를 유지하므로 같은 내용을 이슈마다 다시 복사하지 않습니다. [전환 및 MVP 통합 이슈 #72](https://github.com/kw-mobile/home-timing/issues/72)에서 근거와 남은 이전 범위를 관리합니다. Notion에만 있는 기획·수동 메모는 원문을 읽고 해당 기준 문서 또는 이슈로 옮겨야 하며, 원문 미확인 상태를 이전 완료로 기록하지 않습니다.

## 중단과 기존 자료 보존

Repository variable `NOTION_SYNC_ENABLED=false`로 즉시 중단했습니다. 병합 이벤트 쓰기 workflow도 제거합니다. 기존 Notion 페이지·DB·GitHub secret은 삭제하지 않습니다. 토큰을 출력하거나 이슈에 붙이지 않습니다.

기존 [Notion 기획](https://app.notion.com/p/40f8bac3963f80298786d02fdf27da79), [PRD](https://app.notion.com/p/4108bac3963f80f894a7eff15ebc9d21), [TRD](https://app.notion.com/p/4108bac3963f8073bcbfe3c284792e38), [Development History](https://app.notion.com/p/48d520845c72469aa0ba3bfb2bc4fe43)는 읽기 자료로 보존합니다. 충돌 시 [MVP](../MVP.md)가 기준입니다.

역사적 동기화 구현과 이름 매핑은 `scripts/notion/`에 남깁니다. 오프라인 계약 테스트는 `python3 -m unittest discover -s scripts/notion/tests -v`이며 실제 Notion 쓰기를 실행하지 않습니다. 다시 활성화하는 작업은 별도 요청으로 다룹니다.
