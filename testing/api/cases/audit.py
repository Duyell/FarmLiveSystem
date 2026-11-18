"""操作日志（LOG）用例。"""
import support as sp
from harness import (case, expect, expect_eq, expect_ok, expect_status)


@case("LOG-01", "关键操作写入审计日志", "audit", "P0")
def log_01():
    resp = sp.API.get("/api/admin/logs", token=sp.token("admin"),
                      params={"action": "FARMER_CREATE", "size": 20})
    expect_ok(resp)
    expect(resp.data["total"] >= 1, "未查询到农户创建日志")


@case("LOG-02", "日志可按条件查询并导出 CSV", "audit", "P1")
def log_02():
    resp = sp.API.get("/api/admin/logs/export", token=sp.token("admin"),
                      params={"action": "FARMER_CREATE"})
    expect_status(resp, 200)
    expect(resp.raw.startswith(b"\xef\xbb\xbf"), "导出缺少 UTF-8 BOM")
    text = resp.raw.decode("utf-8-sig", "replace")
    expect("FARMER_CREATE" in text, "导出内容不含目标操作")


@case("LOG-03", "日志不提供修改删除入口", "audit", "P0")
def log_03():
    before = int(sp.db.scalar("SELECT COUNT(*) FROM audit_log"))
    resp = sp.API.delete("/api/admin/logs/1", token=sp.token("admin"))
    expect(resp.status >= 400, "删除日志接口不应成功，实际 " + resp.describe())
    after = int(sp.db.scalar("SELECT COUNT(*) FROM audit_log"))
    expect_eq(after, before, "日志条数不应变化")


@case("LOG-04", "日志防篡改校验通过", "audit", "P0")
def log_04():
    resp = sp.API.get("/api/admin/logs/verify", token=sp.token("admin"))
    expect_ok(resp)
    expect_eq(resp.data["tamperedIds"], [], "被篡改的日志编号")
    expect(resp.data["intact"] == resp.data["total"], "完整条数应等于总条数")
