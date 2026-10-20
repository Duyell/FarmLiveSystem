-- 003 排队模块：queue_ticket 增加 slot_end（只执行一次）
-- 适用于已经执行过 init.sql 的现有开发库。
-- 新环境直接执行最新 init.sql，不需要再执行本文件。

USE farm_live;

ALTER TABLE queue_ticket
  ADD COLUMN slot_end DATETIME NOT NULL AFTER slot_start;

SELECT 'queue slot_end migration done' AS result;
