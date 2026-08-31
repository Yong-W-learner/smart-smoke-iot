package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;       //工单编号
    private String type;          // repair设备报修；alarm仅兼容拆分前的历史数据
    private String title;         //标题
    private String description;   //描述
    private Integer building;
    private Integer floor;
    private Integer room;
    private Long deviceId;        //关联设备id（可选）
    private Long reporterId;      //报修居民id
    private String reporterName;  //报修居民姓名
    private String status;        //pending待接单 / accepted已接单 / closed已关闭
    private Long repairerId;      //接单维修员id
    private String repairerName;  //接单维修员姓名
    private String remark;        //关闭备注
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime acceptTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime closeTime;
}
