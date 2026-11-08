# Development History 자동 기록

main에 병합된 PR을 [Notion Development History](https://app.notion.com/p/48d520845c72469aa0ba3bfb2bc4fe43)에 기록합니다. GitHub가 원본이고 Notion은 팀이 보기 위한 복사본입니다. 별도 웹훅 서버나 AI 요약 API는 사용하지 않습니다.

## 현재 상태

**동기화 설정이 활성화되어 있습니다.** 2026-11-08 사용자가 `NOTION_TOKEN`을 등록한 뒤 실제 GitHub Actions → Notion 쓰기를 확인했습니다. 대화에서 사용하는 Notion 연결과 Actions의 인증은 별개입니다.

- [기존 PR #2 갱신](https://github.com/kw-mobile/home-timing/actions/runs/42472633912): 수동 초기 행을 같은 페이지에서 갱신했습니다.
- [PR #2 재실행](https://github.com/kw-mobile/home-timing/actions/runs/42472665340): 같은 페이지 ID를 유지했고 DB 조회에서 해당 PR 행이 하나임을 확인했습니다.
- [PR #4 신규 생성](https://github.com/kw-mobile/home-timing/actions/runs/42472667632): 새 개발 기록 행을 생성했습니다.

이는 실제 API 인증·생성·갱신의 근거입니다. 위 세 실행은 수동 실행입니다. 병합 이벤트 자동 실행 결과는 [활성화 PR #9](https://github.com/kw-mobile/home-timing/pull/9)의 Actions와 검증 댓글에서 별도 확인합니다.

## 기록 내용

- PR 제목·원본 링크·작성 계정·병합 시각·병합 커밋
- 본문의 참여자별 기여 표에 적힌 사람, 사용자 확인을 받은 PR 작성자 이름과 요약
- 명시한 요구사항 ID, 기여 기록의 누락 여부
- PR 본문과 `[검증]`, `[결정]`, `[문제 해결]`, `[리뷰 반영]`으로 시작하는 근거 댓글

사람별 기여는 [PR 템플릿](../../.github/pull_request_template.md)의 네 열(참여자·역할·수행 내용·근거)을 사용합니다. 도구는 `(도구)`를 붙여 구분합니다. 계정과 이름은 [`team_members.json`](../../scripts/notion/team_members.json)에서 관리합니다. GitHub 계정의 대소문자는 구분하지 않습니다. 확인된 PR 작성자도 참여자에 표시하고 본문에 PR 작성 역할을 명시합니다. 이 이름 연결로 구현·검증 역할을 추정하지 않습니다. 기여 확인의 ‘기록됨’은 표가 채워졌다는 뜻이며 사실 확인이나 리뷰 승인을 뜻하지 않습니다. 누락·미확정 행은 ‘확인 필요’로 표시합니다.

요구사항은 `요구사항/화면: F-04`처럼 선언한 줄에서만 읽습니다. DB 이름을 바꿔도 ID로 연결되므로 동작하지만 속성명·타입은 코드 계약이므로 함께 바꾸어야 합니다.

## 담당자 최초 연결

1. 팀 자동화에는 **내부 연결(Internal connection)**을 사용합니다. 워크스페이스 소유자가 [Notion Developer portal](https://app.notion.com/developers/connections)에서 `Home Timing GitHub` 등의 이름으로 프로젝트 워크스페이스에 연결을 만듭니다. **Configuration** 탭에서 API 토큰을 복사하고 콘텐츠 읽기·생성·수정 권한을 설정합니다. Development History DB에서 우측 상단 **••• → Add connections**로 이 연결을 추가합니다. 토큰 발급만으로 DB 접근 권한이 생기지 않습니다. [공식 인증 안내](https://developers.notion.com/guides/authorization/overview)를 참고합니다.
2. [GitHub Actions Secrets](https://github.com/kw-mobile/home-timing/settings/secrets/actions)에 **`NOTION_TOKEN`**으로 등록합니다. 토큰을 PR·이슈·Notion 본문·대화에 적지 않습니다.
3. GitHub Repository Variables를 확인합니다. 현재 값은 아래와 같습니다.

| Variable | 값 |
| --- | --- |
| `NOTION_WORKLOG_DATA_SOURCE` | `700b01cb-ba6f-41b6-8c80-8735092f1af9` |
| `NOTION_SYNC_ENABLED` | `true` — 현재 활성, 중지 시 `false` |

4. 자동화 코드가 main에 반영된 후 `NOTION_SYNC_ENABLED=true`로 설정합니다.
5. Actions의 **Notion development record → Run workflow**에서 `pr_number=2`로 실행합니다. CLI를 쓰면 아래와 같습니다.

```sh
gh workflow run notion-sync.yml --ref main -f pr_number=2
```

6. [PR #2 수동 초기 스냅샷](https://app.notion.com/p/4128bac3963f819bbcb0cdc7155d3ccf)을 자동 관리 대상으로 등록해두었습니다. 같은 행의 본문·근거 댓글이 갱신되는지 확인합니다. 같은 번호로 다시 실행해 행이 늘지 않고 갱신되는지 확인한 뒤 [이슈 #3](https://github.com/kw-mobile/home-timing/issues/3)에 결과를 남깁니다.

새 연결이나 토큰 교체 시에도 생성·갱신·재실행을 확인합니다. 문제가 있으면 실행 여부 Variable을 `false`로 돌려 기록 작업만 중지할 수 있습니다.

## 동작과 한계

- main 대상 PR의 병합 완료 이벤트에 실행합니다. 닫기만 한 PR이나 진행 중 PR은 게시하지 않습니다. 수동 실행도 실제 병합 상태를 API로 확인합니다.
- 토큰을 쓰는 작업은 `pull_request_target`의 병합 이벤트에서 **main 코드만 checkout**합니다. PR 제목·본문·댓글을 셸 명령으로 실행하지 않습니다.
- PR 번호는 선행 0 없는 양의 정수만 허용합니다. `02` 같은 수동 입력은 API 호출 전에 거부합니다.
- 같은 PR의 작업은 GitHub concurrency로 직렬화하고 PR URL로 기존 행을 조회합니다. 중복 행이 이미 있으면 임의로 선택하지 않고 실패합니다. Notion에 원자적인 고유 제약을 만드는 방식은 아닙니다.
- `자동 기록` 체크박스가 켜진 행만 갱신합니다. 동일 URL의 수동 행은 덮어쓰지 않습니다. 이 필드는 자동화 관리 허용 표식이며, 최초 예시가 수동으로 등록됐는지는 본문의 안내로 구분합니다.
- 본문 교체와 속성 갱신은 두 API 호출이며 원자적이지 않습니다. 중간 실패 시 새 본문·이전 속성이 함께 남을 수 있으므로 Actions 실패를 확인하고 같은 PR로 재실행합니다.
- 자동 관리 행의 **본문은 재동기화 시 교체**됩니다. 보존할 팀 메모는 별도 페이지에 적습니다. 자식 페이지/DB의 강제 삭제 옵션은 사용하지 않습니다.
- 429·일시 오류는 제한적으로 재시도합니다. 생성 요청의 실패는 실제 생성 여부가 불확실할 수 있으므로 무조건 반복하지 않습니다. 재실행하면 먼저 기존 행을 조회합니다.
- 병합 시점의 스냅샷입니다. 병합 후 본문·댓글을 보완했다면 같은 PR 번호로 수동 재동기화합니다.
- 본문은 최대 50,000자, 근거 댓글은 최근 5개·각 4,000자까지 가져옵니다. 전체 기록은 원본 PR 링크로 확인합니다. GitHub HTML/Notion XML은 문자로 표시하고 일반 Markdown은 Notion 형식으로 전달합니다.
- 실패는 해당 Actions 실행에 남습니다. Notion 기록 실패가 앱 코드를 바꾸거나 병합을 되돌리지는 않습니다.

## 개발 검증

Python 버전은 [`.python-version`](../../.python-version), 구현은 [`sync_pr.py`](../../scripts/notion/sync_pr.py)입니다. 추가 Python 패키지는 사용하지 않습니다.

```sh
python -m unittest discover -s scripts/notion/tests -v
```

이 테스트는 기여자 판정·중복/수동 행 보호·생성 재시도·API 요청 계약을 **오프라인 모의 응답**으로 확인합니다. 실제 Notion 인증·렌더링·쓰기 성공의 증거가 아니며, 위 최초 연결 검증이 별도로 필요합니다.

API 기준: [Notion Markdown](https://developers.notion.com/guides/data-apis/working-with-markdown-content), [데이터 소스 조회](https://developers.notion.com/reference/query-a-data-source). `Notion-Version`은 코드에 고정합니다.
