"""HTTP 客户端、用例注册、断言。

设计原则：一个用例只依赖公开接口；需要精确构造历史数据时，
才用 db 模块直接写库（例如"开播前 1 小时"这种靠接口造不出来的场景）。
"""
import json
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid

import cfg


class SkipTest(Exception):
    """前置条件不满足（例如未配置 AI Key），跳过而不是判失败。"""


class Resp:
    def __init__(self, status, body, raw=b"", headers=None):
        self.status = status
        self.body = body
        self.raw = raw
        # 统一小写，兼容 Vite 代理转发后大小写变化
        self.headers = {k.lower(): v for k, v in (headers or {}).items()}

    def header(self, name, default=""):
        return self.headers.get(name.lower(), default)

    @property
    def ok(self):
        return self.status < 400

    @property
    def data(self):
        return (self.body or {}).get("data")

    @property
    def code(self):
        return (self.body or {}).get("code")

    @property
    def message(self):
        return (self.body or {}).get("message") or ""

    def describe(self, limit=400):
        if isinstance(self.body, dict):
            text = json.dumps(self.body, ensure_ascii=False)
        else:
            text = self.raw.decode("utf-8", "replace")
        return "HTTP {}  {}".format(self.status, text[:limit])


class Client:
    """极简 HTTP 客户端，只用标准库，避免装依赖。"""

    def __init__(self, base=None):
        self.base = (base or cfg.BASE_URL).rstrip("/")

    def request(self, method, path, body=None, token=None, params=None,
                data=None, headers=None, timeout=30):
        url = self.base + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        hdrs = {"Accept": "application/json"}
        payload = None
        if body is not None:
            payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
            hdrs["Content-Type"] = "application/json; charset=utf-8"
        if data is not None:
            payload = data
        if token:
            hdrs["Authorization"] = "Bearer " + token
        if headers:
            hdrs.update(headers)

        req = urllib.request.Request(url, data=payload, method=method, headers=hdrs)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
                status = resp.status
                resp_headers = {k.lower(): v for k, v in resp.headers.items()}
        except urllib.error.HTTPError as ex:
            raw = ex.read()
            status = ex.code
            resp_headers = {k.lower(): v for k, v in ex.headers.items()}
        except urllib.error.URLError as ex:
            raise RuntimeError("无法连接 {}：{}".format(url, ex.reason)) from ex

        parsed = None
        if "json" in (resp_headers.get("content-type") or "").lower():
            try:
                parsed = json.loads(raw.decode("utf-8"))
            except ValueError:
                parsed = None
        return Resp(status, parsed, raw, resp_headers)

    def get(self, path, token=None, params=None):
        return self.request("GET", path, token=token, params=params)

    def post(self, path, body=None, token=None):
        return self.request("POST", path, body=body, token=token)

    def put(self, path, body=None, token=None):
        return self.request("PUT", path, body=body, token=token)

    def delete(self, path, token=None):
        return self.request("DELETE", path, token=token)

    def upload(self, path, token, field, filename, content,
               content_type="application/pdf", form=None):
        """multipart 上传（资质材料、知识库文档）。"""
        boundary = "----dsh" + uuid.uuid4().hex
        chunks = []
        for key, value in (form or {}).items():
            chunks.append(
                ('--{}\r\nContent-Disposition: form-data; name="{}"\r\n\r\n{}\r\n'
                 .format(boundary, key, value)).encode("utf-8")
            )
        chunks.append(
            ('--{}\r\nContent-Disposition: form-data; name="{}"; filename="{}"\r\n'
             "Content-Type: {}\r\n\r\n".format(boundary, field, filename, content_type)
             ).encode("utf-8")
        )
        chunks.append(content)
        chunks.append("\r\n--{}--\r\n".format(boundary).encode("utf-8"))
        body = b"".join(chunks)
        return self.request(
            "POST", path, token=token, data=body,
            headers={"Content-Type": "multipart/form-data; boundary=" + boundary},
        )

    # ---- 登录相关 ----

    def captcha(self):
        resp = self.get("/api/auth/captcha")
        expect_status(resp, 200)
        data = resp.data or {}
        numbers = [int(x) for x in re.findall(r"\d+", data.get("question", ""))]
        if len(numbers) < 2:
            raise AssertionError("验证码题面无法解析: " + str(data))
        return data["captchaId"], str(numbers[0] + numbers[1])

    def login(self, username, password=None):
        captcha_id, answer = self.captcha()
        return self.post("/api/auth/login", {
            "username": username,
            "password": password or cfg.TEST_PASSWORD,
            "captchaId": captcha_id,
            "captchaAnswer": answer,
        })

    def login_token(self, username, password=None):
        resp = self.login(username, password)
        expect_ok(resp)
        return resp.data["token"]


# ---------------------------------------------------------------- 用例注册

CASES = []


def case(case_id, title, module, priority="P0"):
    """把函数登记成一条用例。"""
    def wrapper(fn):
        CASES.append({
            "id": case_id,
            "title": title,
            "module": module,
            "priority": priority,
            "fn": fn,
        })
        return fn
    return wrapper


# ---------------------------------------------------------------- 断言

def expect(condition, message):
    if not condition:
        raise AssertionError(message)


def expect_status(resp, *codes):
    if resp.status not in codes:
        raise AssertionError(
            "期望 HTTP {}，实际 {}".format("/".join(str(c) for c in codes), resp.describe())
        )


def expect_ok(resp):
    expect_status(resp, 200)
    if resp.code != "OK":
        raise AssertionError("期望 code=OK，实际 " + resp.describe())


def expect_error(resp, status, code=None):
    expect_status(resp, status)
    if code is not None and resp.code != code:
        raise AssertionError("期望错误码 {}，实际 {}".format(code, resp.describe()))


def expect_eq(actual, expected, label):
    if actual != expected:
        raise AssertionError("{}：期望 {}，实际 {}".format(label, expected, actual))


def expect_in(value, container, label):
    if value not in container:
        raise AssertionError("{}：{} 不在 {} 中".format(label, value, container))


def expect_contains(haystack, needle, label):
    if needle not in (haystack or ""):
        raise AssertionError("{}：{!r} 不包含 {!r}".format(label, haystack, needle))
