"""设备故障（FAULT）用例。"""
import support as sp
from harness import case, expect_eq, expect_error, expect_ok

_ctx = {}


def _device_status(device_no):
    resp = sp.API.get("/api/live/devices", token=sp.token("live"),
                      params={"keyword": device_no})
    expect_ok(resp)
    return resp.data["items"][0]["status"]


@case("FAULT-01", "设备报修后进入维修状态", "fault", "P0")
def fault_01():
    device_no = sp.device_no(3)
    device_id = sp.device_id(3)
    resp = sp.API.post("/api/live/devices/{}/faults".format(device_id), {
        "faultPart": "补光灯",
        "description": "补光灯不亮，现场无法正常补光",
    }, token=sp.token("live"))
    expect_ok(resp)
    expect_eq(resp.data["status"], "reported", "故障记录状态")
    _ctx["fault"] = resp.data["id"]
    expect_eq(_device_status(device_no), "maintenance", "设备状态")


@case("FAULT-02", "同一设备不能重复报修", "fault", "P0")
def fault_02():
    resp = sp.API.post("/api/live/devices/{}/faults".format(sp.device_id(3)), {
        "faultPart": "麦克风",
        "description": "重复报修测试",
    }, token=sp.token("live"))
    expect_error(resp, 409, "FAULT_ACTIVE")


@case("FAULT-03", "报修会自动取消该设备未来已通过预约", "fault", "P0")
def fault_03():
    start, end = sp.slot(18, 10, 2)
    booking = sp.create_approved_booking("farmer3", start, end, device=sp.device_id(2))

    resp = sp.API.post("/api/live/devices/{}/faults".format(sp.device_id(2)), {
        "faultPart": "三脚架",
        "description": "三脚架损坏",
    }, token=sp.token("live"))
    expect_ok(resp)
    _ctx["fault2"] = resp.data["id"]

    row = sp.booking_row(booking["id"])
    expect_eq(row["status"], "canceled", "受影响预约状态")
    expect_eq(sp.lock_count(booking["id"]), 0, "受影响预约的资源锁")


@case("FAULT-04", "维修完成设备恢复可用", "fault", "P0")
def fault_04():
    resp = sp.API.post(
        "/api/live/devices/{}/faults/{}/resolve".format(sp.device_id(2), _ctx["fault2"]),
        {"resolution": "已更换三脚架并复测"}, token=sp.token("live"))
    expect_ok(resp)
    expect_eq(_device_status(sp.device_no(2)), "available", "维修后设备状态")
