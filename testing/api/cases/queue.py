"""排队与候补（QUE）用例。"""
import re

import support as sp
from harness import case, expect, expect_eq, expect_error, expect_ok

_ctx = {}


@case("QUE-01", "时段被占用时可加入排队并生成排队号", "queue", "P0")
def que_01():
    start, end = sp.slot(13, 10, 2)
    holder = sp.create_approved_booking("farmer1", start, end, device=sp.device_id(1))
    _ctx["holder"] = holder["id"]

    resp = sp.join_queue("farmer2", start, end)
    expect_ok(resp)
    data = resp.data
    expect_eq(data["status"], "queued", "排队状态")
    expect(re.match(r"^Q-NFTEST02-\d{8}-\d{3}$", data["queueNo"]) is not None,
           "排队号格式不符: " + str(data["queueNo"]))
    _ctx["ticket"] = data["id"]


@case("QUE-02", "时段空闲时应直接预约而不是排队", "queue", "P0")
def que_02():
    start, end = sp.slot(14, 10, 2)
    resp = sp.join_queue("farmer3", start, end)
    expect_error(resp, 400, "SLOT_NOT_OCCUPIED")


@case("QUE-03", "自己的预约不需要排队", "queue", "P0")
def que_03():
    start, end = sp.slot(13, 10, 2)
    resp = sp.join_queue("farmer1", start, end)
    expect_error(resp, 400, "QUEUE_SELF_OCCUPIED")


@case("QUE-04", "开播前 24 小时内不能加入排队", "queue", "P0")
def que_04():
    start, end = sp.hours_from_now(10)
    sp.insert_booking("farmer1", start, end, status="approved")
    resp = sp.join_queue("farmer2", start, end)
    expect_error(resp, 400, "BAD_REQUEST")


@case("QUE-05", "同一农户同一时段只能有一个有效排队", "queue", "P0")
def que_05():
    start, end = sp.slot(15, 10, 2)
    sp.create_approved_booking("farmer1", start, end, device=sp.device_id(1))

    first = sp.join_queue("farmer3", start, end)
    expect_ok(first)
    second = sp.join_queue("farmer3", start, end)
    expect_error(second, 409, "QUEUE_DUPLICATE")


@case("QUE-06", "场地释放后队首升为候补并收到通知", "queue", "P0")
def que_06():
    expect_ok(sp.cancel("farmer1", _ctx["holder"]))
    row = sp.queue_row(_ctx["ticket"])
    expect_eq(row["status"], "candidate", "队首状态")
    expect(sp.notification_count("farmer2", "candidate") >= 1,
           "候补通知未生成")


@case("QUE-07", "候补确认后生成待审核预约并锁定资源", "queue", "P0")
def que_07():
    resp = sp.confirm_queue("farmer2", _ctx["ticket"])
    expect_ok(resp)
    row = sp.queue_row(_ctx["ticket"])
    expect_eq(row["status"], "confirmed", "确认后排队状态")

    booking_id = int(sp.db.scalar(
        "SELECT confirmed_booking_id FROM queue_ticket WHERE id = {}".format(
            int(_ctx["ticket"]))))
    _ctx["confirmed_booking"] = booking_id
    expect_eq(sp.booking_row(booking_id)["status"], "pending_review", "确认后预约状态")
    expect_eq(sp.lock_count(booking_id), 2, "确认后资源锁数量")


@case("QUE-08", "管理员可关闭整个队列", "queue", "P0")
def que_08():
    start, end = sp.slot(16, 10, 2)
    sp.create_approved_booking("farmer1", start, end, device=sp.device_id(1))
    joined = sp.join_queue("farmer2", start, end)
    expect_ok(joined)

    resp = sp.API.post("/api/live/queues/{}/close".format(joined.data["id"]),
                       token=sp.token("live"))
    expect_ok(resp)
    expect_eq(sp.queue_row(joined.data["id"])["status"], "closed", "关闭后状态")


@case("QUE-09", "候补确认后取消触发 72 小时冷却", "queue", "P0")
def que_09():
    expect_ok(sp.cancel("farmer2", _ctx["confirmed_booking"]))

    start, end = sp.slot(17, 10, 2)
    sp.create_approved_booking("farmer1", start, end, device=sp.device_id(1))
    resp = sp.join_queue("farmer2", start, end)
    expect_error(resp, 409, "QUEUE_COOLDOWN")
