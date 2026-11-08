"""Mirror merged PRs into an explicitly managed Notion worklog; stdlib only."""

import html
import json
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

NOTION_VERSION = "2026-03-11"


class SyncError(RuntimeError):
    pass


class Api:
    def __init__(self, base, token, notion=False):
        self.base = base
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "home-timing-worklog",
        }
        if notion:
            self.headers["Notion-Version"] = NOTION_VERSION

    def request(self, method, path, data=None, retry=True):
        encoded = None if data is None else json.dumps(data, ensure_ascii=False).encode()
        for attempt in range(3):
            try:
                request = Request(self.base + path, data=encoded, headers=self.headers, method=method)
                with urlopen(request, timeout=30) as response:
                    return json.load(response)
            except HTTPError as error:
                if retry and attempt < 2 and error.code in (429, 500, 502, 503, 504):
                    delay = float(error.headers.get("Retry-After", 2 ** attempt))
                    if not 0 <= delay <= 60:
                        raise SyncError("API requested a longer delay; rerun later") from None
                    time.sleep(delay)
                    continue
                raise SyncError(f"API {method} failed with HTTP {error.code}; inspect access/configuration") from None
            except (URLError, TimeoutError, OSError):
                if retry and attempt < 2:
                    time.sleep(2 ** attempt)
                    continue
                raise SyncError("API connection failed; a create may have succeeded. Rerun to query first") from None


def section(body, *names):
    pattern = r"^##\s+(?:" + "|".join(re.escape(name) for name in names) + r")\s*\n(.*?)(?=^##\s|\Z)"
    match = re.search(pattern, body, flags=re.MULTILINE | re.DOTALL)
    return match.group(1).strip() if match else ""


def participants(body):
    text = section(body, "참여자별 기여")
    people = []
    incomplete = False
    for line in text.splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [cell.strip() for cell in re.split(r"(?<!\\)\|", line.strip().strip("|"))]
        if len(cells) < 4 or cells[0] in ("참여자", "참여자/도구") or re.fullmatch(r"[-: ]+", cells[0]):
            continue
        name, role, work, evidence = cells[:4]
        if "(도구)" in name or "[bot]" in name or name in ("Codex", "Claude Code", "ChatGPT"):
            continue
        if not all((name, role, work, evidence)) or any(
            word in name + role + work for word in ("확인 필요", "미확정", "이름 또는", "@계정")
        ):
            incomplete = True
            continue
        name = re.sub(r"\[([^]]+)\]\([^)]+\)", r"\1", name).replace("`", "").replace("**", "").strip()
        if len(name) > 100 or "," in name:
            incomplete = True
            continue
        if name not in people:
            people.append(name)
    return people, incomplete or not people


def literal_html(markdown):
    """Keep common fenced/inline code; make source HTML/Notion XML inert."""
    result, fence = [], None
    for line in markdown.splitlines():
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            run = marker.group(1)
            if fence is None:
                fence = run
            elif run[0] == fence[0] and len(run) >= len(fence):
                fence = None
            result.append(line)
        elif fence:
            result.append(line)
        else:
            parts = re.split(r"(`+[^`\n]+`+)", line)
            result.append("".join(part if part.startswith("`") else re.sub(r"<[^>]+>", lambda m: html.escape(m[0]), part) for part in parts))
    return "\n".join(result)


def rich_text(value, limit=2000):
    return [{"type": "text", "text": {"content": str(value)[:limit]}}]


