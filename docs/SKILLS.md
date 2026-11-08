# 공통 작업 스킬

home-timing의 로컬 Codex·Claude Code 세션에서 같은 시작·완료 절차를 사용합니다. 팀이 레포를 받으면 스킬 파일도 함께 받습니다. 개인 전역 스킬이나 권한 설정은 변경하지 않습니다.

| 단계 | 스킬 | 하는 일 |
| --- | --- | --- |
| 시작·재개 | [team-task-start](../.agents/skills/team-task-start/SKILL.md) | 기존 변경 확인, 최신 main, 이슈·담당·범위·완료 기준, 작업 브랜치 |
| 완료·인계 | [team-task-finish](../.agents/skills/team-task-finish/SKILL.md) | 변경별 검증, 실제 참여자 기록, 개발 기록·PR, 승인 범위 안의 병합/인계, 병합 후 작업 브랜치·worktree 정리 |

## 호출

Codex에서는 `$team-task-start`, `$team-task-finish`를 작업 요청과 함께 사용합니다. Claude Code에서는 `/team-task-start`, `/team-task-finish`를 사용합니다. 자동 선택 여부와 관계없이 [AGENTS](../AGENTS.md)의 단계별 연결을 따릅니다.

```text
$team-task-start
교체일 계산 작업의 기존 이슈를 찾아 범위와 검증 기준을 확인하고 시작해줘.
```

```text
$team-task-finish
현재 작업을 검증하고 참여 내역과 근거를 남겨 PR로 정리해줘.
```

스킬이 목록에 나타나지 않으면 레포를 최신화하고 세션을 다시 시작해 확인합니다. 같은 이름의 개인 스킬이 있다면 이 레포의 파일 경로를 명시합니다.

## 공통 본문과 진입점

```text
.agents/skills/
├─ team-task-start/SKILL.md       공통 시작 절차
└─ team-task-finish/SKILL.md      공통 완료 절차

.claude/skills/
├─ team-task-start/SKILL.md       공통 시작 본문을 읽는 진입점
└─ team-task-finish/SKILL.md      공통 완료 본문을 읽는 진입점
```

Codex는 `.agents/skills`에서 발견하고 Claude Code는 `.claude/skills`에서 발견합니다. 상세 작업 절차는 공통 본문에서만 고칩니다. Claude 진입점에는 중복 절차나 별도 규칙을 추가하지 않습니다. 파일 링크를 사용하므로 symlink 설정을 요구하지 않습니다.

## 작업 기준

깨끗한 새 작업은 `git switch main` → `git pull --ff-only origin main` → 이슈 확인/작성 → 이슈 번호 브랜치 순서입니다. 진행 중 작업을 재개할 때는 기존 브랜치와 변경을 보존합니다. main의 미게시 커밋·divergence·다른 worktree 사용을 강제 초기화로 해결하지 않습니다.

태스크의 크기·필수 정보·브랜치 규칙은 [협업 규칙](../CONTRIBUTING.md#태스크-기준), 검증은 [TESTING](TESTING.md), 참여 내역은 [기여 기록](report/README.md)이 기준입니다. 스킬은 사용자의 현재 요청과 권한 안에서 절차를 수행하며 병합·배포·외부 권한 변경을 새로 승인하지 않습니다.

발견 경로 참고: [OpenAI Skills](https://learn.chatgpt.com/docs/build-skills), [Claude Code Skills](https://code.claude.com/docs/en/skills). 파일·참조 검증과 실제 도구 런타임의 자동 선택 확인은 구분해 기록합니다.
