"""预约调整（ADJ）用例。"""
import support as sp
from harness import case, expect, expect_eq, expect_error, expect_ok

_ctx = {}


@case("ADJ-01", "开播前 24 小时外可提交调整", "adjust", "P0")
def adj_01():
    start, end = sp.slot(9, 13, 2)
    booking = sp.create_approved_booking("farmer1", start, end)
    _ctx["booking"] = booking["id"]

    new_start, new_end = sp.slot(10, 15, 2)
    resp = sp.API.post("/api/farmer/bookings/{}/adjustments".format(booking["id"]), {
        "newVenueId": sp.venue_id(),
        "newDeviceId": sp.device_id(2),
        "newStartTime": new_start,
        "newEndTime": new_end,
        "reason": "农户临时改期",
    }, token=sp.token("farmer1"))
    expect_ok(resp)
    expect_eq(resp.data["status"], "pending", "调整申请状态")
    _ctx["adjustment"] = resp.data["id"]
    _ctx["new_start"] = new_start


@case("ADJ-02", "开播前 24 小时内不能申请调整", "adjust", "P0")
def adj_02():
    start, end = sp.hours_from_now(10)
    booking_id = sp.insert_booking("farmer1", start, end, status="approved")
    new_start, new_end = sp.slot(22, 10, 2)
    resp = sp.API.post("/api/farmer/bookings/{}/adjustments".format(booking_id), {
        "newVenueId": sp.venue_id(),
        "newDeviceId": sp.device_id(1),
        "newStartTime": new_start,
        "newEndTime": new_end,
        "reason": "太晚了",
    }, token=sp.token("farmer1"))
    expect_error(resp, 409, "ADJUST_WINDOW_CLOSED")


@case("ADJ-03", "同一预约只能有一条待审批调整", "adjust", "P0")
def adj_03():
    start, end = sp.slot(10, 9, 2)
    booking = sp.create_approved_booking("farmer1", start, end)
    _ctx["booking2"] = booking["id"]

    body = {
        "newVenueId": sp.venue_id(),
        "newDeviceId": sp.device_id(2),
        "newStartTime": sp.slot(10, 19, 2)[0],
        "newEndTime": sp.slot(10, 19, 2)[1],
        "reason": "改到晚上",
    }
    first = sp.API.post("/api/farmer/bookings/{}/adjustments".format(booking["id"]),
                        body, token=sp.token("farmer1"))
    expect_ok(first)
    _ctx["adjustment2"] = first.data["id"]
    _ctx["new_start2"] = body["newStartTime"]

    second = sp.API.post("/api/farmer/bookings/{}/adjustments".format(booking["id"]),
                         body, token=sp.token("farmer1"))
    expect_error(second, 409, "ADJUST_PENDING")


@case("ADJ-04", "调整审批通过后换时段并迁移资源锁", "adjust", "P0")
def adj_04():
    before = sp.booking_row(_ctx["booking2"])
    expect_eq(before["start_time"], sp.slot(10, 9, 2)[0], "调整前开始时间")
    expect_eq(sp.lock_count(_ctx["booking2"]), 2, "调整前锁数量")

    resp = sp.API.post("/api/live/adjustments/{}/review".format(_ctx["adjustment2"]),
                       {"result": "approve", "comment": "同意改期"},
                       token=sp.token("live"))
    expect_ok(resp)

    after = sp.booking_row(_ctx["booking2"])
    expect_eq(after["start_time"], _ctx["new_start2"], "调整后开始时间")
    expect_eq(after["device_id"], str(sp.device_id(2)), "调整后设备")
    expect_eq(sp.lock_count(_ctx["booking2"]), 2, "新时段锁数量")

    old_lock = sp.db.scalar(
        "SELECT COUNT(*) FROM resource_lock WHERE booking_id = {} "
        "AND slot_time = '{}'".format(int(_ctx["booking2"]), sp.slot(10, 9, 2)[0]))
    expect_eq(old_lock, "0", "旧时段锁应被删除")


@case("ADJ-05", "调整可撤回，驳回需填写意见", "adjust", "P0")
def adj_05():
    start, end = sp.slot(11, 9, 2)
    booking = sp.create_approved_booking("farmer1", start, end)
    booking_id = booking["id"]

    def submit(target_day, target_hour):
        new_start, new_end = sp.slot(target_day, target_hour, 2)
        return sp.API.post("/api/farmer/bookings/{}/adjustments".format(booking_id), {
            "newVenueId": sp.venue_id(),
            "newDeviceId": sp.device_id(2),
            "newStartTime": new_start,
            "newEndTime": new_end,
            "reason": "改期原因",
        }, token=sp.token("farmer1"))

    first = submit(12, 10)
    expect_ok(first)
    withdrawn = sp.API.post(
        "/api/farmer/adjustments/{}/withdraw".format(first.data["id"]),
        token=sp.token("farmer1"))
    expect_ok(withdrawn)
    expect_eq(withdrawn.data["status"], "withdrawn", "撤回后状态")

    second = submit(12, 15)
    expect_ok(second)
    too_short = sp.API.post("/api/live/adjustments/{}/review".format(second.data["id"]),
                            {"result": "reject", "comment": "不行"},
                            token=sp.token("live"))
    expect_error(too_short, 400, "BAD_REQUEST")

    rejected = sp.API.post("/api/live/adjustments/{}/review".format(second.data["id"]),
                           {"result": "reject", "comment": "该时段已有安排"},
                           token=sp.token("live"))
    expect_ok(rejected)
    expect_eq(rejected.data["status"], "rejected", "驳回后状态")
    expect_eq(sp.booking_row(booking_id)["status"], "approved", "原预约仍有效")
