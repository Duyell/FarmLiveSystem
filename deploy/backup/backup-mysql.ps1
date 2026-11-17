# 助农直播管理系统 MySQL 备份脚本（PowerShell）
# 用法示例：
#   powershell -ExecutionPolicy Bypass -File backup-mysql.ps1 `
#     -MysqlUser farmlive -MysqlPassword farmlive123 -Database farm_live `
#     -BackupDir E:\farm_live_backup -KeepDays 14
# 定时任务注册见同目录 README.md 第三节。

param(
    [string]$MysqlUser = "farmlive",
    [string]$MysqlPassword = "",
    [string]$Database = "farm_live",
    [string]$BackupDir = "E:\farm_live_backup",
    [int]$KeepDays = 14
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

# 找 mysqldump（优先 PATH，其次常见安装目录）
$mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
if ($mysqldump) {
    $dumpExe = $mysqldump.Source
} else {
    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
        "C:\xampp\mysql\bin\mysqldump.exe"
    ) | Where-Object { Test-Path $_ }
    if (-not $candidates) {
        Write-Error "找不到 mysqldump.exe，请安装 MySQL 客户端或把 bin 目录加入 PATH"
        exit 1
    }
    $dumpExe = $candidates[0]
}

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$baseName = Join-Path $BackupDir "${Database}_$timestamp"
$sqlFile = "$baseName.sql"
$gzipFile = "$baseName.sql.gz"

Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 开始备份 $Database -> $sqlFile"

# 用 cmd 执行并重定向，隔离 mysqldump 的 stderr 告警
cmd /c "`"$dumpExe`" --host=127.0.0.1 --port=3306 --user=$MysqlUser --password=$MysqlPassword --default-character-set=utf8mb4 --single-transaction --routines --triggers $Database > `"$sqlFile`" 2>NUL"

if ($LASTEXITCODE -ne 0 -or -not (Test-Path $sqlFile) -or (Get-Item $sqlFile).Length -eq 0) {
    Write-Error "mysqldump 备份失败（exit=$LASTEXITCODE）"
    exit 1
}

$size = (Get-Item $sqlFile).Length
Write-Host "备份完成：$sqlFile（$([math]::Round($size/1KB, 1)) KB）"

# 有 gzip 则压缩，删除原始 sql
$gzip = Get-Command gzip -ErrorAction SilentlyContinue
if ($gzip) {
    & $gzip -9 $sqlFile
    if (Test-Path $gzipFile) {
        Write-Host "已压缩：$gzipFile（$([math]::Round((Get-Item $gzipFile).Length/1KB, 1)) KB）"
    }
}

# 清理超过保留期的旧备份
$cutoff = (Get-Date).AddDays(-$KeepDays)
Get-ChildItem $BackupDir -Filter "${Database}_*.sql*" | Where-Object {
    $_.LastWriteTime -lt $cutoff
} | ForEach-Object {
    Remove-Item $_.FullName -Force
    Write-Host "清理过期备份：$($_.Name)"
}

Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 备份任务完成"
exit 0
