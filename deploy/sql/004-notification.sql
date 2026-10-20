-- 004 通知模块（只执行一次）
-- 适用于已经执行过 init.sql 的现有开发库。
-- 新环境直接执行最新 init.sql，不需要再执行本文件。

USE farm_live;

ALTER TABLE notification
  ADD COLUMN next_retry_at DATETIME NULL AFTER retry_count,
  ADD COLUMN read_at DATETIME NULL AFTER sent_at,
  ADD INDEX idx_notification_retry (status, next_retry_at);

CREATE TABLE IF NOT EXISTS notification_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL UNIQUE COMMENT '模板编码',
  name VARCHAR(80) NOT NULL,
  type VARCHAR(30) NOT NULL COMMENT 'review/queue/candidate/reminder/device_fault',
  title_template VARCHAR(120) NOT NULL,
  content_template VARCHAR(1000) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知模板，变量用 ${name} 占位';

INSERT IGNORE INTO notification_template (code, name, type, title_template, content_template) VALUES
  ('review_approved', '审核通过', 'review',
   '预约审核通过',
   '${farmerName}，您预约的「${topic}」已通过审核，${startTime} 在 ${venueName} 直播，请准时到场。'),
  ('review_rejected', '审核驳回', 'review',
   '预约审核未通过',
   '${farmerName}，您预约的「${topic}」未通过审核：${reviewComment}。推荐时间：${recommendedTime}。'),
  ('candidate_promoted', '候补提醒', 'candidate',
   '候补资格已获得',
   '${farmerName}，${venueName} ${startTime} 的场次释放，您已升级为候补，请在 ${deadline} 前确认，超时自动顺延。'),
  ('queue_position', '顺位变化', 'queue',
   '排队顺位更新',
   '${farmerName}，${venueName} ${startTime} 的排队有变化，您当前顺位第 ${position} 位。'),
  ('booking_reminder', '开播提醒', 'reminder',
   '开播前 1 小时提醒',
   '${farmerName}，您预约的「${topic}」将于 ${startTime} 在 ${venueName} 开播，请提前做好准备。'),
  ('device_fault', '设备故障', 'device_fault',
   '设备故障通知',
   '${farmerName}，您 ${startTime} 在 ${venueName} 预约的设备「${deviceName}」出现故障，管理员将尽快为您调整。');

SELECT 'notification migration done' AS result;
