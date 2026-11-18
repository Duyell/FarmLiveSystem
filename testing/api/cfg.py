"""测试套件配置：服务地址、测试账号密码、数据库连接、路径。

全部可用环境变量覆盖，默认值对应本机测试环境。
"""
import os
import pathlib

# 后端地址（建议直连 8080；也可以填 5173 走前端代理）
BASE_URL = os.environ.get("FARM_API", "http://127.0.0.1:8080").rstrip("/")

# 所有测试账号统一密码（setup_env.py 会把它写进数据库）
TEST_PASSWORD = os.environ.get("FARM_TEST_PASSWORD", "Test@1234")

# 数据库：只用来造数和核库，不参与接口断言；连不上可设 FARM_CHECK_DB=0
MYSQL_EXE = os.environ.get(
    "MYSQL_EXE", r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)
CHECK_DB = os.environ.get("FARM_CHECK_DB", "1") == "1"
DB = {
    "host": os.environ.get("FARM_DB_HOST", "127.0.0.1"),
    "port": os.environ.get("FARM_DB_PORT", "3306"),
    "user": os.environ.get("FARM_DB_USER", "farmlive"),
    "password": os.environ.get("FARM_DB_PASSWORD", "farmlive123"),
    "name": os.environ.get("FARM_DB_NAME", "farm_live"),
}

ROOT = pathlib.Path(__file__).resolve().parent
STATE_FILE = ROOT / ".state.json"
# 报告统一放在 testing/reports，与脚本目录分开
REPORT_DIR = ROOT.parent / "reports"
