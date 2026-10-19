# 助农直播管理系统 · Git 分支与提交规范

| 项 | 内容 |
|---|---|
| 文档编号 | C3-01 |
| 版本 | v1.0 |
| 编制/审核 | P2 / P1 |
| 平台 | Gitee（主）/ GitHub（镜像） |
| 强制级别 | 强制；CI + 分支保护双重校验 |

---

## 1. 分支模型

```text
main         ●──────────●────────────●──────────●   （受保护，仅发布）
              \        /              \        /
release        ●──────●                ●──────●     （发布候选，仅修复）
                \    /                  \    /
develop      ●───●───●───●───●───●───●───●───●     （集成分支）
              \     /     \     /
feature        ●───●       ●───●                    （功能，短生命周期）
hotfix                    （从 main 拉出，修完合回 main + develop）
```

| 分支 | 来源 | 合并到 | 生命周期 | 说明 |
|---|---|---|---|---|
| `main` | — | — | 永久 | 生产可发布；保护分支 |
| `develop` | `main` | `main`（迭代末） | 永久 | 日常集成 |
| `feature/<REQ>-<简述>` | `develop` | `develop` | ≤5 天 | 一个需求一个分支 |
| `fix/<issue>-<简述>` | `develop` | `develop` | ≤2 天 | 缺陷修复 |
| `release/<version>` | `develop` | `main` + `develop` | 1 周 | 发布候选，只修不增 |
| `hotfix/<version>` | `main` | `main` + `develop` | ≤1 天 | 紧急修复 |
| `docs/<简述>` | `develop` | `develop` | ≤3 天 | 纯文档 |
| `chore/<简述>` | `develop` | `develop` | ≤2 天 | 构建/依赖/配置 |

### 1.1 命名示例

```text
feature/REQ-BOOK-03-conflict-lock
feature/REQ-QUEUE-02-queue-no
fix/123-review-version-race
release/v0.5.0
hotfix/v0.5.1-sms-npe
docs/g1-baseline
```

**规则**：全小写、连字符分隔、必须含 REQ 或 Issue 号；禁止 `test`、`tmp`、`aaa`、中文分支名。

### 1.2 分支保护

| 分支 | 保护规则 |
|---|---|
| `main` | 禁止直推；需 PR + 1 人 Approve + CI 绿 + 无冲突；仅 P1/P2 可合并 |
| `develop` | 禁止直推；需 PR + 1 人 Approve + CI 绿 |
| `release/*` | 仅 P1/P2 可合并；只接受 fix |

---

## 2. 提交信息规范

### 2.1 格式

```text
<type>(<REQ-ID>): [<RED|GREEN>] <subject>

<body：为什么改、改了什么、影响范围>
<footer：BREAKING CHANGE / Closes #123>
```

- **type**：`feat` 新功能、`fix` 缺陷、`docs` 文档、`style` 格式、`refactor` 重构、`test` 测试、`chore` 构建/依赖、`perf` 性能。
- **REQ-ID**：关联网关需求（如 `REQ-BOOK-03`）；无对应需求用 Issue 号。
- **RED/GREEN**：TDD 成对提交；`RED` 先写失败测试，`GREEN` 使其通过。
- **subject**：≤50 字，动词开头，不写句号。
- **body**：说明"为什么"，不是"改了什么"（diff 已说明）。

### 2.2 示例

```text
feat(REQ-BOOK-03): [RED] 增加同时段并发预约冲突用例

当前实现先查后写，100 并发会出现 2 条有效占用。
新增失败用例：100 线程抢同一设备同一时段，期望仅 1 成功。

Refs: #45
```

```text
feat(REQ-BOOK-03): [GREEN] 用 resource_lock 唯一约束裁决冲突

- 按 resource_type/resource_id/slot_start/active_flag 建唯一索引
- 固定锁顺序，捕获 DuplicateKey 返回 409
- Redis 前置锁保留，仅做削峰

Test: BookingConcurrencyTest#shouldOnlyOneSucceed
```

### 2.3 禁止

- `update`、`fix bug`、`123`、`。`、`asdf`
- 一次提交混合多个无关需求
- 提交生成的 `target/`、`node_modules/`、`.env`、密钥、大文件
- 直接推 `main`

### 2.4 提交粒度

| 粒度 | 建议 |
|---|---|
| 一个提交 | 一个可解释的逻辑变更（≤400 行 diff） |
| 一个 PR | 一个 REQ 或一个缺陷（≤800 行 diff） |
| 超过 800 行 | 拆分为多个 PR，或说明理由并经 P2 同意 |

---

## 3. PR 流程

### 3.1 PR 模板（仓库内置）

```markdown
## 关联
- REQ：REQ-XXX-NN
- Issue：#
- 设计/接口：D2-xx §x

## 变更内容
- ……

## 如何验证
1. ……
2. 命令/接口/页面：
- 截图/录屏：

## 测试
- [ ] 新增/修改单测
- [ ] 本地跑通
- [ ] 影响面回归

## 风险与回滚
- 风险：
- 回滚方式：

## 自查
- [ ] 无密钥/敏感信息
- [ ] 注释率达标
- [ ] 无 TODO 遗留（或已登记）
- [ ] 文档已同步
```

