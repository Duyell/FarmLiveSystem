-- 011 设备校准提醒模板（只执行一次）

USE farm_live;

INSERT IGNORE INTO notification_template
  (code, name, type, title_template, content_template)
VALUES
  ('device_calibration', '设备校准提醒', 'calibration',
   '设备校准到期提醒',
   '设备「${deviceName}」（${deviceNo}）将于 ${calibrationDate} 到达校准周期，请及时安排校准，逾期未校准设备可能影响直播质量。');

SELECT 'calibration template done' AS result;
