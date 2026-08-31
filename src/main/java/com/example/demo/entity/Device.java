package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long deviceId;

    /*
     * ==========================================
     * 历史兼容字段（宿舍楼宇场景遗留）。
     *
     * 森林火险场景下不再使用这三列，
     * 但为保持历史数据 / 老接口兼容，绝不删除。
     * 森林业务、森林 API、森林页面一律改用
     * zone_id / node_code / node_name。
     * ==========================================
     */
    private Integer building;

    private Integer floor;

    private Integer room;

    /*
     * ==========================================
     * 森林火险监测节点扩展字段
     * ==========================================
     */

    /** 所属森林区域ID（关联 forest_zone.id） */
    private Long zoneId;

    /** 监测节点编号，如 FS-N-001 */
    private String nodeCode;

    /** 监测节点名称，如 北部核心保护区-监测节点001 */
    private String nodeName;

    /** 纬度（度，DECIMAL(10,7)） */
    private BigDecimal latitude;

    /** 经度（度，DECIMAL(10,7)） */
    private BigDecimal longitude;

    /** 指挥台 SVG 地图横坐标（0~1000） */
    private Double mapX;

    /** 指挥台 SVG 地图纵坐标（0~650） */
    private Double mapY;

    // 0离线 1在线
    private Integer status;

    // 外键，关联 sys_user.id
    private Long userId;

    // NORMAL / OFFLINE / STALE / SENSOR_FAULT
    private String healthStatus;

    // 最后收到有效设备数据的时间
    private LocalDateTime lastReportTime;

    // 连续获取华为云数据失败次数
    private Integer consecutiveFailures;

    // 华为云IoTDA设备ID（REAL设备才有值）
    private String iotDeviceId;

    // REAL真实硬件 / DEMO模拟监测节点
    private String sourceType;
}