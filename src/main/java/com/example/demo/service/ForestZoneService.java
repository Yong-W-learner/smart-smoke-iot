package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.ForestZone;
import com.example.demo.mapper.ForestZoneMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 森林分区查询服务。
 *
 * 提供分区列表 / 分区详情 / 分区名称解析，
 * 供指挥台概览、SVG 地图与告警位置文案使用。
 */
@Service
public class ForestZoneService {

    private final ForestZoneMapper forestZoneMapper;

    public ForestZoneService(ForestZoneMapper forestZoneMapper) {
        this.forestZoneMapper = forestZoneMapper;
    }

    /**
     * 全部分区（按 id 升序）。
     */
    public List<ForestZone> listZones() {

        return forestZoneMapper.selectList(
                new LambdaQueryWrapper<ForestZone>()
                        .orderByAsc(ForestZone::getId)
        );
    }

    /**
     * 分区详情。
     */
    public ForestZone getZone(Long id) {

        return id == null ? null : forestZoneMapper.selectById(id);
    }

    /**
     * 分区名称；找不到时返回空字符串。
     */
    public String zoneNameById(Long id) {

        ForestZone zone = getZone(id);

        return zone == null ? "" : zone.getZoneName();
    }
}
