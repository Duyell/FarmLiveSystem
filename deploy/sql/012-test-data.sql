-- ============================================================
-- 012: 测试数据种子（幂等，可重复执行）
-- 覆盖：两农户各状态预约、排队、销售记录；清理脏账号
-- ============================================================
USE farm_live;

-- 1. 清理脏账号（role=n 异常数据）
DELETE FROM notification WHERE account_id IN (SELECT id FROM account WHERE username = 't');
DELETE FROM qa_record WHERE account_id IN (SELECT id FROM account WHERE username = 't');
DELETE FROM account WHERE username = 't' AND role_id NOT IN (SELECT id FROM role WHERE code IN ('system_admin','live_admin','farmer','data_viewer'));

-- 2. 农户 234 (farmer_id=1) 的预约
INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, review_comment, recommended_time, cancel_reason)
SELECT 'BKTEST01', 1, 1, 1, DATE_ADD(CURDATE(), INTERVAL 3 DAY) + INTERVAL 9 HOUR, DATE_ADD(CURDATE(), INTERVAL 3 DAY) + INTERVAL 11 HOUR,
       '寿县草莓采摘专场', 'pending_review', NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST01');

INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, review_comment)
SELECT 'BKTEST02', 1, 2, 3, DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 10 HOUR, DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 12 HOUR,
       '电商助农水果节', 'approved', '时段合理，通过'
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST02');

INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, review_comment, recommended_time)
SELECT 'BKTEST03', 1, 1, 1, DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 10 HOUR, DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 12 HOUR,
       '晚稻新品发布会', 'rejected', '目标时段场地设备已被占用，建议改约推荐时段', DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 14 HOUR
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST03');

INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, cancel_reason)
SELECT 'BKTEST04', 1, 2, 3, DATE_ADD(CURDATE(), INTERVAL 6 DAY) + INTERVAL 14 HOUR, DATE_ADD(CURDATE(), INTERVAL 6 DAY) + INTERVAL 16 HOUR,
       '土鸡蛋团购专场', 'canceled', '农产品临时缺货'
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST04');

INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, review_comment)
SELECT 'BKTEST05', 1, 1, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY) + INTERVAL 10 HOUR, DATE_SUB(CURDATE(), INTERVAL 1 DAY) + INTERVAL 12 HOUR,
       '草莓鲜果直供专场', 'completed', '直播顺利，通过'
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST05');

-- 3. 农户 123 (farmer_id=2) 的预约
INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, review_comment)
SELECT 'BKTEST06', 2, 1, 2, DATE_ADD(CURDATE(), INTERVAL 4 DAY) + INTERVAL 14 HOUR, DATE_ADD(CURDATE(), INTERVAL 4 DAY) + INTERVAL 16 HOUR,
       '圣女果大棚直击', 'approved', '资质齐全，通过'
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST06');

INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status, review_comment)
SELECT 'BKTEST07', 2, 2, 3, DATE_SUB(CURDATE(), INTERVAL 2 DAY) + INTERVAL 14 HOUR, DATE_SUB(CURDATE(), INTERVAL 2 DAY) + INTERVAL 16 HOUR,
       '寿县大米品鉴会', 'completed', '完成'
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST07');

INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status)
SELECT 'BKTEST08', 2, 2, 3, DATE_ADD(CURDATE(), INTERVAL 5 DAY) + INTERVAL 9 HOUR, DATE_ADD(CURDATE(), INTERVAL 5 DAY) + INTERVAL 11 HOUR,
       '农家土猪肉腊肉专场', 'pending_review'
WHERE NOT EXISTS (SELECT 1 FROM booking WHERE booking_no = 'BKTEST08');

-- 4. 已通过预约的资源占用锁（每小时一锁）
INSERT INTO resource_lock (venue_id, device_id, slot_time, booking_id)
SELECT b.venue_id, b.device_id, TIMESTAMP(DATE(b.start_time), TIME(b.start_time) + INTERVAL n HOUR), b.id
FROM booking b
JOIN (SELECT 0 AS n UNION SELECT 1) slots ON TIMESTAMP(DATE(b.start_time), TIME(b.start_time) + INTERVAL slots.n HOUR) < b.end_time
WHERE b.booking_no = 'BKTEST02'
  AND NOT EXISTS (SELECT 1 FROM resource_lock rl WHERE rl.booking_id = b.id);

-- 5. 预约主推农产品
INSERT INTO booking_product (booking_id, product_name, spec, quantity, unit)
SELECT b.id, '草莓', '500g/盒', 200, '盒' FROM booking b
WHERE b.booking_no = 'BKTEST01' AND NOT EXISTS (SELECT 1 FROM booking_product bp WHERE bp.booking_id = b.id);

INSERT INTO booking_product (booking_id, product_name, spec, quantity, unit)
SELECT b.id, '红心猕猴桃', '3kg/箱', 80, '箱' FROM booking b
WHERE b.booking_no = 'BKTEST02' AND NOT EXISTS (SELECT 1 FROM booking_product bp WHERE bp.booking_id = b.id);

INSERT INTO booking_product (booking_id, product_name, spec, quantity, unit)
SELECT b.id, '晚稻新米', '5kg/袋', 150, '袋' FROM booking b
WHERE b.booking_no = 'BKTEST07' AND NOT EXISTS (SELECT 1 FROM booking_product bp WHERE bp.booking_id = b.id);

-- 6. 排队（两农户同一时段同一场地，模拟资源冲突排队）
INSERT INTO queue_ticket (queue_no, farmer_id, venue_id, slot_start, slot_end, position, status)
SELECT CONCAT('Q-1-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '%Y%m%d'), '-01'), 1, 1,
       DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 14 HOUR, DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 16 HOUR, 1, 'queued'
WHERE NOT EXISTS (SELECT 1 FROM queue_ticket WHERE queue_no = CONCAT('Q-1-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '%Y%m%d'), '-01'));

INSERT INTO queue_ticket (queue_no, farmer_id, venue_id, slot_start, slot_end, position, status)
SELECT CONCAT('Q-2-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '%Y%m%d'), '-02'), 2, 1,
       DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 14 HOUR, DATE_ADD(CURDATE(), INTERVAL 2 DAY) + INTERVAL 16 HOUR, 2, 'queued'
WHERE NOT EXISTS (SELECT 1 FROM queue_ticket WHERE queue_no = CONCAT('Q-2-', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '%Y%m%d'), '-02'));

-- 7. 已完成场次销售记录（created_by=admin id=1）
INSERT INTO sales_record (booking_id, product_name, spec, quantity, unit, created_by)
SELECT b.id, '奶油草莓', '500g/盒', 156, '盒', 1 FROM booking b
WHERE b.booking_no = 'BKTEST05' AND NOT EXISTS (SELECT 1 FROM sales_record sr WHERE sr.booking_id = b.id AND sr.product_name = '奶油草莓');

INSERT INTO sales_record (booking_id, product_name, spec, quantity, unit, created_by)
SELECT b.id, '草莓礼盒', '3kg/箱', 42, '箱', 1 FROM booking b
WHERE b.booking_no = 'BKTEST05' AND NOT EXISTS (SELECT 1 FROM sales_record sr WHERE sr.booking_id = b.id AND sr.product_name = '草莓礼盒');

INSERT INTO sales_record (booking_id, product_name, spec, quantity, unit, created_by)
SELECT b.id, '寿县晚粳米', '5kg/袋', 210, '袋', 1 FROM booking b
WHERE b.booking_no = 'BKTEST07' AND NOT EXISTS (SELECT 1 FROM sales_record sr WHERE sr.booking_id = b.id);
