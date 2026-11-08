---
name: team-task-start
description: Start or resume implementation work in home-timing with a current main base, a scoped GitHub issue, and an isolated task branch. Skip read-only questions and general advice.
---

# 팀 작업 시작

[공통 지침](../../../AGENTS.md)과 [협업 규칙](../../../CONTRIBUTING.md)을 읽습니다. 이 스킬은 현재 사용자가 요청한 작업만 준비하며 병합·배포·외부 권한 변경을 추가로 허용하지 않습니다.

## 새 작업과 재개 구분

1. 레포 루트·현재 브랜치·`git status --short --branch`·관련 이슈/PR을 확인합니다. 사용자의 목표가 이미 진행 중인 이슈에 속하면 그 작업을 재개합니다. 메시지마다 새 이슈를 만들지 않습니다.
2. 미커밋 변경이 있으면 관련 작업인지 먼저 판단하고 그대로 보존합니다. 임의 stash·commit·reset·파일 삭제로 작업 트리를 비우지 않습니다.
3. 다른 작업을 새로 시작해야 하면 기존 작업과 별도 worktree를 사용합니다. 재개라면 현재 브랜치에서 `git fetch origin main`만 수행하고, 진행 중 파일을 건드리는 base 갱신은 커밋/검증 가능한 시점에 처리합니다.

## 깨끗한 새 작업

깨끗한 작업 트리에서는 아래 순서로 최신 main을 준비합니다.

```sh
git switch main
git pull --ff-only origin main
git rev-parse HEAD
git rev-parse origin/main
```

두 SHA가 같아야 새 작업의 기준이 됩니다. pull이 실패하거나 로컬 main에만 있는 커밋이 있으면 reset·강제 push로 맞추지 않습니다. 원본을 보존하고 fetch한 `origin/main`에서 별도 worktree를 준비합니다. main이 다른 worktree에서 사용 중인 경우도 같습니다.

## 이슈와 브랜치

- [태스크 기준](../../../CONTRIBUTING.md#태스크-기준)에 따라 기존 이슈를 검색해 재사용하거나, 요청 범위와 GitHub 쓰기 권한 안에서 새 이슈를 작성합니다.
- 작업 결과·포함/제외 범위·확인된 예정 담당·사용자 관점 완료 기준·검증 방법을 정합니다. 목표가 없으면 먼저 확인하고, 담당자가 불명확하면 미정으로 남깁니다.
- 이슈 번호가 정해진 뒤 `<type>/<issue>-<short-slug>` 브랜치를 만듭니다. type은 feature/fix/docs/chore 중 작업 성격에 맞춥니다. 한 작업의 변경은 그 브랜치에만 둡니다.
- 기존 브랜치가 있으면 소속 이슈와 작업 내용을 확인해 재사용합니다. 다른 사람의 브랜치를 강제로 바꾸거나 중복 PR을 만들지 않습니다.
- GitHub 접근이 안 되면 이슈를 발행하지 못했다는 점을 밝히고 로컬 준비를 진행합니다. 이슈 번호나 게시 성공을 꾸며내지 않습니다.

시작 결과로 **이슈·브랜치·main 기준 SHA·예정 담당·이번 범위·완료/검증 기준**을 짧게 남긴 뒤 구현을 진행합니다. 상세 계획은 이슈가 기준이며 별도 일지를 중복 작성하지 않습니다.
