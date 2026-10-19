# 助农直播管理系统

助农直播管理系统，当前完成第一阶段初步开发：

- 登录、退出、修改初始密码；
- 系统管理员工作台；
- 账号、农户、场地、设备四类数据的增删改查；
- 电脑管理端页面。

预约、排队、候补、通知、统计和 AI 助手在后续迭代实现。

---

## 1. 目录结构

```text
backend/                 Spring Boot 3 + Java 17 后端
frontend/                Vue 3 + TypeScript + Element Plus 管理端
docs/                    需求、设计、测试、交付文档
init.sql                 MySQL 初始化脚本
maven-settings.xml       本机 Maven 仓库配置（不提交）
```

---

## 2. 环境要求

- JDK 17 及以上（当前开发环境 JDK 21 可编译 Java 17）
- Maven 3.9+
- Node.js 20+、npm
- MySQL 8

---

## 3. 初始化数据库

```bash
mysql -uroot -p < init.sql
```

脚本会创建：

- 数据库 `farm_live`；
- 开发用户 `farmlive`，密码 `farmlive123`；
- 16 张表；
- 四种角色；
- 系统管理员：`admin / Admin@123`，首次登录必须改密；
- 安徽省 / 淮南市 / 寿县等区域字典数据；
- 演示农户、场地、设备。

> 如果本机 MySQL 不在 3306，用环境变量指定端口，见下一节。

---

## 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认连接：

```text
127.0.0.1:3308 / farm_live / farmlive / farmlive123
```

如果本机 MySQL 是 3306，用：

```bash
DB_PORT=3306 mvn spring-boot:run
```

Windows PowerShell：

```powershell
$env:DB_PORT="3306"
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/health
```

---

## 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:5173
```

初始账号：

```text
admin / Admin@123
```

首次登录会跳转到修改密码页，修改后重新登录。

---

## 6. 构建

```bash
cd backend
mvn -DskipTests package

cd ../frontend
npm run build
```

前端构建产物在 `frontend/dist/`。

---

## 7. 当前接口

| 分组 | 地址 |
|---|---|
| 登录 | `POST /api/auth/login` |
| 当前用户 | `GET /api/auth/me` |
| 修改密码 | `POST /api/auth/change-password` |
| 退出 | `POST /api/auth/logout` |
| 工作台 | `GET /api/admin/dashboard` |
| 角色 | `GET /api/admin/roles` |
| 账号 | `GET/POST/PUT/DELETE /api/admin/accounts` |
| 农户 | `GET/POST/PUT/DELETE /api/admin/farmers` |
| 场地 | `GET/POST/PUT/DELETE /api/admin/venues` |
| 设备 | `GET/POST/PUT/DELETE /api/admin/devices` |

完整字段见 `docs/02_设计/09-接口文档-正式版.md`。

---

## 8. 文档

- 需求分析：`docs/01_需求/08-需求分析-正式版.md`
- 数据库设计：`docs/02_设计/08-数据库设计-正式版.md`
- 接口文档：`docs/02_设计/09-接口文档-正式版.md`
- 开发计划：`docs/00_项目管理/07-开发计划-正式版.md`
- 技术方案：`docs/02_设计/00-技术方案确认-送审稿.md`

---

## 9. 注意

- 不要提交 `.env`、真实密码、生产数据；
- 演示数据全部为假数据；
- 手机号列表默认脱敏；
- 数据库不存身份证号、家庭住址；
- 当前 AI 助手、预约、排队、通知尚未实现。
