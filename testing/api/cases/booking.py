"""直播预约（BOOK）用例。"""
from datetime import datetime, timedelta

import support as sp
from harness import case, expect, expect_eq, expect_error, expect_ok

_ctx = {}


def _at(days, hour, minute=0, plus_hours=2):
    start = (datetime.now() + timedelta(days=days)).replace(
        hour=hour, minute=minute, second=0, microsecond=0)
    return sp.fmt(start), sp.fmt(start + timedelta(hours=plus_hours))


@case("BOOK-01", "正常提交预约进入待审核", "booking", "P0")
def book_01():
    start, end = sp.slot(5, 9, 2)
    resp = sp.create_booking("farmer1", start, end, topic="草莓专场")
    expect_ok(resp)
    expect_eq(resp.data["status"], "pending_review", "提交后的状态")
    _ctx["main"] = resp.data["id"]


@case("BOOK-02", "非整点时段被拒绝", "booking", "P0")
def book_02():
    start, end = _at(5, 10, 30, 1)
    resp = sp.create_booking("farmer1", start, end)
    expect_error(resp, 400, "BOOKING_TIME_INVALID")


@case("BOOK-03", "时长超过 4 小时被拒绝", "booking", "P0")
def book_03():
    start, end = sp.slot(5, 13, 5)
    resp = sp.create_booking("farmer1", start, end)
    expect_error(resp, 400, "BOOKING_TIME_INVALID")


@case("BOOK-04", "跨天时段被拒绝", "booking", "P0")
def book_04():
    start = (datetime.now() + timedelta(days=5)).replace(
        hour=23, minute=0, second=0, microsecond=0)
    end = start + timedelta(hours=2)
    resp = sp.create_booking("farmer1", sp.fmt(start), sp.fmt(end))
    expect_error(resp, 400, "BOOKING_TIME_INVALID")


@case("BOOK-05", "距开播不足 2 小时被拒绝", "booking", "P0")
def book_05():
    start, end = sp.hours_from_now(1)
    resp = sp.create_booking("farmer1", start, end)
    expect_error(resp, 400, "BOOKING_TIME_INVALID")


@case("BOOK-06", "同场地同时段第二笔预约被拒绝", "booking", "P0")
def book_06():
    start, end = sp.slot(6, 14, 2)
    first = sp.create_booking("farmer1", start, end, device=sp.device_id(1))
    expect_ok(first)
    _ctx["conflict"] = first.data["id"]

    second = sp.create_booking("farmer2", start, end, device=sp.device_id(2))
    expect_error(second, 409, "BOOKING_CONFLICT")


@case("BOOK-07", "审核通过后按小时锁定资源", "booking", "P0")
def book_07():
    resp = sp.approve(_ctx["conflict"])
    expect_ok(resp)
    expect_eq(resp.data["status"], "approved", "审核后状态")
    expect_eq(sp.lock_count(_ctx["conflict"]), 2, "2 小时应产生 2 条资源锁")


@case("BOOK-08", "驳回必须填原因和推荐时间", "booking", "P0")
def book_08():
    start, end = sp.slot(7, 9, 1)
    created = sp.create_booking("farmer1", start, end, topic="驳回测试")
    expect_ok(created)
    booking_id = created.data["id"]

    too_short = sp.review(booking_id, "reject", "不行")
    expect_error(too_short, 400, "BAD_REQUEST")

    recommended = sp.slot(21, 10)[0]
    rejected = sp.reject(booking_id, "时段冲突，建议改期", recommended)
    expect_ok(rejected)
    expect_eq(rejected.data["status"], "rejected", "驳回后状态")
    expect(bool(rejected.data["recommendedTime"]), "未返回推荐时间")


@case("BOOK-09", "开播前 24 小时内不能取消", "booking", "P0")
def book_09():
    start, end = sp.hours_from_now(3)
    booking_id = sp.insert_booking("farmer1", start, end, status="approved")
    resp = sp.cancel("farmer1", booking_id)
    expect_error(resp, 409, "CANCEL_WINDOW_CLOSED")


@case("BOOK-10", "24 小时外取消并释放资源", "booking", "P0")
def book_10():
    start, end = sp.slot(8, 9, 2)
    resp = sp.create_booking("farmer1", start, end, topic="取消测试")
    expect_ok(resp)
    booking_id = resp.data["id"]
    expect_ok(sp.approve(booking_id))
    expect_eq(sp.lock_count(booking_id), 2, "审核后锁数量")

    cancelled = sp.cancel("farmer1", booking_id)
    expect_ok(cancelled)
    expect_eq(cancelled.data["status"], "canceled", "取消后状态")
    expect_eq(sp.lock_count(booking_id), 0, "取消后资源锁应清空")


@case("BOOK-11", "场次结束才能标记完成", "booking", "P0")
def book_11():
    start, end = sp.days_ago(1, 10, 2)
    past_id = sp.insert_booking("farmer1", start, end, status="approved")
    done = sp.complete(past_id, "completed")
    expect_ok(done)
    expect_eq(done.data["status"], "completed", "完成后状态")

    future_start, future_end = sp.slot(9, 9, 2)
    future = sp.create_booking("farmer1", future_start, future_end, topic="未结束场次")
    expect_ok(future)
    expect_ok(sp.approve(future.data["id"]))
    too_early = sp.complete(future.data["id"], "completed")
    expect_error(too_early, 409, "BOOKING_NOT_ENDED")
