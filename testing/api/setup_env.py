"""一键准备测试环境：清理上一轮测试数据，重建专属账号、农户、场地与设备。

只操作本套件自己的数据（账号 t_*、农户 NFTEST*、场地 C900、设备 ZBT*），
不碰现有业务数据。可反复执行。

用法：
    python setup_env.py
"""
import hashlib
import json
import os
import sys
from datetime import datetime

import cfg
import db

ITERATIONS = 120000


def hash_password(password):
    """与后端 PasswordService 一致的 pbkdf2_sha256 格式。"""
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt,
                                 ITERATIONS, dklen=32)
    return "pbkdf2_sha256:{}:{}:{}".format(
        ITERATIONS, salt.hex(), digest.hex())


def purge():
    """删除本套件上一轮产生的数据，顺序满足外键约束。"""
    farmer_ids = ("SELECT id FROM farmer WHERE farmer_no LIKE 'NFTEST%'")
    account_ids = "SELECT id FROM account WHERE username LIKE 't\\_%'"
    booking_ids = "SELECT id FROM booking WHERE farmer_id IN ({})".format(farmer_ids)
    device_ids = "SELECT id FROM device WHERE device_no LIKE 'ZBT%'"
    db.run_sql("""
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM notification WHERE account_id IN ({acc});
DELETE FROM qa_record WHERE account_id IN ({acc});
DELETE FROM booking_product WHERE booking_id IN ({bk});
DELETE FROM resource_lock WHERE booking_id IN ({bk});
DELETE FROM sales_record WHERE booking_id IN ({bk});
DELETE FROM adjustment_request WHERE booking_id IN ({bk});
DELETE FROM queue_ticket WHERE farmer_id IN ({fr});
DELETE FROM device_fault WHERE device_id IN ({dv});
DELETE FROM farmer_qualification WHERE farmer_id IN ({fr});
DELETE FROM booking WHERE farmer_id IN ({fr});
DELETE FROM account WHERE username LIKE 't\\_%';
DELETE FROM farmer WHERE farmer_no LIKE 'NFTEST%';
DELETE FROM device WHERE device_no LIKE 'ZBT%';
DELETE FROM venue WHERE venue_no = 'C900';
SET FOREIGN_KEY_CHECKS = 1;
""".format(acc=account_ids, bk=booking_ids, fr=farmer_ids, dv=device_ids))


def create_base_data(run):
    hash_value = hash_password(cfg.TEST_PASSWORD)

    # 场地与设备
    db.run_sql("""
INSERT INTO venue (venue_no, name, location, county_code, capacity, open_start, open_end, status)
VALUES ('C900', '自动化测试场地', '测试环境', '340422', 20, '08:00:00', '22:00:00', 'available');
""")
    venue_id = int(db.scalar("SELECT id FROM venue WHERE venue_no = 'C900'"))
    for i in (1, 2, 3):
        db.run_sql("""
INSERT INTO device (device_no, name, model, config, venue_id, status, calibration_date)
VALUES ('ZBT-C900-0{i}', '测试设备组合{i}', '手机直播组合',
        '手机 + 三脚架 + 双补光灯 + 无线领夹麦 + 5G 路由 + 插排', {v}, 'available', '2027-12-31');
""".format(i=i, v=venue_id))

    # 农户档案
    farmers = {}
    for i, (no, name, phone) in enumerate(
            [("NFTEST01", "测试农户一", "13900000001"),
             ("NFTEST02", "测试农户二", "13900000002"),
             ("NFTEST03", "测试农户三", "13900000003")], start=1):
        db.run_sql("""
INSERT INTO farmer (farmer_no, name, county, county_code, phone, qualification_status, status)
VALUES ('{no}', '{name}', '寿县', '340422', '{phone}', 'verified', 'active');
""".format(no=no, name=name, phone=phone))
        farmers["farmer{}".format(i)] = int(
            db.scalar("SELECT id FROM farmer WHERE farmer_no = '{}'".format(no)))

    # 各角色账号
    accounts = {
        "admin": ("t_admin", "测试系统管理员", "system_admin", None, "active", 0),
        "live": ("t_live", "测试直播管理员", "live_admin", None, "active", 0),
        "viewer": ("t_viewer", "测试统计员", "data_viewer", None, "active", 0),
        "farmer1": ("t_farmer1", "测试农户一", "farmer", farmers["farmer1"], "active", 0),
        "farmer2": ("t_farmer2", "测试农户二", "farmer", farmers["farmer2"], "active", 0),
        "farmer3": ("t_farmer3", "测试农户三", "farmer", farmers["farmer3"], "active", 0),
    }
    # 探针账号：验证码、锁定、强制改密、密码修改、停用
    probes = {
        "must": ("t_must_{}".format(run), "首登改密探针", "system_admin", None, "active", 1),
        "pwd": ("t_pwd_{}".format(run), "改密探针", "system_admin", None, "active", 0),
        "lock": ("t_lock_{}".format(run), "锁定探针", "system_admin", None, "active", 0),
        "disabled": ("t_disabled", "停用探针", "system_admin", None, "disabled", 0),
    }

    def insert_account(row):
        username, display, role, farmer_id, status, must_change = row
        db.run_sql("""
INSERT INTO account (username, password_hash, display_name, role_id, farmer_id,
                     status, must_change_password)
VALUES ('{u}', '{h}', '{d}', (SELECT id FROM role WHERE code = '{r}'),
        {f}, '{s}', {m});
""".format(u=username, h=hash_value, d=display, r=role,
           f="NULL" if farmer_id is None else farmer_id,
           s=status, m=must_change))

    for row in accounts.values():
        insert_account(row)
    for row in probes.values():
        insert_account(row)

    account_ids = [int(db.scalar(
        "SELECT id FROM account WHERE username = '{}'".format(row[0])))
        for row in list(accounts.values()) + list(probes.values())]

    devices = [{
        "id": int(db.scalar("SELECT id FROM device WHERE device_no = 'ZBT-C900-0{}'".format(i))),
        "no": "ZBT-C900-0{}".format(i),
    } for i in (1, 2, 3)]

    return {
        "run": run,
        "password": cfg.TEST_PASSWORD,
        "venue": {"id": venue_id, "no": "C900"},
        "devices": devices,
        "farmers": farmers,
        "accounts": {k: v[0] for k, v in accounts.items()},
        "probes": {k: v[0] for k, v in probes.items()},
        "account_ids": account_ids,
    }


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass
    if not os.path.exists(cfg.MYSQL_EXE):
        print("找不到 mysql 客户端: " + cfg.MYSQL_EXE)
        print("请安装 MySQL 客户端，或设置环境变量 MYSQL_EXE 指向 mysql.exe")
        return 1

    run = datetime.now().strftime("%H%M%S")
    print("清理上一轮测试数据 ...")
    purge()
    print("创建测试账号与基础资源 ...")
    state = create_base_data(run)
    cfg.STATE_FILE.write_text(
        json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n环境就绪：")
    print("  场地   C900  id={}".format(state["venue"]["id"]))
    print("  设备   {} 个".format(len(state["devices"])))
    print("  农户   {} 个".format(len(state["farmers"])))
    print("  账号   " + ", ".join(sorted(state["accounts"].values())))
    print("  探针   " + ", ".join(sorted(state["probes"].values())))
    print("  统一密码 {}".format(cfg.TEST_PASSWORD))
    print("  状态文件 {}".format(cfg.STATE_FILE))
    return 0


if __name__ == "__main__":
    sys.exit(main())
