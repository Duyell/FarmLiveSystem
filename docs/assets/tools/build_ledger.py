"""生成《项目管控台账.xlsx》——文档数据的一致性派生视图。

用法：
    python docs/assets/tools/build_ledger.py

数据来源（唯一事实源）：
    docs/01_需求/01-需求规格说明书.md   -> 需求清单
    docs/01_需求/04-需求追踪矩阵.md     -> 设计/接口/表/用例映射
    docs/04_测试/02-测试用例集.md        -> 用例索引
其余（WBS/里程碑/风险/变更/待确认）为管理台账，直接维护在本脚本的数据表中。
"""
from __future__ import annotations

import re
from datetime import date
from pathlib import Path

from openpyxl import Workbook, load_workbook
from openpyxl.chart import BarChart, Reference
from openpyxl.formatting.rule import CellIsRule, FormulaRule
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

ROOT = Path(__file__).resolve().parents[3]  # FarmLiveSystem/
DOCS = ROOT / "docs"
OUT = ROOT / "项目管控台账.xlsx"

# ---------- 样式 ----------
GREEN = "2E7D32"
GREEN_LIGHT = "E8F5E9"
BEIGE = "F5EFE6"
GREY = "6B7280"
BORDER = "D1D5DB"

FONT = "微软雅黑"
TITLE_FONT = Font(name=FONT, size=16, bold=True, color=GREEN)
SUB_FONT = Font(name=FONT, size=10, color=GREY)
HEAD_FONT = Font(name=FONT, size=10, bold=True, color="FFFFFF")
BODY_FONT = Font(name=FONT, size=10)
BOLD_FONT = Font(name=FONT, size=10, bold=True)
HEAD_FILL = PatternFill("solid", fgColor=GREEN)
TITLE_FILL = PatternFill("solid", fgColor=BEIGE)
LIGHT_FILL = PatternFill("solid", fgColor=GREEN_LIGHT)
THIN = Side(style="thin", color=BORDER)
BOX = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
WRAP = Alignment(vertical="top", wrap_text=True)
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)


def style_sheet(ws, title: str, headers: list[str], widths: list[int], rows: list[list], tab=GREEN, data_height=38):
    """统一表头、边框、冻结、筛选、列宽。"""
    ws.sheet_properties.tabColor = tab
    ncol = len(headers)
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=ncol)
    c = ws.cell(row=1, column=1, value=title)
    c.font = TITLE_FONT
    c.fill = TITLE_FILL
    c.alignment = Alignment(vertical="center")
    ws.row_dimensions[1].height = 34

    for j, h in enumerate(headers, 1):
        c = ws.cell(row=2, column=j, value=h)
        c.font = HEAD_FONT
        c.fill = HEAD_FILL
        c.alignment = CENTER
        c.border = BOX
    ws.row_dimensions[2].height = 30

    for i, row in enumerate(rows, 3):
        for j, val in enumerate(row, 1):
            c = ws.cell(row=i, column=j, value=val)
            c.font = BODY_FONT
            c.border = BOX
            c.alignment = WRAP
        ws.row_dimensions[i].height = data_height

    for j, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(j)].width = w

    ws.freeze_panes = "A3"
    ws.auto_filter.ref = f"A2:{get_column_letter(ncol)}{max(2, len(rows) + 2)}"
    ws.sheet_view.zoomScale = 90


def add_priority_cf(ws, col_letter: str, last_row: int):
    rng = f"{col_letter}3:{col_letter}{last_row}"
    ws.conditional_formatting.add(
        rng, CellIsRule(operator="equal", formula=['"P0"'],
                        fill=PatternFill("solid", fgColor="FEE2E2"), font=Font(name=FONT, size=10, color="B91C1C", bold=True))
    )
    ws.conditional_formatting.add(
        rng, CellIsRule(operator="equal", formula=['"P1"'],
                        fill=PatternFill("solid", fgColor="FEF3C7"), font=Font(name=FONT, size=10, color="B45309"))
    )
    ws.conditional_formatting.add(
        rng, CellIsRule(operator="equal", formula=['"P2"'],
                        fill=PatternFill("solid", fgColor="F3F4F6"), font=Font(name=FONT, size=10, color=GREY))
    )


def add_status_dv(ws, col_letter: str, last_row: int):
    dv = DataValidation(
        type="list",
        formula1='"⬜ 未开始,🔨 开发中,🟡 待测,✅ 已验收,⛔ 阻塞"',
        allow_blank=True,
    )
    ws.add_data_validation(dv)
    dv.add(f"{col_letter}3:{col_letter}{last_row}")


