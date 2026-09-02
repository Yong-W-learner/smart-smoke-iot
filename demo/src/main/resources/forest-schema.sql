CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  role VARCHAR(16) NOT NULL DEFAULT 'ranger',
  phone VARCHAR(20) NULL, job_num VARCHAR(32) NULL,
  password VARCHAR(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_camera_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  sensor_id VARCHAR(32) NULL,
  zone VARCHAR(100) NULL,
  level VARCHAR(16) NULL,
  smoke_concentration DOUBLE NULL,
  image_base64 LONGTEXT NOT NULL,
  create_time DATETIME NOT NULL,
  ai_verdict VARCHAR(16) NULL,
  ai_basis VARCHAR(1000) NULL,
  ai_detections LONGTEXT NULL,
  ai_boxes LONGTEXT NULL,
  KEY idx_forest_camera_review_time (create_time),
  KEY idx_forest_camera_review_sensor (sensor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_park (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  area DOUBLE NOT NULL,
  ancient_trees INT NOT NULL,
  visitors INT NOT NULL,
  fire_risk VARCHAR(16) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_zone (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(50) NOT NULL,
  risk VARCHAR(16) NOT NULL,
  map_x DOUBLE NOT NULL,
  map_y DOUBLE NOT NULL,
  trees INT NOT NULL DEFAULT 0,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  geojson LONGTEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_sensor_node (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  zone VARCHAR(100) NOT NULL,
  smoke DOUBLE NULL,
  temperature DOUBLE NULL,
  humidity DOUBLE NULL,
  co DOUBLE NULL,
  online TINYINT(1) NOT NULL DEFAULT 1,
  source VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  camera VARCHAR(32) NULL,
  map_x DOUBLE NOT NULL,
  map_y DOUBLE NOT NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_sensor_reading (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  sensor_id VARCHAR(32) NOT NULL,
  collect_time DATETIME NOT NULL,
  smoke DOUBLE NULL,
  temperature DOUBLE NULL,
  humidity DOUBLE NULL,
  co DOUBLE NULL,
  source VARCHAR(16) NOT NULL,
  KEY idx_forest_sensor_reading_sensor_time (sensor_id, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_drone (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  model VARCHAR(160) NOT NULL,
  battery DOUBLE NOT NULL,
  status VARCHAR(20) NOT NULL,
  location VARCHAR(120) NOT NULL,
  thermal TINYINT(1) NOT NULL DEFAULT 1,
  operator_name VARCHAR(64) NULL,
  payloads VARCHAR(300) NULL,
  map_x DOUBLE NOT NULL,
  map_y DOUBLE NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  altitude DOUBLE NOT NULL DEFAULT 0,
  speed DOUBLE NOT NULL DEFAULT 0,
  satellites INT NOT NULL DEFAULT 0,
  link_quality INT NOT NULL DEFAULT 0,
  eta_sec INT NOT NULL DEFAULT 0,
  phase VARCHAR(32) NOT NULL,
  pm25 DOUBLE NULL,
  temperature DOUBLE NULL,
  humidity DOUBLE NULL,
  co DOUBLE NULL,
  surface_temperature DOUBLE NULL,
  wind_estimate DOUBLE NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drone_mission (
  id VARCHAR(40) NOT NULL PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  route VARCHAR(500) NOT NULL,
  ranger VARCHAR(64) NOT NULL,
  drone VARCHAR(100) NOT NULL,
  mode VARCHAR(16) NOT NULL DEFAULT 'manual',
  status VARCHAR(20) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  plan_time VARCHAR(32) NULL,
  coverage VARCHAR(32) NULL,
  images INT NOT NULL DEFAULT 0,
  hotspots INT NOT NULL DEFAULT 0,
  samples INT NOT NULL DEFAULT 0,
  eta_sec INT NOT NULL DEFAULT 0,
  max_temperature DOUBLE NULL,
  report VARCHAR(1000) NULL,
  summary_source VARCHAR(20) NOT NULL DEFAULT 'rules',
  ranger_latitude DOUBLE NULL,
  ranger_longitude DOUBLE NULL,
  drone_latitude DOUBLE NULL,
  drone_longitude DOUBLE NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drone_telemetry (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  mission_id VARCHAR(40) NOT NULL,
  drone_id VARCHAR(32) NOT NULL,
  collect_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  altitude DOUBLE NOT NULL,
  speed DOUBLE NOT NULL,
  battery DOUBLE NOT NULL,
  satellites INT NOT NULL,
  link_quality INT NOT NULL,
  pm25 DOUBLE NULL,
  temperature DOUBLE NULL,
  humidity DOUBLE NULL,
  co DOUBLE NULL,
  surface_temperature DOUBLE NULL,
  wind_estimate DOUBLE NULL,
  KEY idx_mission_time (mission_id, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drone_mission_photo (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  mission_id VARCHAR(40) NOT NULL,
  category VARCHAR(32) NOT NULL,
  zone_name VARCHAR(100) NULL,
  tree_code VARCHAR(64) NULL,
  tags VARCHAR(500) NULL,
  image_data LONGTEXT NULL,
  object_url VARCHAR(1000) NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  surface_temperature DOUBLE NULL,
  note VARCHAR(1000) NULL,
  captured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mission_photo(mission_id,category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS drone_mission_incident (
  mission_id VARCHAR(40) NOT NULL PRIMARY KEY,
  incident_id VARCHAR(40) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_mission_incident_incident (incident_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_incident (
  id VARCHAR(40) NOT NULL PRIMARY KEY,
  event_time DATETIME NOT NULL,
  zone VARCHAR(100) NOT NULL,
  level VARCHAR(16) NOT NULL,
  source VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL,
  smoke DOUBLE NULL,
  temperature DOUBLE NULL,
  confidence INT NULL,
  analysis_model VARCHAR(80) NULL,
  analysis_detail VARCHAR(1000) NULL,
  reason VARCHAR(600) NULL,
  result VARCHAR(1000) NULL,
  ranger VARCHAR(64) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_broadcast (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  level VARCHAR(20) NOT NULL,
  title VARCHAR(160) NOT NULL,
  message VARCHAR(1000) NOT NULL,
  area VARCHAR(500) NOT NULL,
  exit_name VARCHAR(160) NOT NULL,
  publish_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_trend_point (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  metric VARCHAR(30) NOT NULL,
  point_label VARCHAR(20) NOT NULL,
  value DOUBLE NOT NULL,
  sort_no INT NOT NULL,
  KEY idx_metric_sort (metric, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_equipment (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(40) NOT NULL,
  location VARCHAR(160) NOT NULL,
  source VARCHAR(20) NOT NULL,
  last_maintain DATE NULL,
  status VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_equipment_fault (
  id VARCHAR(40) NOT NULL PRIMARY KEY,
  device_id VARCHAR(32) NOT NULL,
  device_name VARCHAR(160) NOT NULL,
  location VARCHAR(160) NOT NULL,
  fault_type VARCHAR(100) NOT NULL,
  fault_time DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_equipment_self_test (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  test_no VARCHAR(48) NOT NULL UNIQUE,
  device_id VARCHAR(32) NOT NULL,
  device_name VARCHAR(160) NOT NULL,
  device_type VARCHAR(40) NOT NULL,
  source VARCHAR(20) NOT NULL DEFAULT 'simulated',
  status VARCHAR(20) NOT NULL DEFAULT 'running',
  request_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  complete_time DATETIME NULL,
  result_json LONGTEXT NULL,
  summary VARCHAR(500) NULL,
  KEY idx_self_test_device_time(device_id,request_time),
  KEY idx_self_test_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_maintenance_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  record_time DATETIME NOT NULL,
  device_id VARCHAR(32) NOT NULL,
  device_name VARCHAR(160) NOT NULL,
  device_type VARCHAR(40) NOT NULL,
  fault VARCHAR(160) NOT NULL,
  result VARCHAR(1000) NOT NULL,
  operator_name VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS forest_map (
  id BIGINT NOT NULL PRIMARY KEY,
  geojson LONGTEXT NULL,
  west DOUBLE NULL,
  south DOUBLE NULL,
  east DOUBLE NULL,
  north DOUBLE NULL,
  center_lng DOUBLE NULL,
  center_lat DOUBLE NULL,
  zoom INT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ranger_position (
  ranger VARCHAR(64) NOT NULL PRIMARY KEY,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  altitude DOUBLE NULL,
  accuracy DOUBLE NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_user(username,role,password,job_num,phone) VALUES('ranger','ranger','ranger123','G0001','13800000001') ON DUPLICATE KEY UPDATE role=VALUES(role);
INSERT INTO forest_map(id,west,south,east,north,center_lng,center_lat,zoom) VALUES(1,119.273250,26.149579,119.299250,26.173579,119.286250,26.161579,15) ON DUPLICATE KEY UPDATE id=id;
INSERT INTO ranger_position(ranger,latitude,longitude,altitude) VALUES('林海',26.16158,119.28625,0),('周岚',26.16400,119.29000,0) ON DUPLICATE KEY UPDATE ranger=ranger;
INSERT INTO forest_park VALUES(1,'福州国家森林公园',5.2,186,428,'橙色') ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT INTO forest_zone(id,name,type,risk,map_x,map_y,trees) VALUES
(1,'千年银杏古树区','古树核心区','高',24,30,68),(2,'香樟古树群','古树保护区','中',58,24,74),(3,'游客服务中心','游客活动区','低',46,68,0),(4,'无火露营区','游客活动区','中',72,58,0),(5,'南坡生态林','巡护林区','高',78,34,44)
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO forest_sensor_node(id,name,zone,smoke,temperature,humidity,co,online,source,status,camera,map_x,map_y) VALUES
('GT-01','银杏区主节点','千年银杏古树区',82.6,36.8,31,28,1,'real','alarm','CAM-01',14,23),
('GT-02','银杏区北侧节点','千年银杏古树区',41.2,32.4,34,12,1,'simulated','warning','CAM-01',28,14),
('GT-03','香樟区东侧节点','香樟古树群',14.8,29.7,46,4,1,'simulated','normal','CAM-02',57,22),
('GT-04','香樟区西侧节点','香樟古树群',16.3,30.1,45,5,1,'simulated','normal','CAM-02',40,30),
('GT-05','露营区边界节点','无火露营区',25.6,31.5,41,8,1,'simulated','normal','CAM-03',77,70),
('GT-06','南坡巡护节点','南坡生态林',NULL,NULL,NULL,NULL,0,'simulated','offline',NULL,84,22)
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO forest_drone(id,name,model,battery,status,location,thermal,operator_name,payloads,map_x,map_y,latitude,longitude,altitude,speed,satellites,link_quality,eta_sec,phase,pm25,temperature,humidity,co,surface_temperature,wind_estimate) VALUES
('UAV-01','云巡一号','M300 RTK + H20T + 环境采样载荷',86,'flying','银杏区北侧上空',1,'林海','可见光,辐射测温热成像,PM2.5,温湿度,CO',59,38,26.164459,119.288590,86,8.4,18,92,480,'航线巡检',36.8,32.7,38,6,42.8,3.4),
('UAV-02','云巡二号','Mavic 3T 轻型热成像巡检机',74,'idle','游客中心起降坪',1,'周岚','可见光,热成像',49,79,26.154619,119.285990,0,0,16,100,0,'地面待命',NULL,NULL,NULL,NULL,NULL,NULL)
ON DUPLICATE KEY UPDATE name=VALUES(name),model=VALUES(model);
INSERT INTO drone_mission(id,name,route,ranger,drone,status,progress,plan_time,coverage,images,hotspots,samples,eta_sec) VALUES
('PATROL-0830-01','古树核心区午后热源巡查','护林员现场操控 → 银杏区、香樟区 → 返回起降点','林海','云巡一号','待执行',0,'15:00','待计算',0,0,0,0),
('PATROL-0830-02','游客活动区违规用火巡查','游客中心 → 露营区 → 南坡入口','周岚','云巡二号','待执行',0,'16:30','待计算',0,0,0,0),
('PATROL-0829-04','西线闭园前巡查','西门 → 银杏区 → 北侧巡护站','林海','云巡一号','已完成',100,'17:20','1.6 km²',42,0,96,0)
ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT INTO forest_incident(id,event_time,zone,level,source,status,smoke,temperature,confidence,reason,result,ranger) VALUES
('FIRE-20260830-001','2026-08-30 14:26:18','千年银杏古树区','三级','多传感器融合','pending',82.6,36.8,88,'烟雾持续上升，CO同步升高，固定摄像头发现疑似烟羽','',NULL),
('FIRE-20260829-003','2026-08-29 16:42:03','无火露营区','二级','摄像头AI','closed',38.2,30.6,74,'画面发现局部白烟','游客使用卡式炉，护林员已劝阻并完成安全检查','周岚'),
('FIRE-20260828-002','2026-08-28 10:18:46','香樟古树群','一级','护林员上报','closed',22.4,29.8,51,'林区出现短时烟雾','确认为晨雾与逆光干扰，无火情','林海')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO forest_broadcast(id,level,title,message,area,exit_name,publish_time) VALUES(1,'warning','橙色火险提示：全园禁止野外用火','古树保护区空气干燥、风力较大，请勿携带火种进入林区。当前未确认真实火情。','千年银杏古树区及周边步道','游客中心集结点 / 东侧安全出口','2026-08-30 14:30:00') ON DUPLICATE KEY UPDATE id=id;
INSERT INTO forest_equipment VALUES
('GT-01','银杏区主节点','传感节点','千年银杏古树区','真机','2026-08-22','online'),('GT-02','银杏区北侧节点','传感节点','千年银杏古树区','仿真','2026-08-18','online'),('GT-06','南坡巡护节点','传感节点','南坡生态林','仿真','2026-07-29','offline'),('CAM-01','银杏区球机','固定摄像头','银杏区南侧瞭望杆','真机','2026-08-12','online'),('CAST-01','古树区广播A组','广播设备','两处古树保护区','仿真','2026-08-08','online'),('UAV-01','云巡一号','无人机','北侧巡护站','仿真','2026-08-25','warning')
ON DUPLICATE KEY UPDATE name=VALUES(name);
INSERT INTO forest_equipment_fault VALUES
('FAULT-0830-01','GT-06','南坡巡护节点 GT-06','南坡生态林','通信离线','2026-08-30 13:42:00','pending'),('FAULT-0830-02','UAV-01','云巡一号 UAV-01','北侧巡护站','电池健康度下降','2026-08-30 09:18:00','accepted'),('FAULT-0829-04','CAM-01','银杏区球机 CAM-01','银杏区南侧瞭望杆','画面短时中断','2026-08-29 16:22:00','closed')
ON DUPLICATE KEY UPDATE device_name=VALUES(device_name);
INSERT INTO forest_maintenance_record(id,record_time,device_id,device_name,device_type,fault,result,operator_name) VALUES
(1,'2026-08-29 17:05:00','CAM-01','银杏区球机 CAM-01','摄像头','网络接口松动','护林员现场重新固定接口，连续回传测试正常','护林员·林海'),
(2,'2026-08-25 11:30:00','UAV-01','云巡一号 UAV-01','无人机','桨叶磨损','更换备用桨叶，飞行自检通过','护林员·周岚'),
(3,'2026-08-22 15:16:00','GT-01','银杏区主节点 GT-01','传感节点','烟雾探头积灰','清洁探头并完成基线校准','护林员·林海')
ON DUPLICATE KEY UPDATE result=VALUES(result);
INSERT INTO forest_trend_point(metric,point_label,value,sort_no)
SELECT * FROM (SELECT 'smoke','08:00',16.2,1 UNION ALL SELECT 'smoke','11:00',18.4,2 UNION ALL SELECT 'smoke','14:00',28.3,3 UNION ALL SELECT 'smoke','17:00',42.8,4 UNION ALL SELECT 'smoke','20:00',58.6,5 UNION ALL SELECT 'smoke','23:00',82.6,6 UNION ALL SELECT 'temperature','08:00',25.8,1 UNION ALL SELECT 'temperature','11:00',28.4,2 UNION ALL SELECT 'temperature','14:00',31.2,3 UNION ALL SELECT 'temperature','17:00',34.1,4 UNION ALL SELECT 'temperature','20:00',35.2,5 UNION ALL SELECT 'temperature','23:00',36.8,6 UNION ALL SELECT 'humidity','08:00',52,1 UNION ALL SELECT 'humidity','11:00',48,2 UNION ALL SELECT 'humidity','14:00',43,3 UNION ALL SELECT 'humidity','17:00',38,4 UNION ALL SELECT 'humidity','20:00',34,5 UNION ALL SELECT 'humidity','23:00',31,6) seed
WHERE NOT EXISTS (SELECT 1 FROM forest_trend_point LIMIT 1);

-- Older demo data was once imported with the wrong client encoding. Repair only once;
-- later user operations are never overwritten on application restart.
CREATE TABLE IF NOT EXISTS app_data_migration (
  migration_key VARCHAR(100) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
UPDATE forest_park SET fire_risk='橙色' WHERE id=1 AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v1');
UPDATE forest_zone SET type=CASE id WHEN 1 THEN '古树核心区' WHEN 2 THEN '古树保护区' WHEN 3 THEN '游客活动区' WHEN 4 THEN '游客活动区' WHEN 5 THEN '巡护林区' END WHERE id BETWEEN 1 AND 5 AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v1');
UPDATE forest_drone SET location=CASE id WHEN 'UAV-01' THEN '银杏区北侧上空' ELSE '游客中心起降坪' END,operator_name=CASE id WHEN 'UAV-01' THEN '林海' ELSE '周岚' END,payloads=CASE id WHEN 'UAV-01' THEN '可见光,辐射测温热成像,PM2.5,温湿度,CO' ELSE '可见光,热成像' END,phase=CASE id WHEN 'UAV-01' THEN '航线巡检' ELSE '地面待命' END WHERE id IN ('UAV-01','UAV-02') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v1');
UPDATE drone_mission SET route=CASE id WHEN 'PATROL-0830-01' THEN '护林员现场操控 → 银杏区、香樟区 → 返回起降点' WHEN 'PATROL-0830-02' THEN '护林员现场操控 → 露营区 → 返回起降点' ELSE '护林员现场操控 → 西线林区 → 返回起降点' END,ranger=CASE id WHEN 'PATROL-0830-02' THEN '周岚' ELSE '林海' END,drone=CASE id WHEN 'PATROL-0830-02' THEN '云巡二号' ELSE '云巡一号' END,status=CASE id WHEN 'PATROL-0830-01' THEN '待执行' WHEN 'PATROL-0830-02' THEN '待执行' ELSE '已完成' END,coverage=CASE id WHEN 'PATROL-0830-01' THEN '待计算' WHEN 'PATROL-0830-02' THEN '待计算' ELSE '1.6 km²' END WHERE id IN ('PATROL-0830-01','PATROL-0830-02','PATROL-0829-04') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v1');
UPDATE forest_incident SET level=CASE id WHEN 'FIRE-20260830-001' THEN '三级' WHEN 'FIRE-20260829-003' THEN '二级' ELSE '一级' END,source=CASE id WHEN 'FIRE-20260830-001' THEN '多传感器融合' WHEN 'FIRE-20260829-003' THEN '摄像头AI' ELSE '护林员上报' END,reason=CASE id WHEN 'FIRE-20260830-001' THEN '烟雾持续上升，CO同步升高，固定摄像头发现疑似烟羽' WHEN 'FIRE-20260829-003' THEN '画面发现局部白烟' ELSE '林区出现短时烟雾' END,result=CASE id WHEN 'FIRE-20260830-001' THEN '' WHEN 'FIRE-20260829-003' THEN '游客使用卡式炉，护林员已劝阻并完成安全检查' ELSE '确认为晨雾与逆光干扰，无火情' END,ranger=CASE id WHEN 'FIRE-20260830-001' THEN NULL WHEN 'FIRE-20260829-003' THEN '周岚' ELSE '林海' END WHERE id IN ('FIRE-20260830-001','FIRE-20260829-003','FIRE-20260828-002') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v1');
INSERT IGNORE INTO app_data_migration(migration_key) VALUES('forest-utf8-repair-v1');

UPDATE forest_park SET name='福州国家森林公园',fire_risk='橙色' WHERE id=1 AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
UPDATE forest_zone SET name=CASE id WHEN 1 THEN '千年银杏古树区' WHEN 2 THEN '香樟古树群' WHEN 3 THEN '游客服务中心' WHEN 4 THEN '无火露营区' WHEN 5 THEN '南坡生态林' END,type=CASE id WHEN 1 THEN '古树核心区' WHEN 2 THEN '古树保护区' WHEN 3 THEN '游客活动区' WHEN 4 THEN '游客活动区' WHEN 5 THEN '巡护林区' END,risk=CASE id WHEN 1 THEN '高' WHEN 2 THEN '中' WHEN 3 THEN '低' WHEN 4 THEN '中' ELSE '高' END WHERE id BETWEEN 1 AND 5 AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
UPDATE forest_sensor_node SET name=CASE id WHEN 'GT-01' THEN '银杏区主节点' WHEN 'GT-02' THEN '银杏区北侧节点' WHEN 'GT-03' THEN '香樟区东侧节点' WHEN 'GT-04' THEN '香樟区西侧节点' WHEN 'GT-05' THEN '露营区边界节点' ELSE '南坡巡护节点' END,zone=CASE WHEN id IN ('GT-01','GT-02') THEN '千年银杏古树区' WHEN id IN ('GT-03','GT-04') THEN '香樟古树群' WHEN id='GT-05' THEN '无火露营区' ELSE '南坡生态林' END WHERE id IN ('GT-01','GT-02','GT-03','GT-04','GT-05','GT-06') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
UPDATE forest_drone SET name=CASE id WHEN 'UAV-01' THEN '云巡一号' ELSE '云巡二号' END,model=CASE id WHEN 'UAV-01' THEN 'M300 RTK + H20T + 环境采样载荷' ELSE 'Mavic 3T 轻型热成像巡检机' END,location=CASE id WHEN 'UAV-01' THEN '银杏区北侧上空' ELSE '游客中心起降坪' END,operator_name=CASE id WHEN 'UAV-01' THEN '林海' ELSE '周岚' END,payloads=CASE id WHEN 'UAV-01' THEN '可见光,辐射测温热成像,PM2.5,温湿度,CO' ELSE '可见光,热成像' END,phase=CASE id WHEN 'UAV-01' THEN '航线巡检' ELSE '地面待命' END WHERE id IN ('UAV-01','UAV-02') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
UPDATE drone_mission SET name=CASE id WHEN 'PATROL-0830-01' THEN '古树核心区午后热源巡查' WHEN 'PATROL-0830-02' THEN '游客活动区违规用火巡查' ELSE '西线闭园前巡查' END,route=CASE id WHEN 'PATROL-0830-01' THEN '护林员现场操控 → 银杏区、香樟区 → 返回起降点' WHEN 'PATROL-0830-02' THEN '护林员现场操控 → 露营区 → 返回起降点' ELSE '护林员现场操控 → 西线林区 → 返回起降点' END,ranger=CASE id WHEN 'PATROL-0830-02' THEN '周岚' ELSE '林海' END,drone=CASE id WHEN 'PATROL-0830-02' THEN '云巡二号' ELSE '云巡一号' END,status=CASE id WHEN 'PATROL-0830-01' THEN '待执行' WHEN 'PATROL-0830-02' THEN '待执行' ELSE '已完成' END,coverage=CASE id WHEN 'PATROL-0830-01' THEN '待计算' WHEN 'PATROL-0830-02' THEN '待计算' ELSE '1.6 km²' END WHERE id IN ('PATROL-0830-01','PATROL-0830-02','PATROL-0829-04') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
UPDATE forest_incident SET zone=CASE id WHEN 'FIRE-20260830-001' THEN '千年银杏古树区' WHEN 'FIRE-20260829-003' THEN '无火露营区' ELSE '香樟古树群' END,level=CASE id WHEN 'FIRE-20260830-001' THEN '三级' WHEN 'FIRE-20260829-003' THEN '二级' ELSE '一级' END,source=CASE id WHEN 'FIRE-20260830-001' THEN '多传感器融合' WHEN 'FIRE-20260829-003' THEN '摄像头AI' ELSE '护林员上报' END,reason=CASE id WHEN 'FIRE-20260830-001' THEN '烟雾持续上升，CO同步升高，固定摄像头发现疑似烟羽' WHEN 'FIRE-20260829-003' THEN '画面发现局部白烟' ELSE '林区出现短时烟雾' END,result=CASE id WHEN 'FIRE-20260830-001' THEN '' WHEN 'FIRE-20260829-003' THEN '游客使用卡式炉，护林员已劝阻并完成安全检查' ELSE '确认为晨雾与逆光干扰，无火情' END,ranger=CASE id WHEN 'FIRE-20260830-001' THEN NULL WHEN 'FIRE-20260829-003' THEN '周岚' ELSE '林海' END WHERE id IN ('FIRE-20260830-001','FIRE-20260829-003','FIRE-20260828-002') AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
UPDATE forest_broadcast SET title='橙色火险提示：全园禁止野外用火',message='古树保护区空气干燥、风力较大，请勿携带火种进入林区。当前未确认真实火情。',area='千年银杏古树区及周边步道',exit_name='游客中心集结点 / 东侧安全出口' WHERE id=1 AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v2');
INSERT IGNORE INTO app_data_migration(migration_key) VALUES('forest-utf8-repair-v2');

-- Repair earlier equipment seed rows without changing their current status.
UPDATE forest_equipment SET
  type=CASE id WHEN 'CAM-01' THEN '固定摄像头' WHEN 'CAST-01' THEN '广播设备' WHEN 'UAV-01' THEN '无人机' ELSE '传感节点' END,
  location=CASE id WHEN 'CAM-01' THEN '银杏区南侧瞭望杆' WHEN 'CAST-01' THEN '两处古树保护区' WHEN 'GT-01' THEN '千年银杏古树区' WHEN 'GT-02' THEN '千年银杏古树区' WHEN 'GT-06' THEN '南坡生态林' ELSE '北侧巡护站' END,
  source=CASE WHEN id IN ('CAM-01','GT-01') THEN '真机' ELSE '仿真' END
WHERE id IN ('CAM-01','CAST-01','GT-01','GT-02','GT-06','UAV-01')
  AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v3');
UPDATE forest_equipment_fault SET
  location=CASE id WHEN 'FAULT-0830-01' THEN '南坡生态林' WHEN 'FAULT-0830-02' THEN '北侧巡护站' ELSE '银杏区南侧瞭望杆' END,
  fault_type=CASE id WHEN 'FAULT-0830-01' THEN '通信离线' WHEN 'FAULT-0830-02' THEN '电池健康度下降' ELSE '画面短时中断' END
WHERE id IN ('FAULT-0830-01','FAULT-0830-02','FAULT-0829-04')
  AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v3');
UPDATE forest_maintenance_record SET
  device_name=CASE id WHEN 1 THEN '银杏区球机 CAM-01' WHEN 2 THEN '云巡一号 UAV-01' ELSE '银杏区主节点 GT-01' END,
  device_type=CASE id WHEN 1 THEN '摄像头' WHEN 2 THEN '无人机' ELSE '传感节点' END,
  fault=CASE id WHEN 1 THEN '网络接口松动' WHEN 2 THEN '桨叶磨损' ELSE '烟雾探头积灰' END,
  operator_name=CASE id WHEN 1 THEN '护林员·林海' WHEN 2 THEN '护林员·周岚' ELSE '护林员·林海' END
WHERE id IN (1,2,3)
  AND NOT EXISTS (SELECT 1 FROM app_data_migration WHERE migration_key='forest-utf8-repair-v3');
INSERT IGNORE INTO app_data_migration(migration_key) VALUES('forest-utf8-repair-v3');
