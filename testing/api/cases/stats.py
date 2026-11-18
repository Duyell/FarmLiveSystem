"""统计与导出（STAT）用例。"""
import support as sp
from harness import (case, expect, expect_contains, expect_error, expect_ok,
                     expect_status)


@case("STAT-01", "四项指标可正常返回", "stats", "P0")
def stat_01():
    resp = sp.API.get("/api/stats/overview", token=sp.token("live"))
    expect_ok(resp)
    data = resp.data
    for field in ("occupancyRate", "liveCount", "soldQuantity", "successRate"):
        expect(field in data, "缺少指标字段 " + field)
    expect(0 <= data["occupancyRate"] <= 100, "占用率应落在 0-100")
    expect(0 <= data["successRate"] <= 100, "成功率应落在 0-100")


@case("STAT-02", "直播管理员可查看场次明细", "stats", "P0")
def stat_02():
    resp = sp.API.get("/api/stats/details", token=sp.token("live"),
                      params={"size": 5})
    expect_ok(resp)
    expect("items" in resp.data, "明细缺少 items 字段")
    expect(isinstance(resp.data["total"], int), "明细 total 应为整数")


@case("STAT-03", "统计员不能查看农户明细", "stats", "P0")
def stat_03():
    resp = sp.API.get("/api/stats/details", token=sp.token("viewer"))
    expect_error(resp, 403, "FORBIDDEN")

    export = sp.API.get("/api/stats/export", token=sp.token("viewer"))
    expect_error(export, 403, "FORBIDDEN")


@case("STAT-04", "聚合 CSV 全角色可导出且带 BOM", "stats", "P0")
def stat_04():
    resp = sp.API.get("/api/stats/export/aggregate-csv", token=sp.token("viewer"))
    expect_status(resp, 200)
    expect(resp.raw.startswith(b"\xef\xbb\xbf"), "CSV 缺少 UTF-8 BOM，Excel 会乱码")
    expect_contains(resp.raw.decode("utf-8-sig", "replace"), "场地占用率", "CSV 表头")


@case("STAT-05", "Excel 与 PDF 报表可下载", "stats", "P1")
def stat_05():
    excel = sp.API.get("/api/stats/export/excel", token=sp.token("admin"))
    expect_status(excel, 200)
    expect_contains(excel.header("content-type"), "spreadsheet", "Excel 类型")
    expect(len(excel.raw) > 1000, "Excel 内容过小")

    pdf = sp.API.get("/api/stats/export/pdf", token=sp.token("admin"))
    expect_status(pdf, 200)
    expect_contains(pdf.header("content-type"), "pdf", "PDF 类型")
    expect(pdf.raw.startswith(b"%PDF"), "PDF 文件头不正确")


@case("STAT-06", "县域筛选生效", "stats", "P1")
def stat_06():
    resp = sp.API.get("/api/stats/overview", token=sp.token("live"),
                      params={"countyCode": "999999"})
    expect_ok(resp)
    expect(resp.data["bookingTotal"] == 0,
           "不存在的县域不应有预约，实际 {}".format(resp.data["bookingTotal"]))
