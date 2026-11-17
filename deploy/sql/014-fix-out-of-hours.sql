-- ============================================================
-- 014: 收敛存量越界预约（幂等）
-- 排查 SQL：
-- SELECT b.id, b.booking_no, b.status, b.start_time, b.end_time, v.venue_no, v.open_start, v.open_end
-- FROM booking b JOIN venue v ON v.id = b.venue_id
-- WHERE TIME(b.start_time) < v.open_start OR TIME(b.end_time) > v.open_end;
-- ============================================================
USE farm_live;

-- 1) BK20261116204600236：C001 开放 08:00-20:00，原预约 07:00-11:00，收敛开始到 08:00
UPDATE booking
SET start_time = '2026-11-17 08:00:00'
WHERE booking_no = 'BK20261116204600236'
  AND TIME(start_time) < '08:00:00';

-- 同步删除 07:00 的过期资源锁
DELETE FROM resource_lock
WHERE booking_id = (SELECT id FROM booking WHERE booking_no = 'BK20261116204600236')
  AND slot_time = '2026-11-17 07:00:00';

-- 2) BK20261116234003212：C002 开放 09:00-18:00，原预约 20:00-23:00，整体迁到 14:00-17:00
UPDATE booking
SET start_time = '2026-11-17 14:00:00',
    end_time   = '2026-11-17 17:00:00'
WHERE booking_no = 'BK20261116234003212'
  AND TIME(start_time) >= '18:00:00';

-- 复查：应返回 0 行
-- SELECT b.id FROM booking b JOIN venue v ON v.id=b.venue_id
-- WHERE TIME(b.start_time) < v.open_start OR TIME(b.end_time) > v.open_end;
