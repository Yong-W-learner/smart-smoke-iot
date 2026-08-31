-- 森林景区防火答辩演示数据（可重复执行）
-- device_id=1 保留为华为云小熊派真机；2~6 为仿真设备。
USE smoke_db;

INSERT INTO sys_user (username, role, password, job_num, phone)
VALUES ('ranger', 'ranger', 'ranger123', 'G0001', '13800000001')
ON DUPLICATE KEY UPDATE role = VALUES(role), job_num = VALUES(job_num), phone = VALUES(phone);

INSERT INTO device (device_id, building, floor, room, status, user_id) VALUES
    (2, 1, 2, 1, 1, NULL),
    (3, 2, 6, 2, 1, NULL),
    (4, 4, 1, 3, 0, NULL),
    (5, 5, 9, 1, 1, NULL),
    (6, 3, 9, 2, 1, NULL)
ON DUPLICATE KEY UPDATE
    building = VALUES(building), floor = VALUES(floor), room = VALUES(room), status = VALUES(status);

-- 设备2：稳定正常
INSERT INTO smoke_record (device_id, collect_time, smoke_concentration, alarm)
SELECT x.device_id, x.collect_time, x.smoke_concentration, x.alarm
FROM (
    SELECT 2 device_id, NOW() - INTERVAL 14 MINUTE collect_time, 12.4 smoke_concentration, 0 alarm UNION ALL
    SELECT 2, NOW() - INTERVAL 12 MINUTE, 13.1, 0 UNION ALL
    SELECT 2, NOW() - INTERVAL 10 MINUTE, 12.8, 0 UNION ALL
    SELECT 2, NOW() - INTERVAL 8 MINUTE, 14.0, 0 UNION ALL
    SELECT 2, NOW() - INTERVAL 6 MINUTE, 13.5, 0 UNION ALL
    SELECT 2, NOW() - INTERVAL 4 MINUTE, 12.9, 0 UNION ALL
    SELECT 2, NOW() - INTERVAL 2 MINUTE, 13.2, 0
) x
WHERE NOT EXISTS (SELECT 1 FROM smoke_record r WHERE r.device_id = 2);

-- 设备3：昨日厨房油烟导致一级警情，随后恢复
INSERT INTO smoke_record (device_id, collect_time, smoke_concentration, alarm)
SELECT x.device_id, x.collect_time, x.smoke_concentration, x.alarm
FROM (
    SELECT 3 device_id, NOW() - INTERVAL 1 DAY - INTERVAL 18 MINUTE collect_time, 18.0 smoke_concentration, 0 alarm UNION ALL
    SELECT 3, NOW() - INTERVAL 1 DAY - INTERVAL 16 MINUTE, 36.0, 0 UNION ALL
    SELECT 3, NOW() - INTERVAL 1 DAY - INTERVAL 14 MINUTE, 54.0, 1 UNION ALL
    SELECT 3, NOW() - INTERVAL 1 DAY - INTERVAL 12 MINUTE, 63.5, 1 UNION ALL
    SELECT 3, NOW() - INTERVAL 1 DAY - INTERVAL 10 MINUTE, 58.2, 1 UNION ALL
    SELECT 3, NOW() - INTERVAL 1 DAY - INTERVAL 8 MINUTE, 41.0, 0 UNION ALL
    SELECT 3, NOW() - INTERVAL 3 MINUTE, 15.6, 0
) x
WHERE NOT EXISTS (SELECT 1 FROM smoke_record r WHERE r.device_id = 3);

-- 设备4：离线设备，最后数据停留在两小时前
INSERT INTO smoke_record (device_id, collect_time, smoke_concentration, alarm)
SELECT x.device_id, x.collect_time, x.smoke_concentration, x.alarm
FROM (
    SELECT 4 device_id, NOW() - INTERVAL 130 MINUTE collect_time, 16.2 smoke_concentration, 0 alarm UNION ALL
    SELECT 4, NOW() - INTERVAL 125 MINUTE, 16.8, 0 UNION ALL
    SELECT 4, NOW() - INTERVAL 120 MINUTE, 17.1, 0
) x
WHERE NOT EXISTS (SELECT 1 FROM smoke_record r WHERE r.device_id = 4);