### 3.2 评审规则

| 项 | 要求 |
|---|---|
| 评审人 | ≥1 人；核心模块（预约/排队/权限/加密）需 P2 |
| 时效 | 24 小时内首轮反馈 |
| 评论 | 具体到行；区分"必须改/建议" |
| Approve | CI 绿 + 所有必须项解决 |
| 合并 | Squash merge；保留 PR 标题为提交信息 |
| 合并后 | 删除远端分支；更新看板 |

### 3.3 评审关注点

1. 是否满足 REQ 的 AC；
2. 并发/事务/幂等；
3. 异常与边界（空、超时、重复、越权）；
4. 敏感数据（日志/响应/导出）；
5. 测试是否真的断言（非空转）；
6. 命名/分层/注释；
7. 性能（N+1、全表扫描、循环远程调用）。

---

## 4. 日常协作流程

```text
1. 从 develop 拉分支：git switch -c feature/REQ-XXX-NN-xxx
2. 写测试（RED）→ 提交 → 实现（GREEN）→ 提交
3. 本地：mvn verify / pnpm lint && pnpm test
4. push → 开 PR → CI → 评审 → Squash merge
5. 删除分支 → 更新看板 → 通知测试
```

### 4.1 同步与冲突

```bash
git fetch origin
git rebase origin/develop      # 功能分支用 rebase，保持线性
# 解决冲突后
git add .
git rebase --continue
git push --force-with-lease    # 仅自己的分支
```

- 禁止对 `develop/main` 强推。
- 冲突涉及他人代码时，先沟通再改。

### 4.2 代码冻结

| 时点 | 冻结范围 | 例外 |
|---|---|---|
| 迭代最后 1 天 | 新功能 | P0 缺陷修复（P1 批准） |
| 发布前 24h | 所有代码 | 仅 hotfix |
| 答辩前 24h | 所有代码 | 仅演示阻断缺陷 |

---

## 5. 标签与发布

| 标签 | 含义 | 示例 |
|---|---|---|
| `v0.x.0` | 迭代内测 | `v0.5.0` |
| `v1.0.0` | 正式交付 | `v1.0.0` |
| `v1.0.1` | 缺陷修复 | `v1.0.1` |

发布步骤：

```bash
git switch main && git pull
git tag -a v1.0.0 -m "release v1.0.0"
git push origin v1.0.0
# CI 自动构建镜像、生成 release notes、归档 OpenAPI 与测试报告
```

Release Notes 模板：新增/修复/变更/已知问题/升级步骤/回滚方式。

---

## 6. 仓库结构约定

```text
FarmLiveSystem/
├─ backend/            # Spring Boot
│  ├─ src/main/java/com/farm/live/{module}/
│  ├─ src/main/resources/{mapper,db/migration}/
│  └─ src/test/java/
├─ ai-service/         # FastAPI
│  ├─ app/{api,core,rag,models}/
│  └─ tests/
├─ frontend/           # pnpm workspace
│  ├─ apps/admin/
│  ├─ apps/h5/
│  └─ packages/{api-client,shared}/
├─ deploy/             # compose/nginx/sql/scripts
├─ docs/               # 本套文档
├─ tests/              # E2E/performance/security
├─ .gitee/             # PR/Issue 模板、CI
└─ README.md
```

### 6.1 必须忽略

```gitignore
# 构建
target/
dist/
node_modules/
.venv/
__pycache__/
*.pyc

# 环境与密钥
.env
.env.*
!.env.example
*.pem
*.key
secrets/

# IDE/系统
.idea/
.vscode/
.DS_Store
Thumbs.db

# 运行产物
logs/
uploads/
*.log
```

### 6.2 提交前检查

- [ ] `git status` 无意外文件
- [ ] 无 `.env`/密钥/证书
- [ ] 无 `target/`/`node_modules/`
- [ ] 大文件（>5MB）未提交（用 Git LFS 或对象存储）
- [ ] 提交信息符合格式

---

## 7. Issue 与看板

| 类型 | 模板字段 |
|---|---|
| 需求 | REQ、优先级、验收标准、迭代、设计链接 |
| 缺陷 | 环境/版本、复现步骤、期望/实际、证据、严重度、REQ |
| 任务 | 描述、负责人、截止、依赖 |
| 风险 | 描述、概率、影响、应对、责任人 |

看板列：`待办 → 进行中 → 待评审 → 待测试 → 已完成`；WIP 限制：每人 ≤2。

---

## 8. 常见问题

| 问题 | 处理 |
|---|---|
| 误提交密钥 | 立即轮换密钥 → `git filter-repo` 清理历史 → 通知全员 → 复盘 |
| 分支落后太多 | rebase develop，逐段解决冲突 |
| CI 因环境失败 | 本地用 Docker 复现；不要改 CI 绕过 |
| 合并后 CI 红 | 立即回滚该 PR；修复后重提 |
| 需要紧急上线 | hotfix 分支 + P2 复核 + 合并 main + 回合并 develop |

---

## 9. 修订记录

| 版本 | 日期 | 修改人 | 说明 |
|---|---|---|---|
| v1.0 | 2026-10-19 | P2 | 首版：分支、提交、PR、发布、仓库结构 |
