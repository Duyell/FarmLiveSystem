-- 助农直播管理系统 数据库初始化脚本
-- MySQL 8.0+
-- 用法：mysql -uroot -p < init.sql
-- 本地开发默认：数据库 farm_live，用户 farmlive / farmlive123

CREATE DATABASE IF NOT EXISTS farm_live
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'farmlive'@'localhost' IDENTIFIED BY 'farmlive123';
ALTER USER 'farmlive'@'localhost' IDENTIFIED BY 'farmlive123';
GRANT ALL PRIVILEGES ON farm_live.* TO 'farmlive'@'localhost';
FLUSH PRIVILEGES;

USE farm_live;
SET NAMES utf8mb4;

-- ============================================================
-- 1. 角色
-- ============================================================
CREATE TABLE IF NOT EXISTS role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(200) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- ============================================================
-- 2. 区域字典（省 / 市 / 县）
-- ============================================================
CREATE TABLE IF NOT EXISTS region (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(12) NOT NULL UNIQUE,
  parent_code VARCHAR(12) NULL,
  level TINYINT NOT NULL COMMENT '1省 2市 3县',
  name VARCHAR(80) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_region_parent (parent_code),
  INDEX idx_region_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划字典，先导入项目区域，后续可扩全国';

-- ============================================================
-- 3. 农户
-- ============================================================
CREATE TABLE IF NOT EXISTS farmer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  farmer_no VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL,
  county VARCHAR(50) NOT NULL,
  county_code VARCHAR(12) NULL,
  phone VARCHAR(20) NOT NULL,
  qualification_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_farmer_county (county),
  INDEX idx_farmer_county_code (county_code),
  INDEX idx_farmer_phone (phone),
  CONSTRAINT fk_farmer_region FOREIGN KEY (county_code) REFERENCES region(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农户档案，不存身份证号和家庭住址';

-- ============================================================
-- 4. 账号
-- ============================================================
CREATE TABLE IF NOT EXISTS account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  role_id BIGINT NOT NULL,
  farmer_id BIGINT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  must_change_password TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_account_role FOREIGN KEY (role_id) REFERENCES role(id),
  CONSTRAINT fk_account_farmer FOREIGN KEY (farmer_id) REFERENCES farmer(id),
  INDEX idx_account_role (role_id),
  INDEX idx_account_farmer (farmer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录账号';

-- ============================================================
-- 4.1 农户资质材料
-- ============================================================
CREATE TABLE IF NOT EXISTS farmer_qualification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  farmer_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NULL,
  file_size BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  review_comment VARCHAR(300) NULL,
  uploaded_by BIGINT NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_qualification_farmer FOREIGN KEY (farmer_id) REFERENCES farmer(id),
  CONSTRAINT fk_qualification_uploader FOREIGN KEY (uploaded_by) REFERENCES account(id),
  INDEX idx_qualification_farmer (farmer_id, status),
  INDEX idx_qualification_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农户资质材料，文件保存在上传目录';

-- ============================================================
-- 5. 场地
-- ============================================================
CREATE TABLE IF NOT EXISTS venue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  venue_no VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  location VARCHAR(200) NOT NULL,
  county_code VARCHAR(12) NULL,
  capacity INT NOT NULL DEFAULT 0,
  open_start TIME NOT NULL,
  open_end TIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'available',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_venue_county_code (county_code),
  INDEX idx_venue_status (status),
  CONSTRAINT fk_venue_region FOREIGN KEY (county_code) REFERENCES region(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播场地';

-- ============================================================
-- 6. 设备
-- ============================================================
CREATE TABLE IF NOT EXISTS device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_no VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  model VARCHAR(80) NULL,
  config VARCHAR(500) NULL COMMENT '组合包含的设备配置',
  venue_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'available',
  calibration_date DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_device_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
  INDEX idx_device_venue (venue_id),
  INDEX idx_device_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播设备组合，必须属于一个场地';

-- ============================================================
-- 7. 预约
-- ============================================================
CREATE TABLE IF NOT EXISTS booking (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_no VARCHAR(40) NOT NULL UNIQUE,
  farmer_id BIGINT NOT NULL,
  venue_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  topic VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending_review',
  review_comment VARCHAR(300) NULL,
  recommended_time DATETIME NULL,
  cancel_reason VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_booking_farmer FOREIGN KEY (farmer_id) REFERENCES farmer(id),
  CONSTRAINT fk_booking_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
  CONSTRAINT fk_booking_device FOREIGN KEY (device_id) REFERENCES device(id),
  INDEX idx_booking_venue_time (venue_id, start_time),
  INDEX idx_booking_device_time (device_id, start_time),
  INDEX idx_booking_farmer_time (farmer_id, start_time),
  INDEX idx_booking_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播预约';

-- ============================================================
-- 8. 预约农产品
-- ============================================================
CREATE TABLE IF NOT EXISTS booking_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_id BIGINT NOT NULL,
  product_name VARCHAR(80) NOT NULL,
  spec VARCHAR(80) NULL,
  quantity DECIMAL(10,2) NOT NULL DEFAULT 0,
  unit VARCHAR(20) NOT NULL,
  CONSTRAINT fk_booking_product_booking FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE CASCADE,
  INDEX idx_booking_product_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约主推农产品';

-- ============================================================
-- 9. 资源占用：按小时锁场地和设备
-- ============================================================
CREATE TABLE IF NOT EXISTS resource_lock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  venue_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  slot_time DATETIME NOT NULL,
  booking_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_lock_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
  CONSTRAINT fk_lock_device FOREIGN KEY (device_id) REFERENCES device(id),
  CONSTRAINT fk_lock_booking FOREIGN KEY (booking_id) REFERENCES booking(id) ON DELETE CASCADE,
  UNIQUE KEY uk_lock_venue_slot (venue_id, slot_time),
  UNIQUE KEY uk_lock_device_slot (device_id, slot_time),
  INDEX idx_lock_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源占用，按小时唯一';

-- ============================================================
-- 10. 调整申请
-- ============================================================
CREATE TABLE IF NOT EXISTS adjustment_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(40) NOT NULL UNIQUE,
  booking_id BIGINT NOT NULL,
  new_venue_id BIGINT NOT NULL,
  new_device_id BIGINT NOT NULL,
  new_start_time DATETIME NOT NULL,
  new_end_time DATETIME NOT NULL,
  reason VARCHAR(300) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  review_comment VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  CONSTRAINT fk_adjust_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
  CONSTRAINT fk_adjust_venue FOREIGN KEY (new_venue_id) REFERENCES venue(id),
  CONSTRAINT fk_adjust_device FOREIGN KEY (new_device_id) REFERENCES device(id),
  INDEX idx_adjust_booking (booking_id),
  INDEX idx_adjust_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约调整申请';

-- ============================================================
-- 11. 排队
-- ============================================================
CREATE TABLE IF NOT EXISTS queue_ticket (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  queue_no VARCHAR(50) NOT NULL UNIQUE,
  farmer_id BIGINT NOT NULL,
  venue_id BIGINT NOT NULL,
  slot_start DATETIME NOT NULL,
  slot_end DATETIME NOT NULL,
  position INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'queued',
  candidate_deadline DATETIME NULL,
  assigned_device_id BIGINT NULL,
  confirmed_booking_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_queue_farmer FOREIGN KEY (farmer_id) REFERENCES farmer(id),
  CONSTRAINT fk_queue_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
  CONSTRAINT fk_queue_device FOREIGN KEY (assigned_device_id) REFERENCES device(id),
  CONSTRAINT fk_queue_booking FOREIGN KEY (confirmed_booking_id) REFERENCES booking(id),
  INDEX idx_queue_venue_slot (venue_id, slot_start, status),
  INDEX idx_queue_farmer_slot (farmer_id, slot_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按场地排队';

-- ============================================================
-- 12. 通知
-- ============================================================
CREATE TABLE IF NOT EXISTS notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  event_key VARCHAR(80) NULL COMMENT '事件关联键，同事件的双渠道记录共享',
  channel VARCHAR(20) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NULL,
  sent_at DATETIME NULL,
  read_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notification_account FOREIGN KEY (account_id) REFERENCES account(id),
  INDEX idx_notification_account (account_id, status),
  INDEX idx_notification_type (type),
  INDEX idx_notification_retry (status, next_retry_at),
  INDEX idx_notification_event (event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知，按渠道分别记状态';

-- ============================================================
-- 12.1 通知模板
-- ============================================================
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

-- ============================================================
-- 13. 直播销售记录
-- ============================================================
CREATE TABLE IF NOT EXISTS sales_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  booking_id BIGINT NOT NULL,
  product_name VARCHAR(80) NOT NULL,
  spec VARCHAR(80) NULL,
  quantity DECIMAL(10,2) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_sales_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
  CONSTRAINT fk_sales_account FOREIGN KEY (created_by) REFERENCES account(id),
  INDEX idx_sales_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播卖出农产品数量，由直播管理员录入';

-- ============================================================
-- 14. 操作日志
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NULL,
  operator_name VARCHAR(50) NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  action VARCHAR(50) NOT NULL,
  detail TEXT NULL,
  ip VARCHAR(64) NULL,
  checksum VARCHAR(128) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_account FOREIGN KEY (operator_id) REFERENCES account(id) ON DELETE SET NULL,
  INDEX idx_audit_operator (operator_id, created_at),
  INDEX idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志，只追加';

-- ============================================================
-- 15. 知识库文档
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_doc (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  file_name VARCHAR(200) NOT NULL,
  file_type VARCHAR(20) NOT NULL,
  file_size BIGINT NOT NULL,
  parse_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  enabled TINYINT NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 1,
  uploaded_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kb_account FOREIGN KEY (uploaded_by) REFERENCES account(id),
  INDEX idx_kb_enabled (enabled),
  INDEX idx_kb_parse (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识库文档';

-- ============================================================
-- 16. 问答记录
-- ============================================================
CREATE TABLE IF NOT EXISTS qa_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  question TEXT NOT NULL,
  answer TEXT NULL,
  citations JSON NULL,
  handoff TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_qa_account FOREIGN KEY (account_id) REFERENCES account(id),
  INDEX idx_qa_account (account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 问答记录';

-- ============================================================
-- 初始化数据
-- ============================================================
INSERT IGNORE INTO role (id, code, name, description) VALUES
  (1, 'system_admin', '系统管理员', '系统配置、账号角色、全量数据、操作日志'),
  (2, 'live_admin', '直播管理员', '农户资质、场地设备、预约审核、排队、通知、统计'),
  (3, 'farmer', '农户', '预约、排队、候补、通知、个人数据、AI 问答'),
  (4, 'data_viewer', '数据统计员', '查看和导出统计报表，不做业务操作');

-- 先导入项目所在区域；全国数据后续用导入脚本补充
INSERT IGNORE INTO region (code, parent_code, level, name) VALUES
  ('340000', NULL, 1, '安徽省'),
  ('340400', '340000', 2, '淮南市'),
  ('340402', '340400', 3, '大通区'),
  ('340403', '340400', 3, '田家庵区'),
  ('340404', '340400', 3, '谢家集区'),
  ('340405', '340400', 3, '八公山区'),
  ('340406', '340400', 3, '潘集区'),
  ('340421', '340400', 3, '凤台县'),
  ('340422', '340400', 3, '寿县');

-- 初始系统管理员：admin / Admin@123，首次登录必须改密
INSERT IGNORE INTO account
  (username, password_hash, display_name, role_id, farmer_id, status, must_change_password)
VALUES
  ('admin',
   'pbkdf2_sha256:120000:30a207b31f8c82af1ec60ed746c6f329:735db96d1e4de5b37812cd9d1749345a1d278d8e12224c2ca5243fbfa20196fc',
   '系统管理员',
   (SELECT id FROM role WHERE code = 'system_admin'),
   NULL, 'active', 1);

INSERT IGNORE INTO farmer
  (id, farmer_no, name, county, county_code, phone, qualification_status, status)
VALUES
  (1, 'NF2026001', '张农户', '寿县', '340422', '13800000001', 'verified', 'active'),
  (2, 'NF2026002', '李农户', '寿县', '340422', '13800000002', 'pending', 'active');

INSERT IGNORE INTO venue
  (id, venue_no, name, location, county_code, capacity, open_start, open_end, status)
VALUES
  (1, 'C001', '一号直播间', '寿县电商中心一楼', '340422', 20, '08:00:00', '20:00:00', 'available'),
  (2, 'C002', '二号直播间', '寿县电商中心二楼', '340422', 15, '09:00:00', '18:00:00', 'available');

INSERT IGNORE INTO device
  (id, device_no, name, model, config, venue_id, status, calibration_date)
VALUES
  (1, 'ZB-C001-01', '设备组合1', '手机直播组合',
   '手机 + 三脚架 + 双补光灯 + 无线领夹麦 + 5G 路由 + 插排', 1, 'available', '2026-12-31'),
  (2, 'ZB-C001-02', '设备组合2', '补光增强组合',
   '手机 + 三脚架 + 三补光灯 + 无线领夹麦 + 5G 路由 + UPS', 1, 'available', '2026-12-31'),
  (3, 'ZB-C002-01', '设备组合3', '基础直播组合',
   '手机 + 三脚架 + 双补光灯 + 无线领夹麦 + 场地宽带 + 插排', 2, 'available', '2026-12-31');

-- 完成
SELECT 'farm_live init done' AS result;
