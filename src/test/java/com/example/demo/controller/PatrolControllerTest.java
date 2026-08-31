package com.example.demo.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.demo.entity.Alarm;
import com.example.demo.entity.Device;
import com.example.demo.entity.EnvironmentRecord;
import com.example.demo.entity.ForestZone;
import com.example.demo.entity.User;
import com.example.demo.mapper.AlarmMapper;
import com.example.demo.mapper.DeviceMapper;
import com.example.demo.mapper.EnvironmentRecordMapper;
import com.example.demo.service.AlarmService;
import com.example.demo.service.DataScopeService;
import com.example.demo.service.FireWeatherService;
import com.example.demo.service.ForestZoneService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 森林巡护员移动端接口测试。
 *
 * 覆盖：
 * - /home：返回本人分区 + 火险气象 + 节点状态 + 活动事件摘要；
 * - 巡护员未绑定分区 → 403；
 * - resident / admin 以外的身份被拒绝（这里 resident 被拒绝）；
 * - /events 只返回本人分区内的活动火险事件（zone_id + scene_type 过滤）。
 */
@ExtendWith(MockitoExtension.class)
public class PatrolControllerTest {

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ForestZoneService forestZoneService;

    @Mock
    private FireWeatherService fireWeatherService;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private AlarmMapper alarmMapper;

    @Mock
    private EnvironmentRecordMapper environmentRecordMapper;

    @InjectMocks
    private PatrolController controller;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, Alarm.class);
    }

    private User patrol(Long zoneId) {
        User user = new User();
        user.setId(1L);
        user.setUsername("patrol");
        user.setRole("resident");
        user.setZoneId(zoneId);
        return user;
    }

    private ForestZone zone() {
        ForestZone zone = new ForestZone();
        zone.setId(1L);
        zone.setZoneCode("Z01");
        zone.setZoneName("北部核心保护区");
        zone.setRiskLevel("HIGH");
        return zone;
    }

    private EnvironmentRecord weather() {
        EnvironmentRecord record = new EnvironmentRecord();
        record.setZoneId(1L);
        record.setTemperature(new BigDecimal("28.5"));
        record.setHumidity(new BigDecimal("40.2"));
        record.setSoilMoisture(new BigDecimal("32.0"));
        record.setWindSpeed(new BigDecimal("4.8"));
        return record;
    }

    private Alarm forestEvent(Long id, Long deviceId) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setDeviceId(deviceId);
        alarm.setSceneType(AlarmService.SCENE_FOREST);
        alarm.setZoneId(1L);
        alarm.setPriorityScore(85);
        alarm.setPriorityLevel("RED");
        alarm.setRecoverTime(null);
        return alarm;
    }

    private void stubPatrolHome() {
        when(dataScopeService.getCurrentUser()).thenReturn(patrol(1L));
        when(dataScopeService.isPatrol(any())).thenReturn(true);
        when(forestZoneService.getZone(1L)).thenReturn(zone());
        when(fireWeatherService.score(1L)).thenReturn(66);
        when(environmentRecordMapper.selectOne(any()))
                .thenReturn(weather());
        when(deviceMapper.selectList(any()))
                .thenReturn(List.of(node(1002L, 1L)));
        when(alarmMapper.selectCount(any())).thenReturn(0L);
    }

    private Device node(Long deviceId, Long zoneId) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setZoneId(zoneId);
        device.setNodeCode("FS-N-002");
        device.setNodeName("北部核心保护区·FS-N-002");
        device.setStatus(1);
        device.setHealthStatus("STALE");
        return device;
    }

    @Test
    void homeReturnsZoneWeatherAndNodeStats() {

        stubPatrolHome();

        var result = controller.home();

        assertEquals("北部核心保护区",
                ((ForestZone) result.get("zone")).getZoneName());
        assertEquals(66,
                ((java.util.Map<String, Object>) result.get("weather"))
                        .get("fireWeatherScore"));
        assertEquals(1,
                ((java.util.Map<String, Object>) result.get("nodeStats"))
                        .get("total"));
        assertEquals(0L, result.get("activeEventCount"));
    }

    @Test
    void homeRejectsPatrolWithoutZoneBinding() {

        when(dataScopeService.getCurrentUser()).thenReturn(patrol(null));
        when(dataScopeService.isPatrol(any())).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> controller.home());
    }

    @Test
    void homeRejectsResidentIdentity() {

        User resident = new User();
        resident.setRole("resident");

        when(dataScopeService.getCurrentUser()).thenReturn(resident);
        when(dataScopeService.isPatrol(any())).thenReturn(false);
        when(dataScopeService.isAdmin(any())).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> controller.home());
    }

    @Test
    void eventsScopedToPatrolZone() {

        when(dataScopeService.getCurrentUser()).thenReturn(patrol(1L));
        when(dataScopeService.isPatrol(any())).thenReturn(true);

        Alarm event = forestEvent(7L, 1002L);
        when(alarmMapper.selectList(any())).thenReturn(List.of(event));
        when(deviceMapper.selectById(1002L)).thenReturn(node(1002L, 1L));

        var events = controller.events();

        assertEquals(1, events.size());
        assertEquals("FS-N-002", events.get(0).get("nodeCode"));
        assertEquals(85, events.get(0).get("priorityScore"));

        ArgumentCaptor<LambdaQueryWrapper<Alarm>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(alarmMapper).selectList(captor.capture());

        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("zone_id"), "事件查询必须按分区过滤：" + sql);
        assertTrue(sql.contains("scene_type"), "事件查询必须过滤 FOREST：" + sql);
    }
}
