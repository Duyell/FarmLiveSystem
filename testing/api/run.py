"""测试执行器：按模块顺序跑用例，输出控制台结果与 Markdown 报告。

用法：
    python setup_env.py                     # 首次或需要重置环境时
    python run.py                           # 跑全部模块
    python run.py --module booking          # 只跑预约
    python run.py --module auth,queue       # 跑多个
    python run.py --id AUTH                 # 按编号前缀过滤
"""
import argparse
import importlib
import sys
import time
import traceback
from datetime import datetime

import cfg
import harness
import support  # noqa: F401  确保能加载状态文件
from harness import CASES, Client, SkipTest

MODULES = ["auth", "perm", "base", "booking", "adjust", "queue",
           "notify", "stats", "fault", "audit", "ai", "ui"]

MODULE_NAMES = {
    "auth": "登录与账号安全",
    "perm": "权限与越权",
    "base": "基础信息",
    "booking": "直播预约",
    "adjust": "预约调整",
    "queue": "排队与候补",
    "notify": "通知",
    "stats": "统计与导出",
    "fault": "设备故障",
    "audit": "操作日志",
    "ai": "AI 知识库与问答",
    "ui": "前端可用性",
}


def load_cases():
    for module in MODULES:
        importlib.import_module("cases." + module)


def run_one(item, client):
    harness.API = client
    started = time.time()
    try:
        item["fn"]()
        return "PASS", "", time.time() - started
    except SkipTest as ex:
        return "SKIP", str(ex), time.time() - started
    except AssertionError as ex:
        return "FAIL", str(ex), time.time() - started
    except Exception as ex:  # noqa: BLE001
        detail = "{}: {}".format(type(ex).__name__, ex)
        return "ERROR", detail, time.time() - started


def select_cases(module_filter, id_filter):
    selected = []
    for item in CASES:
        if module_filter and item["module"] not in module_filter:
            continue
        if id_filter and not item["id"].startswith(id_filter):
            continue
        selected.append(item)
    order = {name: index for index, name in enumerate(MODULES)}
    selected.sort(key=lambda x: (order.get(x["module"], 99), x["id"]))
    return selected


def write_report(results, started_at, filtered_module):
    cfg.REPORT_DIR.mkdir(exist_ok=True)
    stamp = started_at.strftime("%Y%m%d-%H%M%S")
    path = cfg.REPORT_DIR / ("report-" + stamp + ".md")

    total = len(results)
    failed = [r for r in results if r["status"] in ("FAIL", "ERROR")]
    skipped = [r for r in results if r["status"] == "SKIP"]
    passed = [r for r in results if r["status"] == "PASS"]

    lines = []
    lines.append("# 功能测试执行报告")
    lines.append("")
    lines.append("| 项 | 值 |")
    lines.append("|---|---|")
    lines.append("| 执行时间 | {} |".format(started_at.strftime("%Y-%m-%d %H:%M:%S")))
    lines.append("| 服务地址 | {} |".format(cfg.BASE_URL))
    lines.append("| 数据库 | {} / {} |".format(cfg.DB["host"], cfg.DB["name"]))
    lines.append("| 范围 | {} |".format(filtered_module or "全部模块"))
    lines.append("| 用例总数 | {} |".format(total))
    lines.append("| 通过 | {} |".format(len(passed)))
    lines.append("| 失败 | {} |".format(len(failed)))
    lines.append("| 跳过 | {} |".format(len(skipped)))
    lines.append("| 结论 | {} |".format("通过" if not failed else "不通过"))
    lines.append("")
    lines.append("## 分模块汇总")
    lines.append("")
    lines.append("| 模块 | 用例数 | 通过 | 失败 | 跳过 |")
    lines.append("|---|---|---|---|---|")
    for module in MODULES:
        rows = [r for r in results if r["module"] == module]
        if not rows:
            continue
        lines.append("| {} {} | {} | {} | {} | {} |".format(
            module, MODULE_NAMES.get(module, ""), len(rows),
            sum(1 for r in rows if r["status"] == "PASS"),
            sum(1 for r in rows if r["status"] in ("FAIL", "ERROR")),
            sum(1 for r in rows if r["status"] == "SKIP")))
    lines.append("")
    lines.append("## 用例明细")
    lines.append("")
    lines.append("| 编号 | 用例 | 优先级 | 结果 | 耗时 | 说明 |")
    lines.append("|---|---|---|---|---|---|")
    for item in results:
        mark = {"PASS": "通过", "FAIL": "失败", "ERROR": "异常", "SKIP": "跳过"}[item["status"]]
        note = (item["message"] or "").replace("|", "\\|").replace("\n", " ")
        lines.append("| {} | {} | {} | {} | {:.2f}s | {} |".format(
            item["id"], item["title"], item["priority"], mark,
            item["elapsed"], note[:200]))
    lines.append("")
    if failed:
        lines.append("## 失败明细")
        lines.append("")
        for item in failed:
            lines.append("### {} {}".format(item["id"], item["title"]))
            lines.append("")
            lines.append("```")
            lines.append(item["message"])
            lines.append("```")
            lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")
    return path, len(passed), len(failed), len(skipped)


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    parser = argparse.ArgumentParser()
    parser.add_argument("--module", default="", help="模块名，逗号分隔：booking,queue")
    parser.add_argument("--id", default="", help="用例编号前缀：AUTH、BOOK-0")
    parser.add_argument("--keep", action="store_true",
                        help="沿用当前数据，不重建测试环境（默认每轮重建）")
    args = parser.parse_args()

    if not args.keep:
        import setup_env
        code = setup_env.main()
        if code:
            return code
        print()

    module_filter = [m.strip() for m in args.module.split(",") if m.strip()]
    load_cases()
    selected = select_cases(module_filter, args.id)
    if not selected:
        print("没有匹配的用例")
        return 1

    print("=" * 78)
    print("助农直播管理系统 · 接口测试")
    print("服务 {}   共 {} 条用例".format(cfg.BASE_URL, len(selected)))
    print("=" * 78)

    started_at = datetime.now()
    client = Client()
    results = []
    current_module = None

    for item in selected:
        if item["module"] != current_module:
            current_module = item["module"]
            print("\n-- {} {} --".format(
                current_module.upper(), MODULE_NAMES.get(current_module, "")))
        status, message, elapsed = run_one(item, client)
        results.append({
            "id": item["id"], "title": item["title"], "module": item["module"],
            "priority": item["priority"], "status": status,
            "message": message, "elapsed": elapsed,
        })
        label = {"PASS": "PASS", "FAIL": "FAIL", "ERROR": "ERR ", "SKIP": "SKIP"}[status]
        line = "  [{}] {} {}".format(label, item["id"], item["title"])
        if status != "PASS" and message:
            line += "  <- " + message.splitlines()[0][:120]
        print(line)

    report_path, passed, failed, skipped = write_report(
        results, started_at, args.module or args.id)

    print("\n" + "=" * 78)
    print("结果：通过 {} / 失败 {} / 跳过 {}，共 {}".format(
        passed, failed, skipped, len(results)))
    print("报告：" + str(report_path))
    print("=" * 78)

    if failed:
        print("\n失败用例：")
        for item in results:
            if item["status"] in ("FAIL", "ERROR"):
                print("  - {} {}: {}".format(
                    item["id"], item["title"], item["message"].splitlines()[0]))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
