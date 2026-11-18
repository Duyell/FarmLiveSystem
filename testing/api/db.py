"""数据库小工具：用 mysql 命令行执行 SQL，供造数与核库使用。

每次调用是独立会话，所以不要依赖 LAST_INSERT_ID()；
需要自增 id 时用唯一编号再 SELECT 回来。
"""
import subprocess

import cfg


def _args():
    return [
        cfg.MYSQL_EXE,
        "--host=" + cfg.DB["host"],
        "--port=" + str(cfg.DB["port"]),
        "--user=" + cfg.DB["user"],
        "--password=" + cfg.DB["password"],
        "--default-character-set=utf8mb4",
        "--database=" + cfg.DB["name"],
    ]


def run_sql(sql):
    """执行一段 SQL（可含中文），失败抛异常。"""
    proc = subprocess.run(_args(), input=sql.encode("utf-8"), capture_output=True)
    if proc.returncode != 0:
        msg = (proc.stderr or proc.stdout).decode("utf-8", "replace").strip()
        raise RuntimeError("SQL 执行失败: " + msg)


def query(sql):
    """执行查询，返回字符串二维表。"""
    proc = subprocess.run(_args() + ["-N", "-B", "-e", sql], capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(
            "SQL 查询失败: " + proc.stderr.decode("utf-8", "replace").strip()
        )
    out = proc.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in out.splitlines() if line != ""]


def scalar(sql, default=None):
    rows = query(sql)
    return rows[0][0] if rows else default