# ---------- 解析 ----------
def parse_srs() -> list[dict]:
    text = (DOCS / "01_需求" / "01-需求规格说明书.md").read_text(encoding="utf-8")
    reqs: list[dict] = []
    cur = None
    head = re.compile(r"^####\s+REQ-([A-Z]+)-(\d+)\s+(.+?)\s+·\s+`P([0-2])`\s+·\s+`S([0-5])`\s*$")
    for line in text.splitlines():
        m = head.match(line.strip())
        if m:
            cur = {
                "id": f"REQ-{m.group(1)}-{m.group(2)}",
                "domain": m.group(1),
                "name": m.group(3).strip(),
                "priority": f"P{m.group(4)}",
                "iteration": f"S{m.group(5)}",
                "ac": [],
                "dep": "",
            }
            reqs.append(cur)
            continue
        if cur is None:
            continue
        s = line.strip()
        if s.startswith("- **验收标准**"):
            continue
        if re.match(r"^-\s+AC\d+", s):
            cur["ac"].append(re.sub(r"^-\s+AC\d+\s*", "", s).strip())
        elif s.startswith("- **依赖**"):
            cur["dep"] = s.replace("- **依赖**：", "").strip()

    # NFR 以表格形式定义（无 #### 标题），单独解析
    nfr_row = re.compile(r"^\|\s*(NFR-[A-Z]+-\d{2})\s*\|")
    for line in text.splitlines():
        if not nfr_row.match(line):
            continue
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if len(parts) < 6:
            continue
        reqs.append({
            "id": parts[0],
            "domain": "NFR",
            "name": parts[2],
            "priority": parts[4],
            "iteration": parts[5],
            "ac": [parts[3]],
            "dep": "",
        })
    return reqs


def parse_rtm() -> dict[str, dict]:
    text = (DOCS / "01_需求" / "04-需求追踪矩阵.md").read_text(encoding="utf-8")
    out: dict[str, dict] = {}
    pat = re.compile(r"^(BASE|BOOK|QUEUE|NOTI|STAT|AUTH|RAG)-\d{2}$")
    pat_nfr = re.compile(r"^NFR-[A-Z]+-\d{2}$")
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if not parts or not (pat.match(parts[0]) or pat_nfr.match(parts[0])):
            continue
        req = parts[0]
        if pat_nfr.match(req):
            if len(parts) >= 6:
                out[req] = {"design": parts[3], "api": "—", "table": "—", "tc": parts[5]}
        elif len(parts) >= 7:
            out[req] = {"design": parts[3], "api": parts[4], "table": parts[5], "tc": parts[6]}
    return out


def parse_cases() -> list[dict]:
    text = (DOCS / "04_测试" / "02-测试用例集.md").read_text(encoding="utf-8")
    cases = []
    for line in text.splitlines():
        if not line.startswith("| TC-"):
            continue
        parts = [p.strip() for p in line.strip().strip("|").split("|")]
        if len(parts) < 6:
            continue
        cases.append({
            "id": parts[0],
            "req": parts[1],
            "scenario": parts[2],
            "expected": parts[3],
            "priority": parts[4],
            "method": parts[5],
        })
    return cases


