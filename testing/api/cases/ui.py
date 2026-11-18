"""前端可用性（UI）冒烟用例：页面能打开、代理能通。"""
from harness import Client, case, expect, expect_contains, expect_ok

import cfg

FRONTEND = Client("http://localhost:5173")


@case("UI-01", "前端首页可以打开", "ui", "P0")
def ui_01():
    # Vite 只对 Accept 含 text/html 的请求做 SPA 回退，否则返回 404
    resp = FRONTEND.request("GET", "/", headers={"Accept": "text/html"})
    expect(resp.status == 200, "首页返回 {}".format(resp.status))
    html = resp.raw.decode("utf-8", "replace")
    expect_contains(html, "<div id=\"app\"", "首页挂载点")
    expect_contains(html, "助农直播管理系统", "页面标题")


@case("UI-02", "前端代理能访问后端", "ui", "P0")
def ui_02():
    resp = FRONTEND.get("/api/health")
    expect_ok(resp)
    expect(resp.data["status"] == "UP", "后端健康状态不是 UP")


@case("UI-03", "前端能取到登录验证码", "ui", "P0")
def ui_03():
    resp = FRONTEND.get("/api/auth/captcha")
    expect_ok(resp)
    expect(bool(resp.data.get("captchaId")), "验证码接口未返回 captchaId")
    expect(bool(resp.data.get("question")), "验证码接口未返回题面")
