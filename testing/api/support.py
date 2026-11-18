"""公共数据与操作封装：登录态、时间槽、预约与审批的快捷方法。"""
import json
import time
from datetime import datetime, timedelta

import cfg
import db
from harness import Client, SkipTest, expect_ok

API = Client()
_TOKENS = {}
_STATE = {}


def state():
    global _STATE
    if not _STATE:
        if not cfg.STATE_FILE.exists():
            raise SkipTest("未找到 .state.json，请先运行 python setup_env.py")
        _STATE = json.loads(cfg.STATE_FILE.read_text(encoding="utf-8"))
    return _STATE


def token(role):
    """role: admin / live / viewer / farmer1 / farmer2 / farmer3"""
    if role in _TOKENS:
        return _TOKENS[role]
    username = state()["accounts"][role]
    _TOKENS[role] = API.login_token(username)
    return _TOKENS[role]


def farmer_id(role):
    return state()["farmers"][role]


def venue_id():
    return state()["venue"]["id"]


def device_id(index=1):
    return state()["devices"][index - 1]["id"]


def device_no(index=1):
    return state()["devices"][index - 1]["no"]


def run_id():
    return state().get("run", "x")


def unique(prefix):
    return "{}{}".format(prefix, str(int(time.time() * 1000))[-7:])


# ---------------------------------------------------------------- 时间

def fmt(dt):
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def slot(days=0, hour=9, hours=2):
    """返回从明天起第 days 天、hour 点开始、时长 hours 的整点时段。"""
    start = (datetime.now() + timedelta(days=days)).replace(
        hour=hour, minute=0, second=0, microsecond=0
    )
    return fmt(start), fmt(start + timedelta(hours=hours))


def hours_from_now(hours):
    start = (datetime.now() + timedelta(hours=hours)).replace(
        minute=0, second=0, microsecond=0
    )
    return fmt(start), fmt(start + timedelta(hours=2))


def days_ago(days=1, hour=10, hours=2):
    start = (datetime.now() - timedelta(days=days)).replace(
        hour=hour, minute=0, second=0, microsecond=0
    )
    return fmt(start), fmt(start + timedelta(hours=hours))


# ---------------------------------------------------------------- 预约

def create_booking(role, start, end, venue=None, device=None, topic="测试直播",
                   products=None):
    body = {
        "venueId": venue if venue is not None else venue_id(),
        "deviceId": device if device is not None else device_id(1),
        "startTime": start,
        "endTime": end,
        "topic": topic,
        "products": products if products is not None else [
            {"productName": "草莓", "spec": "5斤装", "quantity": 10, "unit": "箱"}
        ],
    }
    return API.post("/api/farmer/bookings", body, token=token(role))


def create_approved_booking(role, start, end, device=None, topic="测试直播"):
    """提交 + 审核通过，返回预约 dict；失败直接抛断言。"""
    resp = create_booking(role, start, end, device=device, topic=topic)
    expect_ok(resp)
    booking = resp.data
    approve(booking["id"])
    row = booking_row(booking["id"])
    if row["status"] != "approved":
        raise AssertionError("审核后状态不是 approved: " + str(row))
    return booking


def review(booking_id, result, comment=None, recommended=None):
    body = {"result": result}
    if comment is not None:
        body["comment"] = comment
    if recommended is not None:
        body["recommendedTime"] = recommended
    return API.post("/api/live/bookings/{}/review".format(booking_id), body,
                    token=token("live"))


def approve(booking_id, comment="测试通过"):
    return review(booking_id, "approve", comment)


def reject(booking_id, comment="时段不合适，请改期", recommended=None):
    if recommended is None:
        recommended = slot(20, 10)[0]
    return review(booking_id, "reject", comment, recommended)


def complete(booking_id, status="completed"):
    return API.post("/api/live/bookings/{}/complete".format(booking_id),
                    {"status": status}, token=token("live"))


def cancel(role, booking_id, reason="测试取消"):
    return API.post("/api/farmer/bookings/{}/cancel".format(booking_id),
                    {"reason": reason}, token=token(role))


