package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.SmokeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SmokeRecordMapper extends BaseMapper<SmokeRecord> {

    /**
     * 每台设备的最新一条记录。
     *
     * id 自增单调，MAX(id) 即最新采集。
     */
    @Select("SELECT sr.* FROM smoke_record sr "
            + "JOIN (SELECT device_id, MAX(id) AS mid "
            + "      FROM smoke_record GROUP BY device_id) t "
            + "ON sr.id = t.mid")
    List<SmokeRecord> selectLatestPerDevice();
}