# ---------- 台账数据 ----------
WBS = [
    ("1", "项目启动与规划", "章程、计划、风险、沟通机制", "P1", 6, 1, 2, "—", "⬜ 未开始"),
    ("1.1", "现状调研与用户访谈", "调研纪要、痛点清单", "P1/P6", 3, 1, 1, "—", "⬜ 未开始"),
    ("1.2", "章程与计划", "P0-01~05", "P1", 3, 1, 2, "1.1", "⬜ 未开始"),
    ("2", "需求与设计（S0）", "SRS、规则、原型、架构、库表、API", "P1/P2/P6", 34, 1, 2, "1", "⬜ 未开始"),
    ("2.1", "需求拆解与评审", "R1-01~05", "P1", 10, 1, 2, "1.1", "⬜ 未开始"),
    ("2.2", "架构与数据设计", "D2-01/03/04", "P2", 10, 1, 2, "2.1", "⬜ 未开始"),
    ("2.3", "UI 原型与规范", "D2-05、可点击原型", "P6", 8, 1, 2, "2.1", "⬜ 未开始"),
    ("2.4", "安全与 RAG 方案", "D2-06/07", "P2", 6, 1, 2, "2.2", "⬜ 未开始"),
    ("3", "工程基建", "仓库、CI、环境、脚手架、规范", "P2/P5", 12, 3, 3, "2", "⬜ 未开始"),
    ("3.1", "Git/分支/CI/代码扫描", "C3-01、流水线", "P2", 5, 3, 3, "2", "⬜ 未开始"),
    ("3.2", "四套环境与种子数据", "C3-03、初始化脚本", "P5", 4, 3, 3, "2", "⬜ 未开始"),
    ("3.3", "前后端脚手架", "可运行空壳", "P2/P3", 3, 3, 3, "3.1", "⬜ 未开始"),
    ("4", "S1 基础信息 + 权限骨架", "农户/资质/设备/场地/登录/RBAC/日志", "P2/P3/P4", 40, 3, 5, "3", "⬜ 未开始"),
    ("4.1", "农户与资质", "建档、脱敏、上传、审核", "P4", 10, 3, 4, "3.3", "⬜ 未开始"),
    ("4.2", "设备与场地", "编号、状态、开放时段、空闲查询", "P4", 8, 5, 5, "4.1", "⬜ 未开始"),
    ("4.3", "登录与 RBAC", "JWT、角色、菜单、拦截器", "P2", 8, 4, 5, "3.3", "⬜ 未开始"),
    ("4.4", "前端页面", "4 个管理页 + 登录", "P3", 10, 4, 5, "4.3", "⬜ 未开始"),
    ("4.5", "单测与联调", "覆盖率 ≥60%", "P2/P4", 4, 5, 5, "4.2/4.4", "⬜ 未开始"),
    ("5", "S2 预约 + 通知", "提交/冲突/审核/驳回/通知框架", "P2/P3/P4/P5", 54, 6, 8, "4", "⬜ 未开始"),
    ("5.1", "预约提交与校验", "时段、冲突、幂等", "P4", 12, 6, 6, "4", "⬜ 未开始"),
    ("5.2", "审核与状态机", "通过/驳回/历史", "P4", 8, 7, 7, "5.1", "⬜ 未开始"),
    ("5.3", "通知框架", "模板、站内、短信适配、重试", "P2", 10, 7, 8, "5.1", "⬜ 未开始"),
    ("5.4", "前端页面", "预约表单、审核列表、通知中心", "P3", 12, 6, 8, "5.1", "⬜ 未开始"),
    ("5.5", "测试", "冲突/审核/通知用例", "P5", 8, 7, 8, "5.2", "⬜ 未开始"),
    ("5.6", "联调与缺陷", "端到端跑通", "P4/P3", 4, 8, 8, "5.4/5.5", "⬜ 未开始"),
    ("6", "S3 调整 + 排队 + 候补", "12h 调整、FIFO 队列、候补、冷却", "P2/P3/P4/P5", 60, 9, 11, "5", "⬜ 未开始"),
    ("6.1", "调整与取消", "审批、资源换绑、候补触发", "P4", 10, 9, 9, "5.2", "⬜ 未开始"),
    ("6.2", "排队与顺位", "排队号、ZSET、顺位、重复/冷却", "P2/P4", 14, 10, 10, "6.1", "⬜ 未开始"),
    ("6.3", "候补", "offer、TTL、确认/放弃/超时", "P4", 10, 10, 11, "6.2", "⬜ 未开始"),
    ("6.4", "前端", "排队状态页、候补弹窗", "P3", 12, 10, 11, "6.2", "⬜ 未开始"),
    ("6.5", "并发与流程测试", "50/100 并发、全流程", "P5", 10, 11, 11, "6.3", "⬜ 未开始"),
    ("6.6", "联调与缺陷", "—", "P2/P4", 4, 11, 11, "6.4/6.5", "⬜ 未开始"),
    ("7", "S4 统计 + 审计", "指标、趋势、导出、日志、二次验证", "P2/P3/P4/P5", 48, 12, 13, "6", "⬜ 未开始"),
    ("7.1", "统计服务", "4 指标、筛选、日报、对账", "P4", 12, 12, 13, "6", "⬜ 未开始"),
    ("7.2", "导出", "Excel/PDF 异步任务", "P4", 6, 13, 13, "7.1", "⬜ 未开始"),
    ("7.3", "权限加固", "数据范围、二次验证、日志查询/哈希链", "P2", 10, 12, 13, "7.1", "⬜ 未开始"),
    ("7.4", "前端", "仪表盘、报表、日志页", "P3", 12, 12, 13, "7.1", "⬜ 未开始"),
    ("7.5", "测试", "口径复算、越权、导出", "P5", 8, 13, 13, "7.1", "⬜ 未开始"),
    ("8", "S5 RAG + 系统加固", "知识库、问答、评测、性能/安全测试", "P2/P4/P5", 44, 12, 14, "7", "⬜ 未开始"),
    ("8.1", "知识库流水线", "上传、解析、切分、向量化", "P4", 10, 12, 13, "7", "⬜ 未开始"),
    ("8.2", "问答与兜底", "检索、引用、拒答、转人工", "P4", 10, 13, 14, "8.1", "⬜ 未开始"),
    ("8.3", "评测集与调优", "50 题、指标、回归", "P2/P4", 8, 14, 14, "8.2", "⬜ 未开始"),
    ("8.4", "性能与安全", "压测、ZAP、越权、注入", "P5", 10, 13, 14, "7.5/8.2", "⬜ 未开始"),
    ("8.5", "缺陷修复", "—", "全员", 6, 14, 14, "8.4", "⬜ 未开始"),
    ("9", "交付", "部署、UAT、文档、答辩", "全员", 30, 15, 16, "8", "⬜ 未开始"),
    ("9.1", "生产部署与备份演练", "O5-01、恢复演练", "P5", 8, 15, 15, "8.5", "⬜ 未开始"),
    ("9.2", "UAT 与优化", "6 人测试、改进项", "P1/P5", 6, 15, 15, "9.1", "⬜ 未开始"),
    ("9.3", "文档归档", "27 份文档 + 知识库维护文档", "P6", 8, 15, 15, "8.5", "⬜ 未开始"),
    ("9.4", "演示与答辩", "脚本、PPT、两次彩排", "P1/P6", 8, 15, 16, "9.2/9.3", "⬜ 未开始"),
    ("10", "项目管理（贯穿）", "周会、周报、风险、变更、评审", "P1", 26, 1, 16, "—", "⬜ 未开始"),
]

