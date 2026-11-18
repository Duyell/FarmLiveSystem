"""登录与账号安全（AUTH）用例。"""
import cfg
import support as sp
from harness import case, expect, expect_error, expect_eq, expect_ok


@case("AUTH-01", "正常登录返回令牌与角色", "auth", "P0")
def auth_01():
    username = sp.state()["accounts"]["admin"]
    resp = sp.API.login(username)
    expect_ok(resp)
    data = resp.data or {}
    expect(bool(data.get("token")), "未返回 token")
    expect_eq(data["user"]["roleCode"], "system_admin", "登录角色")
    expect_eq(data["user"]["mustChangePassword"], False, "首登改密标记")


@case("AUTH-02", "验证码错误被拒绝", "auth", "P0")
def auth_02():
    captcha_id, answer = sp.API.captcha()
    resp = sp.API.post("/api/auth/login", {
        "username": sp.state()["accounts"]["admin"],
        "password": cfg.TEST_PASSWORD,
        "captchaId": captcha_id,
        "captchaAnswer": str(int(answer) + 1),
    })
    expect_error(resp, 400, "CAPTCHA_INVALID")


@case("AUTH-03", "连续 3 次密码错误后锁定账号", "auth", "P0")
def auth_03():
    username = sp.state()["probes"]["lock"]
    for index in range(3):
        resp = sp.API.login(username, "Wrong@1234")
        expect_error(resp, 401)
    blocked = sp.API.login(username, cfg.TEST_PASSWORD)
    expect_error(blocked, 429, "ACCOUNT_LOCKED")


@case("AUTH-04", "首次登录未改密时拦截业务接口", "auth", "P0")
def auth_04():
    username = sp.state()["probes"]["must"]
    token = sp.API.login_token(username)
    resp = sp.API.get("/api/admin/dashboard", token=token)
    expect_error(resp, 403, "FORBIDDEN")


@case("AUTH-05", "改密后旧密码失效、新密码可用", "auth", "P0")
def auth_05():
    username = sp.state()["probes"]["pwd"]
    token = sp.API.login_token(username)
    new_password = "NewPass@2026"
    changed = sp.API.post("/api/auth/change-password",
                          {"oldPassword": cfg.TEST_PASSWORD,
                           "newPassword": new_password}, token=token)
    expect_ok(changed)
    expect_error(sp.API.login(username, cfg.TEST_PASSWORD), 401)
    expect_ok(sp.API.login(username, new_password))

    # 还原密码，便于 --keep 模式下重复执行
    new_token = sp.API.login_token(username, new_password)
    restore = sp.API.post("/api/auth/change-password",
                          {"oldPassword": new_password,
                           "newPassword": cfg.TEST_PASSWORD}, token=new_token)
    expect_ok(restore)


@case("AUTH-06", "停用账号不能登录", "auth", "P0")
def auth_06():
    resp = sp.API.login(sp.state()["probes"]["disabled"])
    expect_error(resp, 401)


@case("AUTH-07", "无效令牌访问受保护接口返回 401", "auth", "P0")
def auth_07():
    resp = sp.API.get("/api/admin/dashboard", token="invalid-token-for-test")
    expect_error(resp, 401, "UNAUTHORIZED")
