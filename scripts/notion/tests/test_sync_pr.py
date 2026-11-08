import copy
import io
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

from scripts.notion.sync_pr import Api, SyncError, build_record, literal_html, main, sync_record

REPO = "kw-mobile/home-timing"
BODY = """## 목적과 연결

교체일 계산과 상태 표시를 추가합니다.

요구사항/화면: F-04

## 참여자별 기여

| 참여자 | 실제 역할 | 수행 내용 | 근거 |
| --- | --- | --- | --- |
| @alice | 구현 | 교체일 계산 | app/src/Calculator.java |
| @bob | 검증 | 윤년·월말 확인 | 검증 댓글 |
| Codex (도구) | 초안 작성 | 테스트 초안 | 커밋 |

## 검증 결과

단위 테스트 성공. F-05는 이번 범위가 아닙니다.
"""


def pr(body=BODY):
    return {
        "number": 4, "title": "교체일 계산", "body": body,
        "html_url": f"https://github.com/{REPO}/pull/4",
        "merged": True, "merged_at": "2026-11-08T10:00:00Z",
        "merge_commit_sha": "a" * 40,
        "base": {"ref": "main", "repo": {"full_name": REPO}},
        "user": {"login": "pr-author-is-not-implementer"},
    }


def record(body=BODY):
    return build_record(pr(body), [], REPO, now="2026-11-08T10:01:00Z")


class QueueApi:
    def __init__(self, results):
        self.results, self.calls = list(results), []

    def request(self, method, path, data=None, retry=True):
        self.calls.append((method, path, copy.deepcopy(data), retry))
        return self.results.pop(0)


class RecordTests(unittest.TestCase):
    def test_contributors_are_declared_people_not_pr_author_or_tool(self):
        properties = record()["properties"]
        self.assertEqual(properties["참여자"]["multi_select"], [{"name": "@alice"}, {"name": "@bob"}])
        self.assertEqual(properties["기여 확인"]["select"]["name"], "기록됨")
        self.assertEqual(properties["PR 작성자"]["rich_text"][0]["text"]["content"], "pr-author-is-not-implementer")
        self.assertEqual(properties["요구사항"]["multi_select"], [{"name": "F-04"}])

    def test_missing_or_unconfirmed_people_remain_unconfirmed(self):
        for body in ("", BODY.replace("@alice", "확인 필요").replace("@bob", "미확정")):
            properties = record(body)["properties"]
            self.assertEqual(properties["참여자"]["multi_select"], [{"name": "확인 필요"}])
            self.assertEqual(properties["기여 확인"]["select"]["name"], "확인 필요")

    def test_unmerged_wrong_base_and_wrong_repository_are_rejected(self):
        variants = [pr(), pr(), pr()]
        variants[0]["merged"] = False
        variants[1]["base"]["ref"] = "develop"
        variants[2]["base"]["repo"]["full_name"] = "another/repo"
        for value in variants:
            with self.assertRaises(SyncError):
                build_record(value, [], REPO)

    @patch("scripts.notion.sync_pr.urlopen")
    def test_noncanonical_pr_numbers_are_rejected_before_api_access(self, open_request):
        for number in ("02", "-1", "2;echo data", "2２"):
            with patch.dict("os.environ", {"GITHUB_REPOSITORY": REPO, "PR_NUMBER": number}, clear=True):
                with self.assertRaises(SyncError):
                    main()
        open_request.assert_not_called()

    def test_only_tagged_evidence_comments_are_copied(self):
        comments = [
            {"body": "진행 중입니다", "html_url": "https://github.com/comment/1", "user": {"login": "alice"}},
            {"body": "### [검증] 실기기 결과\n\n재부팅 후 도착", "html_url": "https://github.com/comment/2", "user": {"login": "bob"}},
        ]
        markdown = build_record(pr(), comments, REPO)["markdown"]
        self.assertIn("재부팅 후 도착", markdown)
        self.assertNotIn("진행 중입니다", markdown)
        self.assertIn("https://github.com/comment/2", markdown)

    def test_source_xml_is_inert_and_code_is_preserved(self):
        source = '<synced-block-reference url="https://example.com" />\n\n```java\nList<String> names;\n```'
        safe = literal_html(source)
        self.assertNotIn('<synced-block-reference', safe)
        self.assertIn("List<String> names;", safe)


class SyncTests(unittest.TestCase):
    def test_create_queries_first_and_does_not_blindly_retry_create(self):
        api = QueueApi([{"results": [], "has_more": False}, {"id": "page", "url": "https://notion.so/page"}])
        sync_record(api, "source", record())
        self.assertEqual([call[1] for call in api.calls], ["/data_sources/source/query", "/pages"])
        self.assertEqual(api.calls[0][2]["filter"], {"property": "PR", "url": {"equals": f"https://github.com/{REPO}/pull/4"}})
        self.assertFalse(api.calls[1][3])
        self.assertEqual(api.calls[1][2]["parent"], {"type": "data_source_id", "data_source_id": "source"})
        self.assertEqual(api.calls[1][2]["markdown_version"], "v2")

    def test_repeat_sync_updates_existing_managed_page(self):
        api = QueueApi([
            {"results": [{"id": "existing", "properties": {"자동 기록": {"checkbox": True}}}]},
            {}, {"id": "existing"},
        ])
        sync_record(api, "source", record())
        self.assertEqual([(call[0], call[1]) for call in api.calls], [
            ("POST", "/data_sources/source/query"),
            ("PATCH", "/pages/existing/markdown"), ("PATCH", "/pages/existing"),
        ])
        self.assertNotIn("allow_deleting_content", str(api.calls[1][2]))

    def test_manual_and_duplicate_rows_are_not_overwritten(self):
        for results in ([{"id": "manual", "properties": {}}], [{"id": "one"}, {"id": "two"}]):
            api = QueueApi([{"results": results}])
            with self.assertRaises(SyncError):
                sync_record(api, "source", record())
            self.assertEqual(len(api.calls), 1)

    def test_rendering_warnings_are_not_reported_as_a_clean_sync(self):
        api = QueueApi([
            {"results": [{"id": "existing", "properties": {"자동 기록": {"checkbox": True}}}]},
            {"warnings": [{"code": "unsupported_tag"}]},
        ])
        with self.assertRaises(SyncError):
            sync_record(api, "source", record())
        self.assertEqual(len(api.calls), 2)

    @patch("scripts.notion.sync_pr.time.sleep")
    @patch("scripts.notion.sync_pr.urlopen", side_effect=URLError("timeout"))
    def test_ambiguous_create_timeout_is_not_retried(self, open_request, sleep):
        with self.assertRaises(SyncError):
            Api("https://api.notion.com/v1", "test-token").request("POST", "/pages", {}, retry=False)
        self.assertEqual(open_request.call_count, 1)
        sleep.assert_not_called()

    @patch("scripts.notion.sync_pr.time.sleep")
    @patch("scripts.notion.sync_pr.urlopen")
    def test_rate_limited_query_waits_before_retry(self, open_request, sleep):
        open_request.side_effect = [
            HTTPError("https://api.notion.com", 429, "limited", {"Retry-After": "2"}, None),
            io.BytesIO(b'{"results": []}'),
        ]
        self.assertEqual(Api("https://api.notion.com/v1", "test-token").request("POST", "/data_sources/id/query", {}), {"results": []})
        sleep.assert_called_once_with(2)


if __name__ == "__main__":
    unittest.main()