-- 设备5：当前三级高危警情，浓度持续快速上升
INSERT INTO smoke_record (device_id, collect_time, smoke_concentration, alarm)
SELECT x.device_id, x.collect_time, x.smoke_concentration, x.alarm
FROM (
    SELECT 5 device_id, NOW() - INTERVAL 14 MINUTE collect_time, 22.0 smoke_concentration, 0 alarm UNION ALL
    SELECT 5, NOW() - INTERVAL 12 MINUTE, 48.0, 0 UNION ALL
    SELECT 5, NOW() - INTERVAL 10 MINUTE, 57.0, 1 UNION ALL
    SELECT 5, NOW() - INTERVAL 8 MINUTE, 74.0, 2 UNION ALL
    SELECT 5, NOW() - INTERVAL 6 MINUTE, 93.0, 3 UNION ALL
    SELECT 5, NOW() - INTERVAL 4 MINUTE, 106.0, 3 UNION ALL
    SELECT 5, NOW() - INTERVAL 2 MINUTE, 112.5, 3
) x
WHERE NOT EXISTS (SELECT 1 FROM smoke_record r WHERE r.device_id = 5);

-- 设备6：当前二级警情，用于演示同一楼栋的关联风险
INSERT INTO smoke_record (device_id, collect_time, smoke_concentration, alarm)
SELECT x.device_id, x.collect_time, x.smoke_concentration, x.alarm
FROM (
    SELECT 6 device_id, NOW() - INTERVAL 12 MINUTE collect_time, 20.0 smoke_concentration, 0 alarm UNION ALL
    SELECT 6, NOW() - INTERVAL 10 MINUTE, 39.0, 0 UNION ALL
    SELECT 6, NOW() - INTERVAL 8 MINUTE, 53.0, 1 UNION ALL
    SELECT 6, NOW() - INTERVAL 6 MINUTE, 68.0, 1 UNION ALL
    SELECT 6, NOW() - INTERVAL 4 MINUTE, 76.0, 2 UNION ALL
    SELECT 6, NOW() - INTERVAL 2 MINUTE, 81.5, 2
) x
WHERE NOT EXISTS (SELECT 1 FROM smoke_record r WHERE r.device_id = 6);

-- 独立警情事件：两起待处置、一起已处置。每台仿真设备只初始化一次。
INSERT INTO alarm (device_id, alarm_time, location, status)
SELECT 5, NOW() - INTERVAL 10 MINUTE, '5栋 9层 1户', 0
WHERE NOT EXISTS (SELECT 1 FROM alarm WHERE device_id = 5);

INSERT INTO alarm (device_id, alarm_time, location, status)
SELECT 6, NOW() - INTERVAL 8 MINUTE, '3栋 9层 2户', 0
WHERE NOT EXISTS (SELECT 1 FROM alarm WHERE device_id = 6);

INSERT INTO alarm (device_id, alarm_time, location, status)
SELECT 3, NOW() - INTERVAL 1 DAY - INTERVAL 14 MINUTE, '2栋 6层 2户', 1
WHERE NOT EXISTS (SELECT 1 FROM alarm WHERE device_id = 3);

-- 兼容旧版由管理员操作到场的演示记录：回退到已确认，交由应急消防员重新确认到场。
UPDATE alarm
SET process_status = 'confirmed',
    confirmer_id = handler_id,
    confirmer_name = handler_name,
    confirm_time = COALESCE(response_time, alarm_time + INTERVAL 1 MINUTE),
    handler_id = NULL,
    handler_name = NULL,
    arrival_time = NULL
WHERE status = 0 AND process_status IN ('responding', 'arrived');

-- 已处置警情补充完整流程：管理员确认，应急消防员到场并上报。
UPDATE alarm
SET process_status = 'handled',
    confirmer_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1),
    confirmer_name = 'admin',
    confirm_time = alarm_time + INTERVAL 1 MINUTE,
    handler_id = (SELECT id FROM sys_user WHERE username = 'firefighter' LIMIT 1),
    handler_name = 'firefighter',
    response_time = alarm_time + INTERVAL 1 MINUTE,
    arrival_time = alarm_time + INTERVAL 6 MINUTE,
    handle_time = alarm_time + INTERVAL 12 MINUTE,
    handle_result = '生活烟雾',
    handle_remark = '现场确认为厨房烹饪油烟，已开窗通风并确认设备恢复正常。'
WHERE device_id = 3 AND status = 1;
