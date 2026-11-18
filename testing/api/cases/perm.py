"""权限与越权（PERM）用例。"""
import support as sp
from harness import case, expect_error, expect_ok, expect_status


@case("PERM-01", "农户令牌调用系统管理接口返回 403", "perm", "P0")
def perm_01():
    resp = sp.API.get("/api/admin/accounts", token=sp.token("farmer1"))
    expect_error(resp, 403, "FORBIDDEN")


@case("PERM-02", "直播管理员不能查看系统日志", "perm", "P0")
def perm_02():
    resp = sp.API.get("/api/admin/logs", token=sp.token("live"))
    expect_error(resp, 403, "FORBIDDEN")


@case("PERM-03", "统计员不能执行写操作", "perm", "P0")
def perm_03():
    resp = sp.API.post("/api/live/farmers", {
        "farmerNo": sp.unique("X"),
        "name": "越权测试",
        "countyCode": "340422",
        "phone": "13900000009",
    }, token=sp.token("viewer"))
    expect_error(resp, 403, "FORBIDDEN")


@case("PERM-04", "系统管理员不能维护农户档案", "perm", "P0")
def perm_04():
    resp = sp.API.post("/api/live/farmers", {
        "farmerNo": sp.unique("X"),
        "name": "越权测试",
        "countyCode": "340422",
        "phone": "13900000008",
    }, token=sp.token("admin"))
    expect_error(resp, 403, "FORBIDDEN")


@case("PERM-05", "未登录访问受保护接口返回 401", "perm", "P0")
def perm_05():
    resp = sp.API.get("/api/stats/overview")
    expect_error(resp, 401, "UNAUTHORIZED")


@case("PERM-06", "农户看不到他人的预约", "perm", "P0")
def perm_06():
    start, end = sp.slot(25, 10, 2)
    created = sp.create_booking("farmer2", start, end, topic="他人预约")
    expect_ok(created)
    booking_id = created.data["id"]

    resp = sp.API.get("/api/farmer/bookings/{}".format(booking_id),
                      token=sp.token("farmer1"))
    expect_status(resp, 404)
