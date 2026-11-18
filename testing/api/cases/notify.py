"""通知（NOTI）用例。"""
import support as sp
from harness import (SkipTest, case, expect, expect_eq, expect_error,
                     expect_ok, expect_status)


@case("NOTI-01", "审核通过后农户收到站内通知", "notify", "P0")
def noti_01():
    count = int(sp.db.scalar(
        "SELECT COUNT(*) FROM notification n JOIN account a ON a.id = n.account_id "
        "WHERE a.farmer_id = {} AND n.type = 'review' "
        "AND n.channel = 'in_app'".format(int(sp.farmer_id("farmer1")))))
    expect(count >= 1, "未找到审核类站内通知")


@case("NOTI-02", "五类通知模板齐备", "notify", "P0")
def noti_02():
    rows = sp.db.query("SELECT DISTINCT type FROM notification_template")
    types = {row[0] for row in rows}
    expected = {"review", "queue", "candidate", "reminder", "device_fault"}
    missing = expected - types
    expect(not missing, "缺少通知类型: " + ", ".join(sorted(missing)))


@case("NOTI-03", "未读数与已读状态联动", "notify", "P1")
def noti_03():
    token = sp.token("farmer2")
    before = sp.API.get("/api/notifications/unread-count", token=token)
    expect_ok(before)
    count = int(before.data)
    if count == 0:
        raise SkipTest("当前账号没有未读通知")

    listing = sp.API.get("/api/notifications", token=token, params={"size": 20})
    expect_ok(listing)
    unread = [item for item in listing.data["items"] if not item["read"]]
    expect(unread, "未读数为 {} 但列表里没有未读项".format(count))

    marked = sp.API.post("/api/notifications/{}/read".format(unread[0]["id"]),
                         token=token)
    expect_ok(marked)
    after = sp.API.get("/api/notifications/unread-count", token=token)
    expect_ok(after)
    expect_eq(int(after.data), count - 1, "已读后未读数")


@case("NOTI-04", "站内通知不支持手动重试", "notify", "P1")
def noti_04():
    rows = sp.db.query(
        "SELECT n.id FROM notification n JOIN account a ON a.id = n.account_id "
        "WHERE a.farmer_id = {} AND n.channel = 'in_app' LIMIT 1".format(
            int(sp.farmer_id("farmer1"))))
    if not rows:
        raise SkipTest("没有可用的站内通知记录")
    resp = sp.API.post("/api/live/notifications/{}/retry".format(rows[0][0]),
                       token=sp.token("live"))
    expect_error(resp, 400, "BAD_REQUEST")


@case("NOTI-05", "模板只允许白名单变量", "notify", "P0")
def noti_05():
    bad = sp.API.post("/api/live/notification-templates", {
        "code": "tpl_" + sp.run_id(),
        "name": "变量非法模板",
        "type": "review",
        "titleTemplate": "预约结果：${unknownVar}",
        "contentTemplate": "内容",
        "enabled": True,
    }, token=sp.token("live"))
    expect_error(bad, 400, "BAD_REQUEST")

    good = sp.API.post("/api/live/notification-templates", {
        "code": "tplok" + sp.run_id(),
        "name": "合法模板",
        "type": "review",
        "titleTemplate": "${farmerName} 的预约${bookingNo}",
        "contentTemplate": "审核结果：${reviewComment}",
        "enabled": False,
    }, token=sp.token("live"))
    expect_ok(good)
