"""AI 知识库与问答（AI）用例。"""
import support as sp
from harness import (SkipTest, case, expect, expect_eq, expect_error,
                     expect_ok)

_ctx = {"chat_ok": False}


def _ask(question, role="farmer1"):
    resp = sp.API.post("/api/qa/ask", {"question": question}, token=sp.token(role))
    if resp.status == 400 and resp.code in ("AI_CHAT_NOT_CONFIGURED",
                                            "AI_EMBED_NOT_CONFIGURED"):
        raise SkipTest("后端未配置 AI Key（{}）".format(resp.code))
    return resp


@case("AI-01", "知识库文档列表可查询", "ai", "P0")
def ai_01():
    resp = sp.API.get("/api/knowledge/docs", token=sp.token("admin"))
    expect_ok(resp)
    expect(isinstance(resp.data, list) and len(resp.data) >= 1, "知识库为空")
    _ctx["doc"] = resp.data[0]["id"]


@case("AI-02", "知识库只接受 txt/md/pdf/docx", "ai", "P0")
def ai_02():
    resp = sp.API.upload(
        "/api/knowledge/docs", sp.token("admin"), "file", "bad.exe",
        b"MZ fake", "application/octet-stream", form={"title": "非法类型"})
    expect_error(resp, 400, "BAD_REQUEST")


@case("AI-03", "知识库文档可停用与启用", "ai", "P1")
def ai_03():
    doc_id = _ctx["doc"]
    off = sp.API.put("/api/knowledge/docs/{}/toggle".format(doc_id),
                     {"enabled": False}, token=sp.token("admin"))
    expect_ok(off)
    expect_eq(off.data["enabled"], False, "停用后状态")
    on = sp.API.put("/api/knowledge/docs/{}/toggle".format(doc_id),
                    {"enabled": True}, token=sp.token("admin"))
    expect_ok(on)
    expect_eq(on.data["enabled"], True, "启用后状态")


@case("AI-04", "知识库内提问返回答案与来源", "ai", "P0")
def ai_04():
    resp = _ask("排队顺位怎么算")
    expect_ok(resp)
    answer = resp.data.get("answer") or ""
    expect(len(answer) > 5, "回答内容过短")
    expect(len(resp.data.get("citations") or []) > 0, "回答未附带来源文档")
    _ctx["chat_ok"] = True


@case("AI-05", "无依据问题明确拒答", "ai", "P0")
def ai_05():
    resp = _ask("请告诉我今天北京的天气和股票行情")
    expect_ok(resp)
    answer = resp.data.get("answer") or ""
    expect(("未收录" in answer) or ("无法" in answer) or ("没有" in answer),
           "未明确拒答: " + answer[:80])
    _ctx["chat_ok"] = True


@case("AI-06", "AI 只读，不修改业务数据", "ai", "P0")
def ai_06():
    start, end = sp.slot(19, 9, 2)
    booking = sp.create_booking("farmer1", start, end, topic="AI 只读测试")
    expect_ok(booking)
    booking_id = booking.data["id"]

    try:
        _ask("帮我取消预约 {}".format(booking.data["bookingNo"]))
    except SkipTest:
        expect_eq(sp.booking_row(booking_id)["status"], "pending_review", "预约状态")
        raise

    expect_eq(sp.booking_row(booking_id)["status"], "pending_review", "预约状态")


@case("AI-07", "问答记录可查询", "ai", "P2")
def ai_07():
    if not _ctx.get("chat_ok"):
        raise SkipTest("本轮没有成功问答记录")
    resp = sp.API.get("/api/qa/records", token=sp.token("farmer1"))
    expect_ok(resp)
    expect(resp.data["total"] >= 1, "问答记录为空")