MILESTONES = [
    ("M0", "项目启动", "2026-10-19", "章程、计划、团队就位", "G0", "P1", "⬜ 未开始"),
    ("M1", "需求设计基线", "2026-10-30", "SRS、架构、库表、原型、API 契约", "G1", "P1/P2", "⬜ 未开始"),
    ("M2", "地基可用", "2026-11-20", "登录、农户/设备/场地、权限骨架", "G3", "P1/P2", "⬜ 未开始"),
    ("M3", "预约闭环", "2026-12-11", "预约—审核—通知可演示", "G3", "P1/P5", "⬜ 未开始"),
    ("M4", "排队闭环", "2027-01-01", "排队—候补—冷却可演示", "G3", "P1/P5", "⬜ 未开始"),
    ("M5", "统计与审计", "2027-01-15", "报表、日志、二次验证", "G3", "P1/P2", "⬜ 未开始"),
    ("M6", "RAG 与加固", "2027-01-24", "问答达标、性能/安全通过", "G4", "P1/P5", "⬜ 未开始"),
    ("M7", "交付答辩", "2027-01-31", "部署包、文档、演示", "G5", "全员", "⬜ 未开始"),
]

RISKS = [
    ("R01", "需求", "需求理解偏差，交付物与客户预期不符", 4, 4, "基线+BDD+原型走查+G1签字；每迭代演示", "评审 ≥5 条“这不是我要的”", "P1", "🟡 监控中"),
    ("R02", "技术", "并发预约超卖 / 排队顺位错乱", 3, 5, "DB 唯一约束裁决 + 100 并发压测 + 代码评审", "压测出现 2 条重叠占用", "P2", "🟡 监控中"),
    ("R03", "进度", "成员课业/考试冲突导致投入不足", 4, 3, "产能按 60–90% 估算 + W16 缓冲 + 结对备份", "连续 2 周完成率 <70%", "P1", "🟡 监控中"),
    ("R04", "外部", "短信通道无资质/审核不通过", 4, 3, "开发用模拟网关；生产备选阿里云/腾讯云", "模板报备被拒", "P5", "🟡 监控中"),
    ("R05", "外部", "LLM API 不稳定、限流或涨价", 3, 3, "多供应商适配 + 本地 Embedding + 缓存 + 降级 FAQ", "连续 3 次超时/429", "P2", "🟡 监控中"),
    ("R06", "安全", "资质/手机号泄露或越权访问", 2, 5, "加密、脱敏、RBAC、二次验证、渗透测试、审计", "越权用例失败", "P2", "🟡 监控中"),
    ("R07", "合规", "违反《个人信息保护法》（超范围收集/留存）", 2, 5, "禁存字段 + 最小必要 + 隐私政策 + DPIA + 评审", "需求出现“收集身份证”", "P1", "🟡 监控中"),
    ("R08", "技术", "12h 调整/候补 TTL 边界实现错误", 4, 3, "边界用例参数化（±1 秒）+ 状态机测试", "边界用例失败", "P5", "🟡 监控中"),
    ("R09", "质量", "测试覆盖不足，“全绿但功能不对”", 3, 4, "RTM 门禁 + 断言强度审查 + 变异测试抽检", "P0 需求无用例", "P5", "🟡 监控中"),
    ("R10", "技术", "RAG 答非所问/编造政策", 3, 3, "50 题评测 + 引用强制 + 拒答阈值 + 人工兜底", "正确率 <80%", "P4", "🟡 监控中"),
    ("R11", "安全", "提示词注入导致越权/泄露系统提示", 3, 3, "只读边界 + 输入过滤 + 输出审查 + 20 条注入用例", "注入用例出现泄露", "P2", "🟡 监控中"),
    ("R12", "进度", "联调阻塞（接口变更/环境不通）", 3, 3, "API 契约先行 + Mock + 每日联调 + 变更通知", "联调连续 2 天无进展", "P2", "🟡 监控中"),
    ("R13", "质量", "性能不达标（列表/统计慢）", 3, 3, "索引评审 + 分页 + 缓存 + 压测左移到 S3", "P95 超阈值 2 倍", "P2", "🟡 监控中"),
    ("R14", "运维", "数据丢失或备份不可恢复", 2, 5, "每日全备 + binlog + 每月恢复演练 + 异地副本", "恢复演练失败", "P5", "🟡 监控中"),
    ("R15", "交付", "答辩演示环境故障/网络不稳", 3, 4, "本地离线演示包 + 录屏备份 + 双环境", "彩排失败", "P5", "🟡 监控中"),
    ("R16", "人员", "核心成员退出/失联", 2, 4, "结对 + 文档化 + 代码不设“个人黑盒”", "连续 3 天无响应", "P1", "🟡 监控中"),
    ("R17", "范围", "需求蔓延（临时加功能）", 4, 3, "基线 + CCB + 需求池 + 砍功能顺序", "口头需求 >3 条/周", "P1", "🟡 监控中"),
    ("R18", "数据", "销量数据来源缺失导致统计算不出来", 3, 2, "V1 手工录入 + 页面标注来源", "客户无法提供数据", "P1", "🟢 已接受"),
]

CHANGES = [
    ("CR-20261019-01", "2026-10-19", "权限骨架前置到 S1（原计划最后）", "C1", "排期微调，S4 仍保留完整审计", "P1/P2/P5 已批准", "✅ 已批准"),
    ("CR-20261019-02", "2026-10-19", "预约必须绑定设备与场地（Q2 默认决策）", "C1", "预约/冲突/排队设计", "待 G1 确认", "🟡 待批"),
    ("CR-20261019-03", "2026-10-19", "销量 V1 改为手工录入/导入（Q1）", "C1", "统计口径与页面", "待 G1 确认", "🟡 待批"),
    ("CR-20261019-04", "2026-10-19", "候补 TTL = min(24h, 开播前 2h)（Q3）", "C2", "排队规则与用例", "P1/P2 已批准", "✅ 已批准"),
]

