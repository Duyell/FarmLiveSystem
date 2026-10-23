-- 009 排队绑定预约、预约调整通知、设备故障取消预约
-- 执行示例：mysql -uroot -p -P3306 FarmLiveSys < 009-queue-adjustment-fault.sql

ALTER TABLE booking MODIFY COLUMN device_id BIGINT NULL;

ALTER TABLE queue_ticket
  ADD COLUMN booking_id BIGINT NULL AFTER farmer_id,
  ADD CONSTRAINT fk_queue_request_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
  ADD INDEX idx_queue_booking (booking_id);

-- 把已有的排队记录关联到同一农户、场地、时段的 queued 预约（如果有）
UPDATE queue_ticket q
JOIN booking b
  ON b.farmer_id = q.farmer_id
 AND b.venue_id = q.venue_id
 AND b.start_time = q.slot_start
 AND b.status = 'queued'
SET q.booking_id = b.id
WHERE q.booking_id IS NULL;

INSERT IGNORE INTO notification_template
  (code, name, type, title_template, content_template)
VALUES
  ('adjustment_approved', '调整通过', 'review',
   '预约调整已通过',
   '${farmerName}，您预约「${bookingNo}」的调整申请已通过：${startTime} 在 ${venueName} 直播，请准时到场。'),
  ('adjustment_rejected', '调整驳回', 'review',
   '预约调整未通过',
   '${farmerName}，您预约「${bookingNo}」的调整申请未通过：${reviewComment}。'),
  ('device_fault_canceled', '设备故障取消预约', 'device_fault',
   '预约已因设备故障取消',
   '${farmerName}，您 ${startTime} 在 ${venueName} 的预约「${topic}」因设备「${deviceName}」故障无法使用，已自动取消，请重新预约。');

SELECT 'queue/adjustment migration done' AS result;
