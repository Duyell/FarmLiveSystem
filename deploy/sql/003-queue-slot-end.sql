-- 003 排队模块：queue_ticket 增加 slot_end（只执行一次）
-- 适用于已经执行过 init.sql 的现有开发库。
-- 新环境直接执行最新 init.sql，不需要再执行本文件。
-- 已有排队记录没有结束时间，按开始时间后 2 小时回填；如有真实结束时间，执行前先补数据。

USE farm_live;

ALTER TABLE queue_ticket
  ADD COLUMN slot_end DATETIME NULL AFTER slot_start;

UPDATE queue_ticket
SET slot_end = DATE_ADD(slot_start, INTERVAL 2 HOUR)
WHERE slot_end IS NULL;

ALTER TABLE queue_ticket
  MODIFY COLUMN slot_end DATETIME NOT NULL;

SELECT 'queue slot_end migration done' AS result;