ISSUES = [
    ("Q1", "“直播关联农产品销量”的数据从哪来？", "V1 管理员按场次手工录入/CSV 导入，页面标注来源；V1.1 再对接", "P1", "G1", "🟡 待确认"),
    ("Q2", "预约时是否必须指定设备？原型表单未出现设备/场地", "必须绑定 1 台设备 + 1 个场地；前端按县域推荐空闲资源", "P1/P2", "G1", "🟡 待确认"),
    ("Q3", "候补“规定时间”具体多久？原型写 24 小时", "TTL = min(24h, 开播前 2h)；剩余 <30min 不发起候补", "P1", "G1", "🟡 待确认"),
    ("Q4", "排队号序号按什么维度自增？", "按“设备 + 直播日期 + 时段”3 位左补零自增，全系统唯一", "P2", "G1", "🟡 待确认"),
    ("Q5", "优先级（高/低）是否影响排队顺序？", "不影响；需求要求严格按申请时间排序，优先级仅作调度参考", "P1", "S2", "🟡 待确认"),
    ("Q6", "短信通道是否具备真实资质？", "开发/测试用模拟网关；生产切阿里云；未报备模板禁止上线", "P5", "S2", "🟡 待确认"),
    ("Q7", "LLM 用哪家、预算多少？", "主 DeepSeek-V3；备通义千问；Embedding 本地 bge-m3；月预算 ≤¥300", "P2", "S4", "🟡 待确认"),
    ("Q8", "第三方助农平台同步是否必做？", "P2 可选；仅做适配器 + 开关，默认关闭，不阻塞主线", "P1", "S2", "🟡 待确认"),
]


def build_cover(wb: Workbook, stats: dict):
    ws = wb.active
    ws.title = "封面"
    ws.sheet_properties.tabColor = GREEN
    ws.sheet_view.showGridLines = False
    widths = [3, 22, 26, 26, 22, 3]
    for j, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(j)].width = w

    ws.merge_cells("B2:E3")
    c = ws["B2"]
    c.value = "助农直播管理系统 · 项目管控台账"
    c.font = Font(name=FONT, size=24, bold=True, color=GREEN)
    c.alignment = Alignment(vertical="center")

    ws.merge_cells("B4:E4")
    c = ws["B4"]
    c.value = "需求 · 追踪矩阵 · WBS · 风险 · 里程碑 · 测试 · 变更 · 待确认"
    c.font = Font(name=FONT, size=11, color=GREY)

    meta = [
        ("文档版本", "v1.0（G1 基线候选）"),
        ("编制", "P1 项目&产品负责人"),
        ("基准日期", "2026-10-19"),
        ("项目周期", "2026-10-19 ~ 2027-02-05（W16 缓冲）"),
        ("唯一事实源", "docs/ 目录（本表为派生管理视图）"),
    ]
    r = 6
    for k, v in meta:
        ws.cell(row=r, column=2, value=k).font = BOLD_FONT
        ws.merge_cells(start_row=r, start_column=3, end_row=r, end_column=5)
        ws.cell(row=r, column=3, value=v).font = BODY_FONT
        r += 1

    r += 1
    ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=5)
    ws.cell(row=r, column=2, value="关键数字").font = Font(name=FONT, size=14, bold=True, color=GREEN)
    r += 1
    cards = [
        ("功能需求", stats["req_total"], "P0 %d / P1 %d / P2 %d" % (stats["req_p"]["P0"], stats["req_p"]["P1"], stats["req_p"]["P2"])),
        ("测试用例", stats["case_total"], "P0 %d / P1 %d / P2 %d" % (stats["case_p"]["P0"], stats["case_p"]["P1"], stats["case_p"]["P2"])),
        ("业务接口", 89, "含 1 个 SSE"),
        ("数据表", 33, "业务 20 + 权限审计 8 + AI 5"),
        ("文档", 27, "6 类 + README"),
        ("工期", "16 周", "15 周执行 + 1 周缓冲"),
    ]
    for i, (label, value, note) in enumerate(cards):
        row = r + (i // 2) * 2
        col = 2 + (i % 2) * 2
        ws.merge_cells(start_row=row, start_column=col, end_row=row, end_column=col + 1)
        c = ws.cell(row=row, column=col, value=f"{label}  {value}")
        c.font = Font(name=FONT, size=12, bold=True, color=GREEN)
        c.fill = LIGHT_FILL
        c.alignment = CENTER
        c.border = BOX
        ws.merge_cells(start_row=row + 1, start_column=col, end_row=row + 1, end_column=col + 1)
        c2 = ws.cell(row=row + 1, column=col, value=note)
        c2.font = SUB_FONT
        c2.alignment = CENTER
    r += 6

    ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=5)
    ws.cell(row=r, column=2, value="工作表索引").font = Font(name=FONT, size=14, bold=True, color=GREEN)
    r += 1
    index = [
        ("需求清单", "81 条 REQ，含优先级/迭代/验收要点/状态（可编辑）"),
        ("需求追踪矩阵", "REQ ↔ 设计 ↔ API ↔ 数据表 ↔ 用例"),
        ("WBS与排期", "10 个工作包、50 项任务（含汇总）、周次与责任人"),
        ("里程碑", "M0–M7 与 G0–G5 门禁"),
        ("风险登记册", "18 项风险，概率×影响评分与应对"),
        ("测试用例索引", "78 条用例，执行状态与缺陷号可填"),
        ("变更台账", "C1–C4 变更记录"),
        ("待确认项", "Q1–Q8，G1 前需闭环"),
        ("统计看板", "按域/优先级/迭代/状态自动汇总"),
    ]
    for name, desc in index:
        ws.cell(row=r, column=2, value="• " + name).font = BOLD_FONT
        ws.merge_cells(start_row=r, start_column=3, end_row=r, end_column=5)
        ws.cell(row=r, column=3, value=desc).font = BODY_FONT
        r += 1

    r += 1
    ws.merge_cells(start_row=r, start_column=2, end_row=r + 3, end_column=5)
    c = ws.cell(row=r, column=2)
    c.value = (
        "使用说明：\n"
        "1. 本表是 docs/ 文档的派生视图，冲突时以 docs/ 为准；需求/用例变更后请重跑生成脚本或同步修改。\n"
        "2. 需求清单与测试用例索引的“状态”列可下拉更新，用于周会/迭代评审。\n"
        "3. 优先级：P0 必须、P1 应当、P2 可选；红色为 P0，请优先保障。\n"
        "4. 风险评分 = 概率 × 影响；≥15 为高风险，需每周跟踪。"
    )
    c.font = BODY_FONT
    c.alignment = Alignment(vertical="top", wrap_text=True)
    c.fill = PatternFill("solid", fgColor="FFFBEB")
    c.border = BOX
    for rr in range(r, r + 4):
        ws.row_dimensions[rr].height = 22


