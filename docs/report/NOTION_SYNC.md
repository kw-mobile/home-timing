# 개발 기록: GitHub 전환

2026-11-14 사용자 요청으로 Notion 신규 기록을 중단합니다. 무료 블록을 모두 사용했다는 사용자 보고가 있었고, [PR #69 동기화](https://github.com/kw-mobile/home-timing/actions/runs/43436812662)와 [PR #71 동기화](https://github.com/kw-mobile/home-timing/actions/runs/43447501915)는 HTTP 403으로 실패했습니다. HTTP 상태만으로 용량 초과 원인을 단정하지 않습니다.

## 기록 기준

- 작업 목적·범위·담당·완료 기준·진행 상태: [GitHub 이슈](https://github.com/kw-mobile/home-timing/issues)
- 실제 변경·참여자별 기여·검증 근거: 연결된 PR의 최종 본문과 결정/검증 댓글
- 사람·작업별 근거 색인: [WORKLOG](WORKLOG.md)
- 확정 제품·구조·환경·검증 계약: [문서 안내](../README.md)의 저장소 문서

기존 Development History는 병합 PR의 복사본입니다. 원본 PR·댓글·WORKLOG를 유지하므로 같은 내용을 이슈마다 다시 복사하지 않습니다. [전환 및 MVP 통합 이슈 #72](https://github.com/kw-mobile/home-timing/issues/72)에서 근거와 남은 이전 범위를 관리합니다. 2026-11-14 Notion 데스크톱 앱에서 블록 소진 안내를 확인하고, 프로젝트와 하위 페이지를 내보냈습니다(51개 파일). 읽은 원문을 아래 이슈로 이전했습니다.

| 자료 | GitHub 이전 위치 |
| --- | --- |
| PRD·TRD 전체 본문 | [#74](https://github.com/kw-mobile/home-timing/issues/74) |
| BE 체크리스트 전체 본문 | [#75](https://github.com/kw-mobile/home-timing/issues/75) |
| 초기 기획 DOCX의 추출 본문·Cal 7건·예정 역할 배정 | [#76](https://github.com/kw-mobile/home-timing/issues/76) |
| Development History 33건 | 각 행의 원본 PR·댓글과 WORKLOG 유지 |

자료 이전 이슈의 닫힘은 원문 이전 완료를 뜻하며 기능 구현·실기기 검증 완료를 뜻하지 않습니다. 과거 계획은 현재 MVP 계약을 덮어쓰지 않습니다. 프로젝트 루트의 팀 연락처·개인 메일은 GitHub에 옮기지 않았습니다. 내보낸 원본 ZIP과 DOCX는 사용자의 Downloads에 로컬 보존했고 저장소에 커밋하지 않았습니다. Notion 댓글·버전 이력과 프로젝트 밖 개인 페이지는 내보내기 대상이 아니며 이전 완료로 주장하지 않습니다.

## 중단과 기존 자료 보존

Repository variable `NOTION_SYNC_ENABLED=false`로 즉시 중단했습니다. 병합 이벤트 쓰기 workflow도 제거합니다. 기존 Notion 페이지·DB·GitHub secret은 삭제하지 않습니다. 토큰을 출력하거나 이슈에 붙이지 않습니다.

기존 [Notion 기획](https://app.notion.com/p/40f8bac3963f80298786d02fdf27da79), [PRD](https://app.notion.com/p/4108bac3963f80f894a7eff15ebc9d21), [TRD](https://app.notion.com/p/4108bac3963f8073bcbfe3c284792e38), [Development History](https://app.notion.com/p/48d520845c72469aa0ba3bfb2bc4fe43)는 읽기 자료로 보존합니다. 충돌 시 [MVP](../MVP.md)가 기준입니다.

역사적 동기화 구현과 이름 매핑은 `scripts/notion/`에 남깁니다. 오프라인 계약 테스트는 `python3 -m unittest discover -s scripts/notion/tests -v`이며 실제 Notion 쓰기를 실행하지 않습니다. 다시 활성화하는 작업은 별도 요청으로 다룹니다.
