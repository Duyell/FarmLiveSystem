# 数据库备份与恢复

> 依据 yaoqiu.txt 技术要求：设置数据备份策略（定时备份、手动备份），防止农户资质、预约数据丢失。

## 一、备份脚本

`deploy/backup/backup-mysql.ps1`：

- 使用 `mysqldump` 导出 `farm_live` 库（含表结构与数据，utf8mb4）；
- 文件名带时间戳：`farm_live_20261116_183000.sql.gz`（若系统无 gzip 则保留 .sql）；
- 自动清理超过保留期（默认 14 天）的旧备份；
- 备份失败时非零退出，便于定时任务告警。

## 二、手动备份

```powershell
# 参数化执行
powershell -ExecutionPolicy Bypass -File deploy\backup\backup-mysql.ps1 ^
  -MysqlUser root -MysqlPassword "你的密码" -BackupDir E:\farm_live_backup -KeepDays 14
```

默认值（不传参数时）：用户 `farmlive`、密码 `farmlive123`、库 `farm_live`、备份目录 `E:\farm_live_backup`、保留 14 天。

## 三、注册定时备份（每天凌晨 2:30）

以管理员身份运行：

```powershell
schtasks /Create /TN "FarmLive-MySQL-Backup" ^
  /TR "powershell -ExecutionPolicy Bypass -File E:\fuzhou\dinggangshixi\FarmLiveSystem\deploy\backup\backup-mysql.ps1 -MysqlUser farmlive -MysqlPassword farmlive123 -BackupDir E:\farm_live_backup -KeepDays 14" ^
  /SC DAILY /ST 02:30
```

验证：`schtasks /Query /TN "FarmLive-MySQL-Backup"`；手动跑一次：`schtasks /Run /TN "FarmLive-MySQL-Backup"`。

## 四、恢复

```powershell
# 1. 解压（.gz 备份）
gzip -d farm_live_20261116_183000.sql.gz

# 2. 恢复到库（会覆盖同名表数据，请先确认）
mysql --host=127.0.0.1 --port=3306 --user=farmlive --password=farmlive123 farm_live < farm_live_20261116_183000.sql
```

整库重建（库被误删时）：

```powershell
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS farm_live DEFAULT CHARSET utf8mb4;"
mysql -uroot -p farm_live < farm_live_20261116_183000.sql
```

## 五、建议

- 备份目录不要与数据库同盘；重要周期（如每月 1 日）将备份文件复制到外部存储；
- 恢复演练：交接/验收前用最近一份备份在测试库完整恢复一次，验证备份可用；
- 生产环境将 `MysqlPassword` 移入任务计划的安全凭据或加密配置，避免明文留在命令历史。