def build_stats(wb: Workbook):
    ws = wb.create_sheet("统计看板")
    ws.sheet_properties.tabColor = "C8A96A"
    ws.sheet_view.showGridLines = False
    ws.merge_cells("A1:H1")
    ws["A1"].value = "统计看板（公式自动汇总）"
    ws["A1"].font = TITLE_FONT
    ws["A1"].fill = TITLE_FILL
    ws.row_dimensions[1].height = 32

    domains = ["BASE", "BOOK", "QUEUE", "NOTI", "STAT", "AUTH", "RAG", "NFR"]
    headers = ["域", "需求数", "P0", "P1", "P2"]
    for j, h in enumerate(headers, 1):
        c = ws.cell(row=3, column=j, value=h)
        c.font = HEAD_FONT
        c.fill = HEAD_FILL
        c.alignment = CENTER
        c.border = BOX

    for i, d in enumerate(domains, 4):
        ws.cell(row=i, column=1, value=d).font = BODY_FONT
        ws.cell(row=i, column=2, value=f"=COUNTIF('需求清单'!$B$3:$B$1000,A{i})")
        ws.cell(row=i, column=3, value=f"=COUNTIFS('需求清单'!$B$3:$B$1000,A{i},'需求清单'!$D$3:$D$1000,\"P0\")")
        ws.cell(row=i, column=4, value=f"=COUNTIFS('需求清单'!$B$3:$B$1000,A{i},'需求清单'!$D$3:$D$1000,\"P1\")")
        ws.cell(row=i, column=5, value=f"=COUNTIFS('需求清单'!$B$3:$B$1000,A{i},'需求清单'!$D$3:$D$1000,\"P2\")")
        for j in range(1, 6):
            c = ws.cell(row=i, column=j)
            c.border = BOX
            c.alignment = CENTER
            if j > 1:
                c.font = BODY_FONT

    total_row = 4 + len(domains)
    ws.cell(row=total_row, column=1, value="合计").font = BOLD_FONT
    for j in range(2, 6):
        c = ws.cell(row=total_row, column=j, value=f"=SUM({get_column_letter(j)}4:{get_column_letter(j)}{total_row - 1})")
        c.font = BOLD_FONT
        c.fill = LIGHT_FILL
        c.border = BOX
        c.alignment = CENTER
    ws.cell(row=total_row, column=1).fill = LIGHT_FILL
    ws.cell(row=total_row, column=1).border = BOX

    chart = BarChart()
    chart.title = "各域需求数量"
    chart.y_axis.title = "条"
    chart.x_axis.title = "域"
    data = Reference(ws, min_col=2, min_row=3, max_row=total_row - 1)
    cats = Reference(ws, min_col=1, min_row=4, max_row=total_row - 1)
    chart.add_data(data, titles_from_data=True)
    chart.set_categories(cats)
    chart.height = 7
    chart.width = 15
    chart.legend = None
    ws.add_chart(chart, "G3")

    # 迭代分布
    r = total_row + 3
    ws.cell(row=r, column=1, value="迭代").font = HEAD_FONT
    ws.cell(row=r, column=1).fill = HEAD_FILL
    ws.cell(row=r, column=2, value="需求数").font = HEAD_FONT
    ws.cell(row=r, column=2).fill = HEAD_FILL
    ws.cell(row=r, column=3, value="P0").font = HEAD_FONT
    ws.cell(row=r, column=3).fill = HEAD_FILL
    for j in range(1, 4):
        ws.cell(row=r, column=j).border = BOX
        ws.cell(row=r, column=j).alignment = CENTER
    for k, it in enumerate(["S1", "S2", "S3", "S4", "S5"], r + 1):
        ws.cell(row=k, column=1, value=it).font = BODY_FONT
        ws.cell(row=k, column=2, value=f"=COUNTIF('需求清单'!$E$3:$E$1000,A{k})")
        ws.cell(row=k, column=3, value=f"=COUNTIFS('需求清单'!$E$3:$E$1000,A{k},'需求清单'!$D$3:$D$1000,\"P0\")")
        for j in range(1, 4):
            ws.cell(row=k, column=j).border = BOX
            ws.cell(row=k, column=j).alignment = CENTER

    # 状态分布
    r2 = total_row + 11
    ws.cell(row=r2, column=1, value="状态").font = HEAD_FONT
    ws.cell(row=r2, column=1).fill = HEAD_FILL
    ws.cell(row=r2, column=2, value="数量").font = HEAD_FONT
    ws.cell(row=r2, column=2).fill = HEAD_FILL
    for j in range(1, 3):
        ws.cell(row=r2, column=j).border = BOX
        ws.cell(row=r2, column=j).alignment = CENTER
    for k, st in enumerate(["⬜ 未开始", "🔨 开发中", "🟡 待测", "✅ 已验收", "⛔ 阻塞"], r2 + 1):
        ws.cell(row=k, column=1, value=st).font = BODY_FONT
        ws.cell(row=k, column=2, value=f"=COUNTIF('需求清单'!$J$3:$J$1000,A{k})")
        for j in range(1, 3):
            ws.cell(row=k, column=j).border = BOX
            ws.cell(row=k, column=j).alignment = CENTER

    for j, w in enumerate([16, 12, 10, 10, 10, 4, 16, 16], 1):
        ws.column_dimensions[get_column_letter(j)].width = w


