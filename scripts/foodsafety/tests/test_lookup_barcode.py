import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("lookup_barcode", Path(__file__).parents[1] / "lookup_barcode.py")
lookup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lookup)
BARCODE = "4902430597241"


def response(rows, total=None):
    return {"I2570": {"RESULT": {"CODE": "INFO-000"},
                     "total_count": str(len(rows) if total is None else total), "row": rows}}


class LookupTest(unittest.TestCase):
    def test_exact_match_returns_product_fields(self):
        code, lines = lookup.evaluate(response([{"BRCD_NO": BARCODE, "PRDT_NM": "샘플 제품", "CMPNY_NM": "테스트회사"}]), BARCODE)
        self.assertEqual(0, code)
        self.assertIn("제품명: 샘플 제품", lines)

    def test_similar_barcode_is_not_a_match(self):
        code, lines = lookup.evaluate(response([{"BRCD_NO": BARCODE + "9", "PRDT_NM": "다른 제품"}]), BARCODE)
        self.assertEqual(3, code)
        self.assertNotIn("다른 제품", "\n".join(lines))

    def test_empty_result_and_provider_no_data_are_not_api_failures(self):
        self.assertEqual(3, lookup.evaluate(response([]), BARCODE)[0])
        self.assertEqual(3, lookup.evaluate({"RESULT": {"CODE": "INFO-200"}}, BARCODE)[0])

    def test_auth_service_and_quota_errors_are_distinct(self):
        for code in ["INFO-100", "INFO-110", "INFO-120", "INFO-300"]:
            status, lines = lookup.evaluate({"RESULT": {"CODE": code}}, BARCODE)
            self.assertEqual(4, status)
            self.assertIn(code, lines[0])
        self.assertIn("활용신청", lookup.evaluate({"RESULT": {"CODE": "INFO-110"}}, BARCODE)[1][0])

    def test_xml_error_and_json_response(self):
        self.assertEqual("INFO-100", lookup.decode_response(b'<RESULT><CODE>INFO-100</CODE></RESULT>')["RESULT"]["CODE"])
        self.assertEqual(response([]), lookup.decode_response(json.dumps(response([])).encode()))
        with self.assertRaises(lookup.LookupError):
            lookup.decode_response(b'<html>server error</html>')

    def test_missing_status_or_count_is_not_success(self):
        for data in [{}, {"I2570": {"RESULT": {"CODE": "INFO-000"}}},
                     {"I2570": {"RESULT": {"CODE": "INFO-000"}, "total_count": "wrong"}}]:
            with self.assertRaises(lookup.LookupError):
                lookup.evaluate(data, BARCODE)

    def test_partial_response_is_disclosed_and_name_is_required(self):
        status, lines = lookup.evaluate(response([], total=101), BARCODE)
        self.assertEqual(3, status)
        self.assertIn("일부 결과", "\n".join(lines))
        self.assertEqual(3, lookup.evaluate(response([{"BRCD_NO": BARCODE}]), BARCODE)[0])

    def test_sample_key_rejected_and_key_path_encoded(self):
        with self.assertRaises(lookup.LookupError):
            lookup.request_url("sample", BARCODE)
        url = lookup.request_url("fake/key+value?", BARCODE)
        self.assertTrue(url.startswith('https://openapi.foodsafetykorea.go.kr/api/'))
        self.assertIn("fake%2Fkey%2Bvalue%3F/I2570", url)
        self.assertTrue(url.endswith("/BRCD_NO=" + BARCODE))

    def test_key_file_and_environment_precedence(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            path = Path(directory) / 'local.env'
            path.write_text("# example\nFOODSAFETY_API_KEY='file-value'\n")
            self.assertEqual("file-value", lookup.load_key(path))
            with patch.dict(os.environ, {lookup.KEY_NAME: "env-value"}):
                self.assertEqual("env-value", lookup.load_key(path))

    def test_main_redacts_keys_in_product_fields(self):
        key = "FAKE-TEST-KEY"
        body = json.dumps(response([{"BRCD_NO": BARCODE, "PRDT_NM": key}])).encode()
        out = io.StringIO()
        with patch.object(lookup, 'load_key', return_value=key), patch.object(lookup, 'fetch', return_value=body), contextlib.redirect_stdout(out):
            self.assertEqual(0, lookup.main([]))
        self.assertNotIn(key, out.getvalue())
        self.assertIn('[인증키 숨김]', out.getvalue())

    def test_network_exception_does_not_expose_url(self):
        error = urllib.error.URLError('https://example.invalid/api/FAKE-TEST-KEY')
        err = io.StringIO()
        with patch.object(lookup, 'load_key', return_value='FAKE-TEST-KEY'), patch.object(lookup.urllib.request.OpenerDirector, 'open', side_effect=error), contextlib.redirect_stderr(err):
            self.assertEqual(2, lookup.main([]))
        self.assertNotIn('FAKE-TEST-KEY', err.getvalue())
        self.assertNotIn('https://', err.getvalue())

    def test_redirect_is_not_followed(self):
        self.assertIsNone(lookup.NoRedirect().redirect_request(None, None, 302, '', {}, 'http://example.invalid/'))


if __name__ == '__main__':
    unittest.main()
