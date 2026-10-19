-- 002 区域字典扩展（只执行一次）
-- 适用于已经执行过 init.sql 的现有开发库。
-- 新环境直接执行 init.sql，不需要再执行本文件。

USE farm_live;

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

ALTER TABLE farmer
  ADD COLUMN county_code VARCHAR(12) NULL AFTER county,
  ADD INDEX idx_farmer_county_code (county_code),
  ADD CONSTRAINT fk_farmer_region FOREIGN KEY (county_code) REFERENCES region(code);

ALTER TABLE venue
  ADD COLUMN county_code VARCHAR(12) NULL AFTER location,
  ADD INDEX idx_venue_county_code (county_code),
  ADD CONSTRAINT fk_venue_region FOREIGN KEY (county_code) REFERENCES region(code);

UPDATE farmer SET county_code = '340422' WHERE county = '寿县' AND county_code IS NULL;
UPDATE venue SET county_code = '340422' WHERE location LIKE '寿县%' AND county_code IS NULL;

SELECT 'region migration done' AS result;