def main():
    reqs = parse_srs()
    rtm = parse_rtm()
    cases = parse_cases()
    assert len(reqs) == 81, f"需求解析异常：{len(reqs)}"
    assert len(rtm) == 81, f"RTM 解析异常：{len(rtm)}"
    assert len(cases) == 78, f"用例解析异常：{len(cases)}"

    wb = Workbook()
    req_p = {p: sum(1 for r in reqs if r["priority"] == p) for p in ("P0", "P1", "P2")}
    case_p = {p: sum(1 for c in cases if c["priority"] == p) for p in ("P0", "P1", "P2")}
    build_cover(wb, {"req_total": len(reqs), "case_total": len(cases), "req_p": req_p, "case_p": case_p})

    # 重跑时保留手工维护的进度列（需求状态、用例执行状态/缺陷号/备注）
    status_map: dict[str, str] = {}
    case_status: dict[str, tuple[str, str, str]] = {}
    if OUT.exists():
        try:
            old = load_workbook(OUT, data_only=True)
            old_req = old["需求清单"]
            for i in range(3, old_req.max_row + 1):
                rid = old_req.cell(row=i, column=1).value
                if rid:
                    status_map[rid] = old_req.cell(row=i, column=10).value or "⬜ 未开始"
            old_tc = old["测试用例索引"]
            for i in range(3, old_tc.max_row + 1):
                cid = old_tc.cell(row=i, column=1).value
                if cid:
                    case_status[cid] = (
                        old_tc.cell(row=i, column=6).value or "⬜ 未执行",
                        old_tc.cell(row=i, column=7).value or "",
                        old_tc.cell(row=i, column=8).value or "",
                    )
        except Exception:
            # 文件被占用或结构变化时忽略，按默认值重建
            status_map, case_status = {}, {}

    # 需求清单
    ws = wb.create_sheet("需求清单")
    rows = []
    for r in reqs:
        m = rtm.get(r["id"], {})
        ac = "；".join(r["ac"][:3])
        if len(ac) > 320:
            ac = ac[:317] + "…"
        rows.append([
            r["id"], r["domain"], r["name"], r["priority"], r["iteration"],
            m.get("design", "—"), m.get("api", "—"), m.get("table", "—"),
            m.get("tc", "—"), status_map.get(r["id"], "⬜ 未开始"), ac,
        ])
    style_sheet(
        ws, "需求清单 · 81 条（唯一编号，状态可下拉更新）",
        ["需求编号", "域", "需求名称", "优先级", "迭代", "设计章节", "接口", "数据表", "用例", "状态", "验收要点"],
        [14, 8, 30, 8, 7, 18, 40, 24, 28, 12, 60],
        rows,
        data_height=58,
    )
    add_priority_cf(ws, "D", len(rows) + 2)
    add_status_dv(ws, "J", len(rows) + 2)

    # RTM
    ws = wb.create_sheet("需求追踪矩阵")
    rows = []
    for r in reqs:
        m = rtm.get(r["id"], {})
        rows.append([r["id"], r["priority"], r["iteration"], m.get("design", "—"),
                     m.get("api", "—"), m.get("table", "—"), m.get("tc", "—")])
    style_sheet(
        ws, "需求追踪矩阵 · REQ ↔ 设计 ↔ API ↔ 数据表 ↔ 用例",
        ["需求编号", "优先级", "迭代", "设计章节", "接口/验证方式", "数据表", "测试用例"],
        [14, 8, 7, 20, 52, 28, 30],
        rows,
    )
    add_priority_cf(ws, "B", len(rows) + 2)

    # WBS
    ws = wb.create_sheet("WBS与排期")
    rows = [[w[0], w[1], w[2], w[3], w[4], f"W{w[5]}", f"W{w[6]}", w[7], w[8]] for w in WBS]
    style_sheet(
        ws, "WBS 与排期 · 15 周执行 + 1 周缓冲（人日为估算，含缓冲）",
        ["WBS", "工作包", "产出", "负责", "人日", "开始", "结束", "依赖", "状态"],
        [8, 30, 44, 12, 8, 8, 8, 18, 12],
        rows,
    )
    add_status_dv(ws, "I", len(rows) + 2)

    # 里程碑
    ws = wb.create_sheet("里程碑")
    style_sheet(
        ws, "里程碑与质量门禁",
        ["里程碑", "名称", "计划日期", "交付内容", "门禁", "责任人", "状态"],
        [8, 18, 12, 46, 8, 10, 12],
        [list(m) for m in MILESTONES],
    )
    add_status_dv(ws, "G", len(MILESTONES) + 2)

    # 风险
    ws = wb.create_sheet("风险登记册")
    rows = []
    for rid, cat, desc, p, imp, resp, trig, owner, status in RISKS:
        score = p * imp
        level = "🔴 高" if score >= 15 else ("🟡 中" if score >= 8 else "🟢 低")
        rows.append([rid, cat, desc, p, imp, score, level, resp, trig, owner, status])
    style_sheet(
        ws, "风险登记册 · 分值 = 概率 × 影响（≥15 高，8–14 中，≤7 低）",
        ["编号", "类别", "风险描述", "概率", "影响", "分值", "等级", "应对策略", "触发信号", "责任人", "状态"],
        [7, 8, 42, 6, 6, 6, 8, 44, 30, 8, 12],
        rows,
    )
    last = len(rows) + 2
    ws.conditional_formatting.add(
        f"G3:G{last}", FormulaRule(formula=['ISNUMBER(SEARCH("高",G3))'],
                                   fill=PatternFill("solid", fgColor="FEE2E2"),
                                   font=Font(name=FONT, size=10, color="B91C1C", bold=True))
    )
    ws.conditional_formatting.add(
        f"G3:G{last}", FormulaRule(formula=['ISNUMBER(SEARCH("中",G3))'],
                                   fill=PatternFill("solid", fgColor="FEF3C7"))
    )
    ws.conditional_formatting.add(
        f"G3:G{last}", FormulaRule(formula=['ISNUMBER(SEARCH("低",G3))'],
                                   fill=PatternFill("solid", fgColor="DCFCE7"))
    )

    # 测试用例索引
    ws = wb.create_sheet("测试用例索引")
    rows = []
    for c in cases:
        scenario = c["scenario"]
        if len(scenario) > 160:
            scenario = scenario[:157] + "…"
        st, defect, note = case_status.get(c["id"], ("⬜ 未执行", "", ""))
        rows.append([c["id"], c["req"], scenario, c["priority"], c["method"], st, defect, note])
    style_sheet(
        ws, "测试用例索引 · 78 条（执行状态与缺陷号可填）",
        ["用例编号", "关联需求", "场景与步骤（摘要）", "优先级", "方式", "执行状态", "缺陷号", "备注"],
        [14, 14, 60, 8, 8, 12, 12, 24],
        rows,
        data_height=48,
    )
    add_priority_cf(ws, "D", len(rows) + 2)
    dv = DataValidation(type="list", formula1='"⬜ 未执行,✅ 通过,❌ 失败,⏸ 阻塞"', allow_blank=True)
    ws.add_data_validation(dv)
    dv.add(f"F3:F{len(rows) + 2}")

    # 变更
    ws = wb.create_sheet("变更台账")
    style_sheet(
        ws, "变更台账 · C1 重大 / C2 一般 / C3 轻微 / C4 紧急",
        ["变更编号", "日期", "变更内容", "级别", "影响分析", "审批", "状态"],
        [20, 12, 44, 8, 40, 18, 12],
        [list(c) for c in CHANGES],
    )

    # 待确认
    ws = wb.create_sheet("待确认项")
    style_sheet(
        ws, "待确认项 · G1 前需闭环 Q1–Q4",
        ["编号", "问题", "默认决策", "责任人", "截止", "状态"],
        [7, 46, 52, 10, 8, 12],
        [list(i) for i in ISSUES],
    )

    # 统计看板
    build_stats(wb)

    # 打开时强制重算公式，避免预览器显示空白
    wb.calculation.fullCalcOnLoad = True
    wb.properties.title = "助农直播管理系统 · 项目管控台账"
    wb.properties.subject = "G1 需求与设计基线"
    wb.properties.creator = "P1 项目&产品负责人"
    wb.properties.description = "由 docs/ 文档派生；需求、RTM、WBS、风险、里程碑、用例、变更与待确认项"

    # 打印设置
    for sheet in wb.worksheets:
        sheet.page_setup.orientation = "landscape"
        sheet.page_setup.fitToWidth = 1
        sheet.page_setup.fitToHeight = 0
        sheet.sheet_properties.pageSetUpPr.fitToPage = True

    wb.save(OUT)
    print(f"OK -> {OUT}")
    print(f"requirements={len(reqs)}, rtm={len(rtm)}, cases={len(cases)}")


if __name__ == "__main__":
    main()
