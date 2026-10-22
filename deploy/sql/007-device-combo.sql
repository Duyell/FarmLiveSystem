-- 007 设备组合：device 增加组合配置，现有设备改为设备组合（只执行一次）
-- 适用于已经执行过 init.sql 的现有开发库。

USE farm_live;

ALTER TABLE device
  ADD COLUMN config VARCHAR(500) NULL COMMENT '组合包含的设备配置' AFTER model;

UPDATE device SET name = '设备组合1', model = '手机直播组合',
  config = '手机 + 三脚架 + 双补光灯 + 无线领夹麦 + 5G 路由 + 插排'
WHERE device_no = 'ZB-C001-01';

UPDATE device SET name = '设备组合2', model = '补光增强组合',
  config = '手机 + 三脚架 + 三补光灯 + 无线领夹麦 + 5G 路由 + UPS'
WHERE device_no = 'ZB-C001-02';

UPDATE device SET name = '设备组合3', model = '基础直播组合',
  config = '手机 + 三脚架 + 双补光灯 + 无线领夹麦 + 场地宽带 + 插排'
WHERE device_no = 'ZB-C002-01';

SELECT 'device combo migration done' AS result;
