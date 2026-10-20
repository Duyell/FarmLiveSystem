-- 006 核心缺口：农户资质材料表（只执行一次）
-- 适用于已经执行过 init.sql 的现有开发库。
-- 新环境直接执行最新 init.sql，不需要再执行本文件。

USE farm_live;

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

SELECT 'farmer qualification migration done' AS result;
