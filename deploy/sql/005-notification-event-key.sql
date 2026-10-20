-- 005 通知事件关联键（只执行一次）
-- 适用于已经执行过 004 的现有开发库。
-- 同一事件的站内/短信记录共享 event_key，我的通知按事件合并展示。

USE farm_live;

ALTER TABLE notification
  ADD COLUMN event_key VARCHAR(80) NULL AFTER type,
  ADD INDEX idx_notification_event (event_key);

SELECT 'notification event_key migration done' AS result;
