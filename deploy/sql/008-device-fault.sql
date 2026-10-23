-- 008 设备组合故障记录：报修、维修完成、恢复通知
-- 执行时指定目标库，例如：
-- mysql -uroot -p -P3306 FarmLiveSys < 008-device-fault.sql

CREATE TABLE IF NOT EXISTS device_fault (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  fault_part VARCHAR(80) NOT NULL,
  description VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'reported',
  reported_by BIGINT NOT NULL,
  reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  resolution VARCHAR(500) NULL,
  CONSTRAINT fk_fault_device FOREIGN KEY (device_id) REFERENCES device(id),
  CONSTRAINT fk_fault_account FOREIGN KEY (reported_by) REFERENCES account(id),
  INDEX idx_fault_device (device_id, status),
  INDEX idx_fault_reported (reported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备组合故障与维修记录';

INSERT IGNORE INTO notification_template
  (code, name, type, title_template, content_template)
VALUES
  ('device_recovered', '设备恢复', 'device_fault',
   '设备恢复通知',
   '${farmerName}，您 ${startTime} 在 ${venueName} 预约的设备「${deviceName}」已维修完成并恢复可用。');

SELECT 'device fault migration done' AS result;
