#!/usr/bin/env python3
"""One read-only I2570 lookup. Credentials never go in CLI arguments or output."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

DEFAULT_BARCODE = "4902430597241"
KEY_NAME = "FOODSAFETY_API_KEY"
KEY_FILE = Path(__file__).resolve().parents[2] / ".env.foodsafety.local"
SERVICE = "I2570"
MAX_ROWS = 100
MAX_RESPONSE_BYTES = 2_000_000
MESSAGES = {
    "INFO-100": "인증키가 유효하지 않습니다. 식품안전나라 인증키를 확인하세요.",
    "INFO-110": "인증키는 확인됐지만 I2570 활용신청 내역이 없습니다.",
    "INFO-120": "I2570 서비스 신청상태를 확인하세요.",
    "INFO-200": "해당하는 데이터가 없습니다.",
    "INFO-300": "유효 호출건수를 초과했습니다.",
    "INFO-310": "샘플키 호출 제한입니다. 본인 인증키를 사용하세요.",
    "INFO-320": "시간당 호출 제한입니다. 나중에 다시 시도하세요.",
    "INFO-400": "서비스 접근 권한이 없습니다.",
    "INFO-500": "현재 접속 중인 인증키입니다. 잠시 후 다시 시도하세요.",
    "INFO-600": "인증키가 만료됐습니다.",
    "ERROR-503": "서비스 호출 제한입니다. 나중에 다시 시도하세요.",
}


class LookupError(Exception):
    """A local error with a fixed, credential-free message."""


def load_key(path=KEY_FILE):
    value = os.environ.get(KEY_NAME, "").strip()
    if value:
        return value
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if line.strip().startswith(KEY_NAME + "="):
                value = line.strip().split("=", 1)[1].strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                if value:
                    return value
    if sys.stdin.isatty():
        return getpass.getpass("식품안전나라 I2570 인증키 (입력 숨김): ").strip()
    raise LookupError(".env.foodsafety.local의 FOODSAFETY_API_KEY에 키를 넣으세요.")


def request_url(key, barcode):
    if not key or key.lower() == "sample":
        raise LookupError("본인 인증키가 필요합니다. sample 키는 필터 검증에 사용하지 않습니다.")
    return ("https://openapi.foodsafetykorea.go.kr/api/"
            + urllib.parse.quote(key, safe="")
            + f"/{SERVICE}/json/1/{MAX_ROWS}/BRCD_NO={barcode}")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Do not forward a credential-bearing URL to a different location or to HTTP.
        return None


def fetch(key, barcode):
    request = urllib.request.Request(request_url(key, barcode), headers={
        "Accept": "application/json",
        "User-Agent": "HomeTiming-I2570-local-check/1.0",
    })
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as error:
        raise LookupError(f"HTTP {error.code}: 요청 실패. 자동 재시도하지 않았습니다.") from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise LookupError("HTTPS 연결 실패 또는 시간 초과입니다. 자동 재시도하지 않았습니다.") from None
    if len(body) > MAX_RESPONSE_BYTES:
        raise LookupError("응답이 예상 크기를 초과했습니다.")
    return body


def decode_response(body):
    try:
        text = body.decode("utf-8-sig")
        data = json.loads(text)
    except (UnicodeDecodeError, json.JSONDecodeError):
        # Some provider errors arrive as XML despite a JSON request.
        try:
            root = ET.fromstring(body)
            result = root if root.tag == "RESULT" else root.find(".//RESULT")
            if result is not None and result.findtext("CODE"):
                return {"RESULT": {"CODE": result.findtext("CODE")}}
        except ET.ParseError:
            pass
        raise LookupError("JSON 또는 API 오류 형식의 응답이 아닙니다. 원문은 출력하지 않습니다.") from None
    if not isinstance(data, dict):
        raise LookupError("예상하지 못한 응답 구조입니다.")
    return data


def evaluate(data, barcode):
    service = data.get(SERVICE, data)
    if not isinstance(service, dict):
        raise LookupError("I2570 응답 구조를 확인할 수 없습니다.")
    result = service.get("RESULT", data.get("RESULT", {}))
    if not isinstance(result, dict):
        raise LookupError("API 상태 코드를 확인할 수 없습니다.")
    code = result.get("CODE")
    if not isinstance(code, str) or not re.fullmatch(r"(?:INFO|ERROR)-\d{3}", code):
        raise LookupError("API 상태 코드를 확인할 수 없습니다.")
    if code == "INFO-200":
        return 3, [f"결과: {code} — {MESSAGES[code]}", "이 응답은 바코드 자체가 잘못됐다는 뜻은 아닙니다."]
    if code != "INFO-000":
        return 4, [f"결과: {code} — {MESSAGES.get(code, '제공기관의 API 메시지 설명을 확인하세요.')}"]
    rows = service.get("row", [])
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise LookupError("상품 목록의 응답 구조가 올바르지 않습니다.")
    try:
        total = int(service["total_count"])
    except (KeyError, ValueError, TypeError):
        raise LookupError("조회 건수를 확인할 수 없습니다.") from None
    if total < 0 or total < len(rows):
        raise LookupError("조회 건수와 응답 목록이 일치하지 않습니다.")
    exact = [row for row in rows if str(row.get("BRCD_NO", "")).strip() == barcode]
    lines = [f"API 상태: {code}", f"필터 응답: 전체 {total}건 / 수신 {len(rows)}건"]
    if total > len(rows):
        lines.append("주의: 일부 결과만 받았습니다. 전체 조회 완료로 판단하지 않습니다.")
    if not exact:
        lines += ["요청한 번호와 정확히 일치하는 상품이 수신 목록에 없습니다.",
                  "다른 번호의 결과를 제품명으로 사용하지 않습니다."]
        return 3, lines
    lines.append(f"바코드 정확히 일치: {len(exact)}건")
    for index, row in enumerate(exact, 1):
        lines.append(f"\n[{index}]")
        for label, field in [("바코드", "BRCD_NO"), ("제품명", "PRDT_NM"),
                             ("회사명", "CMPNY_NM"), ("최종 갱신", "LAST_UPDT_DTM"),
                             ("분류", "PRDLST_NM")]:
            lines.append(f"{label}: {row.get(field) or '(제공 없음)'}")
    if not any(str(row.get("PRDT_NM") or "").strip() for row in exact):
        lines.append("번호는 일치하지만 제품명이 제공되지 않았습니다.")
        return 3, lines
    return 0, lines


def redact(text, key):
    for value in {key, urllib.parse.quote(key, safe="")}:
        if value:
            text = text.replace(value, "[인증키 숨김]")
    # Remove terminal control characters from external product fields.
    return "".join(c for c in text if c in "\n\t" or c.isprintable())


def main(argv=None):
    parser = argparse.ArgumentParser(description="식품안전나라 I2570 제품명 조회 (한 번만 요청)")
    parser.add_argument("barcode", nargs="?", default=DEFAULT_BARCODE,
                        help=f"바코드 숫자 (기본값: {DEFAULT_BARCODE})")
    args = parser.parse_args(argv)
    if not re.fullmatch(r"\d{8}|\d{12,14}", args.barcode, flags=re.ASCII):
        parser.error("바코드는 8자리 또는 12~14자리 숫자 문자열이어야 합니다.")
    key = ""
    try:
        key = load_key()
        body = fetch(key, args.barcode)
        status, lines = evaluate(decode_response(body), args.barcode)
        print(redact(f"조회 서비스: {SERVICE}\n요청 바코드: {args.barcode}\n" + "\n".join(lines), key))
        return status
    except LookupError as error:
        print(redact(str(error), key), file=sys.stderr)
        return 2
    except (OSError, UnicodeError):
        print("로컬 인증키 파일을 읽을 수 없습니다.", file=sys.stderr)
        return 2
    except (KeyboardInterrupt, EOFError):
        print("\n조회 취소.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