# ---------------------------------------------------------------- 排队

def join_queue(role, start, end, venue=None, topic="排队测试"):
    return API.post("/api/farmer/queues", {
        "venueId": venue if venue is not None else venue_id(),
        "startTime": start,
        "endTime": end,
        "topic": topic,
    }, token=token(role))


def confirm_queue(role, queue_id):
    return API.post("/api/farmer/queues/{}/confirm".format(queue_id),
                    token=token(role))


def abandon_queue(role, queue_id):
    return API.post("/api/farmer/queues/{}/abandon".format(queue_id),
                    token=token(role))


# ---------------------------------------------------------------- 查库

def booking_row(booking_id):
    rows = db.query(
        "SELECT id, booking_no, farmer_id, venue_id, device_id, "
        "DATE_FORMAT(start_time,'%Y-%m-%d %H:%i:%s'), "
        "DATE_FORMAT(end_time,'%Y-%m-%d %H:%i:%s'), status "
        "FROM booking WHERE id = {}".format(int(booking_id))
    )
    if not rows:
        raise AssertionError("数据库里找不到预约 " + str(booking_id))
    row = rows[0]
    keys = ["id", "booking_no", "farmer_id", "venue_id", "device_id",
            "start_time", "end_time", "status"]
    return dict(zip(keys, row))


def lock_count(booking_id):
    return int(db.scalar(
        "SELECT COUNT(*) FROM resource_lock WHERE booking_id = {}".format(int(booking_id))
    ))


def queue_row(queue_id):
    rows = db.query(
        "SELECT id, queue_no, status, position, slot_start, slot_end "
        "FROM queue_ticket WHERE id = {}".format(int(queue_id))
    )
    if not rows:
        raise AssertionError("数据库里找不到排队 " + str(queue_id))
    keys = ["id", "queue_no", "status", "position", "slot_start", "slot_end"]
    return dict(zip(keys, rows[0]))


def notification_count(farmer_role, ntype=None):
    sql = ("SELECT COUNT(*) FROM notification n JOIN account a ON a.id = n.account_id "
           "WHERE a.farmer_id = {}".format(int(farmer_id(farmer_role))))
    if ntype:
        sql += " AND n.type = '{}'".format(ntype)
    return int(db.scalar(sql))


def clear_test_notifications():
    ids = state().get("account_ids") or []
    if ids:
        db.run_sql(
            "DELETE FROM notification WHERE account_id IN ({});".format(
                ",".join(str(i) for i in ids))
        )


def insert_booking(role, start, end, status="approved", device=None,
                   topic="历史数据", venue=None):
    """直接写一条预约（用于造"距开播不足 24 小时"这类接口造不出的数据）。"""
    no = unique("HIST")
    d = device if device is not None else device_id(1)
    v = venue if venue is not None else venue_id()
    db.run_sql(
        "INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, "
        "start_time, end_time, topic, status) VALUES ('{}', {}, {}, {}, '{}', '{}', '{}', '{}');"
        .format(no, farmer_id(role), v, d, start, end, topic, status)
    )
    return int(db.scalar("SELECT id FROM booking WHERE booking_no = '{}'".format(no)))


def insert_locks(booking_id, start_dt, hours, device=None, venue=None):
    """手工补资源锁（只有数据库造的已通过预约才需要）。"""
    d = device if device is not None else device_id(1)
    v = venue if venue is not None else venue_id()
    base = datetime.strptime(start_dt, "%Y-%m-%d %H:%M:%S")
    values = ",".join(
        "({}, {}, '{}', {})".format(
            v, d, fmt(base + timedelta(hours=i)), int(booking_id))
        for i in range(int(hours))
    )
    db.run_sql("INSERT INTO resource_lock (venue_id, device_id, slot_time, booking_id) "
               "VALUES {};".format(values))


def require_db():
    if not cfg.CHECK_DB:
        raise SkipTest("FARM_CHECK_DB=0，跳过依赖数据库校验的用例")
