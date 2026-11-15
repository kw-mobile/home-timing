---
name: team-task-finish
description: Finish a home-timing implementation task with appropriate verification, actual contributor records, an updated worklog, a reviewable PR, and post-merge task branch/worktree cleanup. Merge only when the current user has authorized it.
---

# 팀 작업 완료

[공통 지침](../../../AGENTS.md), [검증 기준](../../../docs/TESTING.md), [기여 기록](../../../docs/report/README.md)을 적용해 작업을 검토 가능한 결과로 만듭니다.

1. 이슈의 범위·완료 기준과 실제 diff를 대조합니다. 기존 팀원 변경을 보존하고, 관련 없는 변경은 이번 작업에 섞지 않습니다.
2. 변경 유형에 필요한 검증만 수행합니다. 문서/지침은 링크·정합성·diff, 동작 계약은 의미 있는 테스트, 기기/외부 서비스는 실제 확인을 구분합니다. NO-SOURCE·미실행·모의 응답 테스트의 한계를 그대로 적습니다.
3. 리뷰 요청 전에 `git fetch origin main`으로 기준을 확인합니다. 필요한 main 변경은 현재 작업과 팀원 커밋을 보존하며 반영하고 영향받은 검증을 다시 수행합니다. 진행 중 변경을 임의 stash/reset하거나 공용 브랜치를 강제 push하지 않습니다.
4. 실제 참여자별 설계·구현·검증·리뷰와 근거를 정리합니다. 작성 계정/커밋 수로 구현자를 추정하지 않고 모르면 확인 필요로 둡니다. 도구 사용과 사람의 기여를 구분합니다.
5. [개발 기록](../../../docs/report/WORKLOG.md)의 해당 작업 행을 추가/갱신합니다. 자세한 설명은 PR에, 색인은 개발 기록에 둡니다. 동시 수정 충돌은 양쪽 기록을 보존합니다.
6. 현재 요청이 허용하는 범위에서 [작업 단위별 CI 원칙](../../../CONTRIBUTING.md#pr-게시와-ci-실행-단위)에 따라 위 구현·검증·리뷰·기록을 마친 변경을 모아 한 번에 push하고 PR을 작성합니다. 게시 후 수정도 관련 로컬 검증을 마친 묶음으로 push합니다. 저장소 PR 템플릿에 최종 구현·참여자별 기여·검증 SHA/환경/결과·한계를 남깁니다. 중요한 결정·새 검증·리뷰 수정만 댓글로 기록합니다.
7. 사용자 병합 지시가 있으면 대상 PR과 최신 HEAD, 서버 필수 요건을 확인하고 일반 병합을 수행합니다. 이미 받은 병합 지시를 다시 묻지 않습니다. 지시가 없으면 검토 가능한 PR로 인계합니다. 실패한 검사를 성공으로 바꾸거나 보호/권한 설정을 임의 변경하지 않습니다.
8. 병합했다면 아래 정리 절차까지 수행합니다. 기록은 GitHub 이슈·PR·WORKLOG에 남깁니다. Notion 신규 쓰기는 중단했으며 이전 완료는 원문을 읽고 옮긴 범위에만 사용합니다.

## 병합 후 정리

병합이 끝난 작업의 정리만 요청받았다면 이 절차만 수행합니다. 정리를 위해 새 커밋이나 PR을 만들지 않습니다. 병합 완료를 확인한 해당 작업의 정리는 별도 확인 없이 진행합니다.

1. 원격 PR의 `MERGED` 상태·대상 main·병합 SHA·head 브랜치·head SHA를 확인합니다. `git fetch origin main` 후 병합 SHA가 `origin/main`에 포함되는지 확인합니다. 삭제 대상은 이 세션에서 작업한 브랜치와 소유가 확인된 전용 worktree로 한정하고 정확한 이름·경로·head SHA를 기록합니다.
2. `git worktree list --porcelain`으로 위치·사용 브랜치·잠금을 확인합니다. 다른 작업자가 사용 중이거나 소유가 불명확한 worktree는 보존합니다. 각 대상에서 미커밋·untracked뿐 아니라 ignored 파일도 확인합니다. `local.properties`, 인증 파일 등 개인 파일은 내용 노출 없이 보존 여부를 확인하며, 삭제 경로 밖에 완전하고 읽을 수 있는 별도 사본이 확인되지 않으면 worktree를 남깁니다. 확실한 생성물인 빌드 출력만 정리할 수 있습니다.
3. 로컬/원격 브랜치가 확인한 PR head SHA 그대로이며 최신 `origin/main`의 조상인지 확인합니다. 병합 이후 추가 커밋·이동한 원격 ref·squash/rebase로 조상 관계가 불명확한 경우 해당 브랜치를 보존하고 이유를 보고합니다. main 등 공용 브랜치는 삭제하지 않습니다.
4. 기본 작업 디렉터리는 삭제하지 않습니다. 그곳에서 작업했다면 깨끗한 상태에서 `git switch main`과 `git pull --ff-only origin main`으로 돌아옵니다. 전용 worktree는 사용 중인 프로세스가 없고 개인 파일 보존이 확인된 경우에만, 다른 작업 디렉터리에서 `git worktree remove <정확한 경로>`로 제거합니다. 프로세스 사용 여부가 불명확해도 보존하며, 정리를 위해 다른 프로세스를 종료하지 않습니다. dirty/locked worktree에 `--force`, 임의 stash/reset, 광역 prune을 사용하지 않습니다.
5. 삭제 직전 로컬 SHA도 다시 확인하고, 사용 중인 worktree가 없는 작업 브랜치를 `git branch -d <작업 브랜치>`로 삭제합니다. 원격은 삭제 직전 SHA를 다시 확인하고 `git push --force-with-lease=refs/heads/<작업 브랜치>:<확인한 PR head SHA> origin --delete <작업 브랜치>`로 해당 ref만 삭제합니다. 이 lease는 확인 이후 새 커밋이 올라오면 삭제를 거부하기 위한 것입니다. 실패하면 강제 삭제로 우회하지 않습니다. 이미 없는 브랜치는 정리 완료로 취급합니다.
6. 원격 ref·로컬 브랜치·worktree 목록과 main 상태를 다시 확인합니다. main pull 실패나 보존한 항목이 있으면 경로/브랜치와 이유를 남깁니다. 정리 실패가 병합 성공을 취소하지는 않으며, 남은 항목을 완료로 쓰지 않습니다.

완료 보고에는 **변경 이유와 결과·참여 내역의 근거·수행/미수행 검증·이슈/PR·병합 또는 인계 상태·브랜치/worktree 정리 결과**를 짧게 적습니다. 남은 외부 인증이나 실제 기기 검증은 구체적인 후속 작업으로 남깁니다.
