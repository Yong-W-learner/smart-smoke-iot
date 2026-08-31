package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Alarm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {

    /**
     * 最近 N 天每台设备产生的告警次数（含已恢复），
     * 用于识别"反复出现烟雾异常/频繁掉线"的寝室，
     * 只作为管理员排查线索，不推断具体原因。
     *
     * 返回行：deviceId / cnt。
     */
    @Select("SELECT device_id AS deviceId, COUNT(*) AS cnt "
            + "FROM alarm "
            + "WHERE alarm_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "GROUP BY device_id")
    List<Map<String, Object>> countRecentAnomaliesPerDeviceDays(
            @Param("days") int days);
}
