"""基础信息（BASE）用例：农户、场地、设备、资质。"""
import support as sp
from harness import (case, expect, expect_eq, expect_error, expect_ok,
                     expect_status)

_ctx = {}

PDF_BYTES = (b"%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n"
             b"trailer\n<< /Root 1 0 R >>\n%%EOF\n")


def _find_farmer(farmer_no):
    """按编号在列表中查找农户。

    注意：当前版本 /api/live/farmers?keyword= 会返回 500（后端参数个数不匹配，
    见 已知问题.md BUG-01），所以这里先取全量再本地筛选。
    """
    resp = sp.API.get("/api/live/farmers", token=sp.token("live"),
                      params={"size": 100})
    expect_ok(resp)
    for item in resp.data["items"]:
        if item["farmerNo"] == farmer_no:
            return item
    raise AssertionError("农户列表里找不到 " + farmer_no)


@case("BASE-01", "直播管理员新增农户", "base", "P0")
def base_01():
    farmer_no = sp.unique("NF")
    resp = sp.API.post("/api/live/farmers", {
        "farmerNo": farmer_no,
        "name": "自动化农户",
        "countyCode": "340422",
        "phone": "13900000010",
        "qualificationStatus": "pending",
    }, token=sp.token("live"))
    expect_ok(resp)
    data = resp.data
    expect_eq(data["farmerNo"], farmer_no, "农户编号")
    expect_eq(data["county"], "寿县", "县域由区域编码回填")
    _ctx["farmer_id"] = data["id"]
    _ctx["farmer_no"] = farmer_no


@case("BASE-02", "手机号格式校验", "base", "P0")
def base_02():
    resp = sp.API.post("/api/live/farmers", {
        "farmerNo": sp.unique("NF"),
        "name": "手机号错误",
        "countyCode": "340422",
        "phone": "139000000",
    }, token=sp.token("live"))
    expect_error(resp, 400, "BAD_REQUEST")


@case("BASE-03", "农户列表手机号脱敏", "base", "P0")
def base_03():
    item = _find_farmer(_ctx["farmer_no"])
    expect("****" in item["phone"], "手机号未脱敏: " + str(item["phone"]))


@case("BASE-04", "区域编码必须存在", "base", "P0")
def base_04():
    resp = sp.API.post("/api/live/farmers", {
        "farmerNo": sp.unique("NF"),
        "name": "区域错误",
        "countyCode": "999999",
        "phone": "13900000011",
    }, token=sp.token("live"))
    expect_error(resp, 400, "BAD_REQUEST")


@case("BASE-05", "修改农户信息必须填写原因", "base", "P0")
def base_05():
    body = {
        "farmerNo": _ctx["farmer_no"],
        "name": "自动化农户改",
        "countyCode": "340422",
        "phone": "13900000010",
    }
    without_reason = sp.API.put("/api/live/farmers/{}".format(_ctx["farmer_id"]),
                                body, token=sp.token("live"))
    expect_error(without_reason, 400, "BAD_REQUEST")

    body["changeReason"] = "自动化用例：修改姓名"
    ok = sp.API.put("/api/live/farmers/{}".format(_ctx["farmer_id"]),
                    body, token=sp.token("live"))
    expect_ok(ok)
    expect_eq(ok.data["name"], "自动化农户改", "修改后的姓名")


@case("BASE-06", "资质上传只允许 pdf/jpg/jpeg/png/docx", "base", "P0")
def base_06():
    resp = sp.API.upload(
        "/api/live/farmers/{}/qualifications".format(_ctx["farmer_id"]),
        sp.token("live"), "file", "virus.exe", b"MZ fake exe",
        "application/octet-stream")
    expect_error(resp, 400, "BAD_REQUEST")


@case("BASE-07", "资质上传并审核通过后农户变为已认证", "base", "P0")
def base_07():
    upload = sp.API.upload(
        "/api/live/farmers/{}/qualifications".format(_ctx["farmer_id"]),
        sp.token("live"), "file", "license.pdf", PDF_BYTES, "application/pdf")
    expect_ok(upload)
    qualification_id = upload.data["id"]

    review = sp.API.post(
        "/api/live/qualifications/{}/review".format(qualification_id),
        {"result": "approve", "comment": "材料齐全，通过"}, token=sp.token("live"))
    expect_ok(review)

    farmer = _find_farmer(_ctx["farmer_no"])
    expect_eq(farmer["qualificationStatus"], "verified", "资质状态")


@case("BASE-08", "新建场地与设备", "base", "P0")
def base_08():
    venue_no = sp.unique("C9")
    venue = sp.API.post("/api/live/venues", {
        "venueNo": venue_no,
        "name": "自动化测试场地",
        "location": "测试位置",
        "capacity": 15,
        "openStart": "09:00",
        "openEnd": "18:00",
        "countyCode": "340422",
    }, token=sp.token("live"))
    expect_ok(venue)
    _ctx["venue_id"] = venue.data["id"]
    _ctx["venue_no"] = venue_no

    device_no = sp.unique("ZBT")
    device = sp.API.post("/api/live/devices", {
        "deviceNo": device_no,
        "name": "自动化测试设备",
        "model": "手机直播组合",
        "config": "手机 + 三脚架 + 补光灯",
        "venueId": _ctx["venue_id"],
        "status": "available",
    }, token=sp.token("live"))
    expect_ok(device)
    expect_eq(device.data["venueId"], _ctx["venue_id"], "设备归属场地")
    _ctx["device_no"] = device_no


@case("BASE-09", "场地下仍有设备时不能删除", "base", "P0")
def base_09():
    resp = sp.API.delete("/api/live/venues/{}".format(_ctx["venue_id"]),
                         token=sp.token("live"))
    expect_status(resp, 409)
    expect("设备" in resp.message, "提示语应说明仍有设备: " + resp.message)


@case("BASE-10", "设备列表可按编号检索且归属正确", "base", "P1")
def base_10():
    resp = sp.API.get("/api/live/devices", token=sp.token("live"),
                      params={"keyword": _ctx["device_no"]})
    expect_ok(resp)
    items = resp.data["items"]
    expect(items, "未检索到刚建的设备")
    expect_eq(items[0]["deviceNo"], _ctx["device_no"], "设备编号")
    expect_eq(items[0]["venueId"], _ctx["venue_id"], "设备所属场地")