def build_record(pr, comments, repository, now=None):
    number = pr["number"]
    url = f"https://github.com/{repository}/pull/{number}"
    if not pr.get("merged") or not pr.get("merged_at") or pr.get("base", {}).get("ref") != "main":
        raise SyncError("Only PRs merged into main can be published")
    if pr.get("html_url") != url or pr.get("base", {}).get("repo", {}).get("full_name") != repository:
        raise SyncError("PR repository does not match the configured repository")
    body = pr.get("body") or ""
    people, incomplete = participants(body)
    purpose = section(body, "목적과 연결", "목적과 결과", "변경 내용") or pr["title"]
    first_paragraph = purpose.split("\n\n")[0]
    summary = re.sub(r"\[([^]]+)\]\([^)]+\)", r"\1", first_paragraph)
    summary = re.sub(r"\s+", " ", summary.replace("**", "").replace("`", "")).strip()[:300]
    declared = re.findall(r"^(?:요구사항/화면|요구사항):\s*(.*)$", body, re.MULTILINE)
    requirements = sorted(set(re.findall(r"\bF-\d{2}\b", " ".join(declared))))
    if not requirements:
        requirements = [tag for tag in ("환경", "문서") if tag in pr["title"]]
    timestamp = now or datetime.now(timezone.utc).isoformat()
    properties = {
        "작업": {"title": rich_text(f"#{number} {pr['title']}")},
        "병합일": {"date": {"start": pr["merged_at"]}},
        "참여자": {"multi_select": [{"name": name} for name in people or ["확인 필요"]]},
        "요약": {"rich_text": rich_text(summary)},
        "요구사항": {"multi_select": [{"name": name} for name in requirements]},
        "기여 확인": {"select": {"name": "확인 필요" if incomplete else "기록됨"}},
        "PR": {"url": url},
        "PR 작성자": {"rich_text": rich_text(pr["user"]["login"])},
        "PR 번호": {"number": number},
        "병합 커밋": {"rich_text": rich_text(pr["merge_commit_sha"])},
        "저장소": {"rich_text": rich_text(repository)},
        "마지막 동기화": {"date": {"start": timestamp}},
        "자동 기록": {"checkbox": True},
    }
    markdown = (
        "> GitHub PR에서 가져온 자동 기록입니다. 재동기화 시 본문이 교체됩니다. 보존할 팀 메모는 별도 페이지에 남겨주세요.\n\n"
        f"[원본 PR]({url}) · 병합 커밋: `{pr['merge_commit_sha']}`\n\n"
        + literal_html(body[:50000] or "PR 본문이 없습니다. 원본 PR에서 내용을 보완해주세요.")
    )
    if len(body) > 50000:
        markdown += f"\n\n본문 일부를 생략했습니다. 전체 내용은 [원본 PR]({url})에서 확인합니다."
    evidence = [comment for comment in comments if re.match(
        r"^\s*(?:#{1,6}\s*)?\[(?:검증|결정|문제 해결|리뷰 반영)\]", comment.get("body", "")
    )][-5:]
    if evidence:
        markdown += "\n\n## 근거 댓글\n"
        for comment in evidence:
            markdown += f"\n[댓글 원본]({comment['html_url']}) · GitHub 작성 계정: {comment['user']['login']}\n\n"
            markdown += literal_html(comment["body"][:4000]) + "\n"
    return {"properties": properties, "markdown": markdown, "markdown_version": "v2"}


def sync_record(notion, data_source_id, record):
    pr_url = record["properties"]["PR"]["url"]
    found = notion.request("POST", f"/data_sources/{data_source_id}/query", {
        "filter": {"property": "PR", "url": {"equals": pr_url}}, "page_size": 2,
    })
    pages = found.get("results", [])
    if len(pages) > 1 or found.get("has_more"):
        raise SyncError("Duplicate PR rows exist; resolve them before syncing")
    if not pages:
        # Never blindly retry a non-idempotent create after a timeout or 5xx.
        return notion.request("POST", "/pages", {
            "parent": {"type": "data_source_id", "data_source_id": data_source_id}, **record,
        }, retry=False)
    page = pages[0]
    if not page.get("properties", {}).get("자동 기록", {}).get("checkbox"):
        raise SyncError("A manual row already uses this PR URL; it will not be overwritten")
    rendered = notion.request("PATCH", f"/pages/{page['id']}/markdown", {
        "markdown_version": "v2", "type": "replace_content",
        "replace_content": {"new_str": record["markdown"]},
    })
    if rendered.get("warnings"):
        raise SyncError("Notion returned rendering warnings; inspect the page before retrying")
    return notion.request("PATCH", f"/pages/{page['id']}", {"properties": record["properties"]})


def main():
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    number = os.environ.get("PR_NUMBER", "")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository) or not re.fullmatch(r"[1-9][0-9]*", number):
        raise SyncError("GITHUB_REPOSITORY and a positive PR_NUMBER are required")
    missing = [name for name in ("GH_TOKEN", "NOTION_TOKEN", "NOTION_WORKLOG_DATA_SOURCE") if not os.environ.get(name)]
    if missing:
        raise SyncError("Missing configuration: " + ", ".join(missing))
    github = Api("https://api.github.com", os.environ["GH_TOKEN"])
    pr = github.request("GET", f"/repos/{repository}/pulls/{number}")
    comments, page = [], 1
    while True:
        batch = github.request("GET", f"/repos/{repository}/issues/{number}/comments?per_page=100&page={page}")
        comments.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    record = build_record(pr, comments, repository)
    notion = Api("https://api.notion.com/v1", os.environ["NOTION_TOKEN"], notion=True)
    result = sync_record(notion, os.environ["NOTION_WORKLOG_DATA_SOURCE"], record)
    url = result.get("url", "")
    print(f"Synced PR #{number}: {url}")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
            summary.write(f"Notion development record for PR #{number}: {url}\n")


if __name__ == "__main__":
    try:
        main()
    except SyncError as error:
        print(f"Notion sync failed: {error}", file=sys.stderr)
        sys.exit(1)
